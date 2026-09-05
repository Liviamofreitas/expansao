package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeOrganizacao;
import br.com.engesoftware.sgdf.privacidade.Mascara;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As telas do Anexo 2 — histórias F1-07 e F1-10.
 *
 * <p>A tela 3 (triagem) saiu daqui na F1-06: ela ganhou escrita e recorte por
 * contrato, e passou a ter um controlador próprio.
 *
 * <p>Cada método faz a mesma coisa na mesma ordem: pega o ator, <b>pergunta ao
 * Autorizador</b>, e só então consulta. A autorização não é uma anotação: é uma
 * chamada visível, no corpo do método, com o alvo que a decisão precisa — o
 * contrato e o escopo do documento. É o que permite negar por escopo
 * profissional e por recorte de contrato, que nenhuma anotação de papel
 * expressa.
 */
@RestController
@RequestMapping("/api")
public class PainelController {

    private final AtorDaRequisicao atores;
    private final ConsultaDoPainel consulta;
    private final RepositorioDeOrganizacao organizacao;

    public PainelController(AtorDaRequisicao atores, ConsultaDoPainel consulta,
                            RepositorioDeOrganizacao organizacao) {
        this.atores = atores;
        this.consulta = consulta;
        this.organizacao = organizacao;
    }

    /**
     * Tela 5, metade dos CONFLITOS — história F1-10.
     *
     * <p>Separado de {@code /desconhecidos} porque são dois problemas
     * diferentes: ali estão arquivos que o motor não reconheceu (score abaixo do
     * limiar); aqui, arquivos que ele reconheceu como <b>não sendo evidência</b>
     * — cópia de conflito, temporário, checklist. O primeiro é falta de regra; o
     * segundo é bagunça de pasta, e quem resolve cada um é outra pessoa.
     *
     * <p>Este tem recorte por contrato, e o de desconhecidos não: o achado nasce
     * de uma varredura, e a varredura é de um contrato. Ver RA-04.
     */
    @GetMapping("/organizacao")
    public List<RepositorioDeOrganizacao.Achado> organizacao(
            @RequestParam(defaultValue = "50") int limite) {
        Ator ator = exigir(Permissao.VER_PAINEL, Autorizador.Alvo.nenhum());
        return organizacao.abertos(
                ator.enxergaTodosOsContratos() ? null : ator.contratos(), limite);
    }

    /**
     * Tela 1 e 2: painel da competência e ciclos por contrato.
     *
     * <p>O contrato do alvo vem do <b>ciclo</b>, não de um parâmetro. Se viesse
     * do parâmetro, o recorte por contrato seria opcional para quem chama —
     * bastaria omiti-lo para que a decisão não tivesse contra o que comparar.
     *
     * <p>Ciclo inexistente recebe o mesmo 403 de ciclo alheio, e não um 404: a
     * diferença entre as duas respostas diria a quem não pode ver o ciclo se ele
     * existe, que é a metade da informação que se está protegendo.
     */
    @GetMapping("/ciclos/{cicloId}/painel")
    public Painel painel(@PathVariable UUID cicloId) {
        UUID contrato = consulta.contratoDoCiclo(cicloId);
        if (contrato == null) {
            throw new AcessoNegado("ciclo " + cicloId + " não existe");
        }
        Ator ator = exigir(Permissao.VER_PAINEL, Autorizador.Alvo.doContrato(contrato));
        return new Painel(cicloId,
                consulta.estadosDoCiclo(cicloId),
                consulta.motivosDeBloqueio(cicloId),
                ator.nome());
    }

    /**
     * Completude por tipo — história F2-03.
     *
     * <p>A contagem é para todo mundo que vê o painel; a lista de matrículas
     * faltantes, não. Ela só é preenchida para quem tem
     * {@code VER_DOCUMENTO_PROFISSIONAL} (cap. 15.1) — matrícula identifica uma
     * pessoa, e a decisão de mostrá-la é a mesma que decide abrir o contracheque.
     */
    @GetMapping("/ciclos/{cicloId}/completude")
    public List<Linha> completude(@PathVariable UUID cicloId) {
        UUID contrato = consulta.contratoDoCiclo(cicloId);
        if (contrato == null) {
            throw new AcessoNegado("ciclo " + cicloId + " não existe");
        }
        Ator ator = exigir(Permissao.VER_PAINEL, Autorizador.Alvo.doContrato(contrato));
        boolean veProfissional = Autorizador.pode(ator, Permissao.VER_DOCUMENTO_PROFISSIONAL)
                .permitida();

        return consulta.completudePorTipo(cicloId).stream()
                .map(c -> new Linha(c,
                        veProfissional && "PROFISSIONAL".equals(c.escopo()) && c.ausentes() > 0
                                ? consulta.matriculasFaltantes(cicloId, c.tipo(), 200)
                                : List.of()))
                .toList();
    }

    /**
     * @param faltantes matrículas de quem não entregou; vazia para quem não pode
     *                  ver escopo profissional — e vazia também quando não falta
     *                  ninguém, para não confundir "não posso ver" com "não há"
     */
    public record Linha(ConsultaDoPainel.Completude completude, List<String> faltantes) {

        public Linha {
            faltantes = List.copyOf(faltantes);
        }

        public String resumo() {
            return completude.tipo() + " — " + completude.resumo();
        }
    }

    /** Tela 5: arquivos desconhecidos e conflitos de sincronização (F1-10). */
    @GetMapping("/desconhecidos")
    public List<ItemDeTriagem> desconhecidos(@RequestParam(defaultValue = "50") int limite) {
        Ator ator = exigir(Permissao.VER_PAINEL, Autorizador.Alvo.nenhum());
        return consulta.desconhecidos(limite).stream()
                .map(c -> ItemDeTriagem.de(c, podeVerConteudo(ator, c)))
                .toList();
    }

    private boolean podeVerConteudo(Ator ator, ConsultaDoPainel.Candidato c) {
        boolean profissional = "PESSOAL".equals(c.sigilo())
                || "PESSOAL_SENSIVEL".equals(c.sigilo());
        return Autorizador.pode(ator, Permissao.VER_CONTEUDO_DOCUMENTO,
                new Autorizador.Alvo(null, profissional, null)).permitida();
    }

    private Ator exigir(Permissao permissao, Autorizador.Alvo alvo) {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao, alvo);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }

    /**
     * @param estados      quantas exigências em cada estado — a barra segmentada
     * @param bloqueios    por que não pode publicar; vazio quando pode
     */
    public record Painel(UUID cicloId, Map<String, Integer> estados, List<String> bloqueios,
                         String ator) {

        public boolean podePublicar() {
            return bloqueios.isEmpty();
        }
    }

    /**
     * Um item da fila, já com o nome do arquivo tratado.
     *
     * <p>O nome do arquivo é digitado por gente e a massa real mostrou que ele
     * carrega CPF ({@code 1041601__CONTRACHEQUE.pdf} é matrícula, mas
     * {@code 052190471-40 folha.pdf} apareceria igual). A máscara é aplicada aqui
     * porque esta é a fronteira de saída — é o requisito SEC-02.
     */
    public record ItemDeTriagem(UUID documentoId, String nomeArquivo, String caminho,
                                String tipoProposto, double confianca,
                                boolean conteudoVisivel) {

        static ItemDeTriagem de(ConsultaDoPainel.Candidato c, boolean conteudoVisivel) {
            return new ItemDeTriagem(c.documentoId(), Mascara.texto(c.nomeArquivo()),
                    Mascara.texto(c.caminho()), c.tipoProposto(), c.confianca(),
                    conteudoVisivel);
        }
    }
}
