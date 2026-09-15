package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.classificacao.Ancora;
import br.com.engesoftware.sgdf.classificacao.RegraDeReconhecimento;
import br.com.engesoftware.sgdf.classificacao.VerificadorDeRegra;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeCadastro;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeRegras;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Acrescentar e excluir documentos do checklist — a regra que os reconhece.
 *
 * <p><b>Só o ADMIN_SISTEMA</b>, por decisão da área registrada no ADR-004. O
 * cap. 15.1 dava "cadastro de contratos/regras" ao CURADOR_MATRIZ; a divergência
 * é deliberada e o risco está declarado lá.
 *
 * <p><b>Cadastrar um tipo não o coloca em contrato nenhum.</b> Para passar a ser
 * exigido, alguém com PUBLICAR_MATRIZ precisa publicar uma versão da matriz — e
 * essa permissão continua com o curador. É o que sobrou da segregação depois do
 * ADR-004, e é a metade que importa: quem cria o tipo e quem decide que ele é
 * exigido seguem em mãos diferentes.
 *
 * <p><b>A regra entra valendo na hora.</b> O classificador carrega do banco a
 * cada varredura, sem cache — requisito explícito da área.
 */
@RestController
@RequestMapping("/api")
public class RegraController {

    /** Uma âncora, como quem cadastra a descreve. */
    public record AncoraNova(String expressao, Double peso, Boolean discriminante,
                             Boolean ignorandoEspacos) {}

    /** Um exemplo: o que é o documento, e se a regra deve reconhecê-lo. */
    public record ExemploNovo(String rotulo, String texto, Boolean deveReconhecer) {}

    /** O corpo do cadastro. */
    public record RegraNova(String emissor, String identificacao, List<AncoraNova> ancoras,
                            Double limiarAuto, Double limiarTriagem,
                            List<ExemploNovo> exemplos) {}

    private final AtorDaRequisicao atores;
    private final RepositorioDeRegras regras;
    private final RepositorioDeCadastro cadastro;

    public RegraController(AtorDaRequisicao atores, RepositorioDeRegras regras,
                           RepositorioDeCadastro cadastro) {
        this.atores = atores;
        this.regras = regras;
        this.cadastro = cadastro;
    }

    /**
     * Acrescenta (ou substitui) a regra de reconhecimento de um tipo.
     *
     * <p>A regra só é gravada se passar nas três verificações do
     * {@link VerificadorDeRegra}. A recusa devolve 422 com a lista inteira do
     * que falhou — não a primeira falha: quem está ajustando âncora precisa ver
     * tudo o que quebrou de uma vez, ou corrige uma coisa por tentativa.
     */
    @PostMapping("/tipos/{tipoId}/regra")
    public Map<String, Object> cadastrar(@PathVariable UUID tipoId,
                                         @RequestBody RegraNova corpo) {
        Ator ator = exigirCadastro();
        String codigo = codigoDoTipo(tipoId);

        RegraDeReconhecimento candidata = montar(codigo, corpo);

        // AS OUTRAS REGRAS, SEM NENHUMA VERSÃO DESTE TIPO.
        //
        // Deixar a versão antiga no motor faria as duas competirem, e o
        // resultado não diria nada sobre nenhuma das duas: um empate entre a
        // regra velha e a nova mandaria o exemplo para triagem, e a recusa
        // culparia a candidata por um conflito que ela não tem depois de entrar.
        List<RegraDeReconhecimento> demais = new ArrayList<>();
        for (RegraDeReconhecimento r : regras.ativas()) {
            if (!r.tipo().equals(codigo)) {
                demais.add(r);
            }
        }

        List<VerificadorDeRegra.Exemplo> novos = new ArrayList<>();
        for (ExemploNovo e : corpo.exemplos() == null ? List.<ExemploNovo>of() : corpo.exemplos()) {
            if (e.rotulo() == null || e.rotulo().isBlank() || e.texto() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "exemplo sem rótulo ou sem texto");
            }
            novos.add(new VerificadorDeRegra.Exemplo(codigo, e.rotulo(), e.texto(),
                    e.deveReconhecer() == null || e.deveReconhecer()));
        }

        VerificadorDeRegra.Veredito veredito = VerificadorDeRegra.verificar(
                candidata, demais, novos, regras.exemplos());
        if (!veredito.aprovada()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "a regra não foi aceita: " + String.join(" | ", veredito.recusas()));
        }

        UUID id = regras.cadastrar(tipoId, corpo.emissor(),
                candidata.ancoras().isEmpty() ? "PAREAMENTO" : "ANCORA",
                ancorasJson(corpo.ancoras()), 0.0,
                candidata.limiarAuto(), candidata.limiarTriagem(), novos,
                ator.identificador());

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("id", id);
        resposta.put("tipo", codigo);
        resposta.put("exemplos_verificados", novos.size() + regras.exemplos().size());
        resposta.put("vale_a_partir_de", "agora — a próxima varredura já usa esta regra");
        return resposta;
    }

    /**
     * Exclui o tipo do checklist: tira a regra de circulação.
     *
     * <p>Desativa, não apaga. O tipo aparece em exigências de ciclos passados,
     * em vínculos de documento e em books publicados; apagá-lo arrastaria tudo
     * isso ou deixaria referência pendurada. Desativar faz o que se quer — parar
     * de reconhecer daqui para a frente — sem reescrever o passado.
     */
    @PostMapping("/tipos/{tipoId}/regra/desativar")
    public Map<String, Object> desativar(@PathVariable UUID tipoId) {
        Ator ator = exigirCadastro();
        String codigo = codigoDoTipo(tipoId);
        int quantas = regras.desativar(tipoId, ator.identificador());
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("tipo", codigo);
        resposta.put("regras_desativadas", quantas);
        resposta.put("observacao", quantas == 0
                ? "não havia regra ativa para este tipo"
                : "os documentos já classificados por ela continuam como estão (cap. 16)");
        return resposta;
    }

    // -------------------------------------------------------------------------

    private RegraDeReconhecimento montar(String codigo, RegraNova corpo) {
        List<Ancora> ancoras = new ArrayList<>();
        for (AncoraNova a : corpo.ancoras() == null ? List.<AncoraNova>of() : corpo.ancoras()) {
            if (a.expressao() == null || a.expressao().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "âncora sem expressão");
            }
            try {
                ancoras.add(new Ancora(Pattern.compile(a.expressao()),
                        a.peso() == null ? 1.0 : a.peso(),
                        a.discriminante() != null && a.discriminante(),
                        a.ignorandoEspacos() != null && a.ignorandoEspacos()));
            } catch (PatternSyntaxException e) {
                // A MENSAGEM DO REGEX É A ÚNICA QUE DIZ ONDE ESTÁ O ERRO.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "expressão inválida em '" + a.expressao() + "': " + e.getDescription());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        try {
            return new RegraDeReconhecimento(codigo, corpo.emissor(), ancoras,
                    List.of(), List.of(), 0.0,
                    corpo.limiarAuto() == null ? 0.95 : corpo.limiarAuto(),
                    corpo.limiarTriagem() == null ? 0.70 : corpo.limiarTriagem(), 1);
        } catch (IllegalArgumentException e) {
            // O construtor já recusa regra sem âncora e limiares invertidos, com
            // a razão escrita. Repetir a razão aqui seria arriscar que as duas
            // divirjam.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static String ancorasJson(List<AncoraNova> ancoras) {
        StringBuilder sb = new StringBuilder("[");
        for (AncoraNova a : ancoras == null ? List.<AncoraNova>of() : ancoras) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append("{\"expressao\": \"")
              .append(a.expressao().replace("\\", "\\\\").replace("\"", "\\\""))
              .append("\", \"peso\": ").append(a.peso() == null ? 1.0 : a.peso())
              .append(", \"discriminante\": ")
              .append(a.discriminante() != null && a.discriminante())
              .append(", \"ignorando_espacos\": ")
              .append(a.ignorandoEspacos() != null && a.ignorandoEspacos())
              .append("}");
        }
        return sb.append("]").toString();
    }

    private String codigoDoTipo(UUID tipoId) {
        String codigo = cadastro.codigoDoTipo(tipoId);
        if (codigo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "tipo documental " + tipoId + " não existe");
        }
        return codigo;
    }

    /** ADR-004: cadastrar é do ADMIN_SISTEMA, e de mais ninguém. */
    private Ator exigirCadastro() {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, Permissao.CADASTRAR);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }
}
