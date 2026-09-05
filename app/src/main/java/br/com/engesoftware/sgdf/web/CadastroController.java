package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeCadastro;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeRascunho;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Papel;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro e matriz — histórias F0-02, F0-03 e F0-05.
 *
 * <p>Duas permissões diferentes, e a diferença é o ponto: {@code CADASTRAR}
 * basta para cliente, contrato, tipo e alias; <b>publicar a matriz</b> exige
 * {@code PUBLICAR_MATRIZ}, e publicar mudança de criticidade bloqueante exige
 * ainda {@code ALTERAR_CRITICIDADE}, que só o APROVADOR_DAF tem (cap. 15.1).
 */
@RestController
@RequestMapping("/api")
public class CadastroController {

    private final AtorDaRequisicao atores;
    private final RepositorioDeCadastro cadastro;
    private final RepositorioDeRascunho rascunhos;

    public CadastroController(AtorDaRequisicao atores, RepositorioDeCadastro cadastro,
                              RepositorioDeRascunho rascunhos) {
        this.atores = atores;
        this.cadastro = cadastro;
        this.rascunhos = rascunhos;
    }

    // --- F0-02: cliente e contrato-serviço -----------------------------------

    @PostMapping("/clientes")
    public Map<String, UUID> cliente(@RequestBody NovoCliente corpo) {
        Ator ator = exigir(Permissao.CADASTRAR);
        return Map.of("id", cadastro.cadastrarCliente(corpo.nome(), corpo.cnpj(),
                corpo.esfera(), ator.identificador(), papelDe(ator)));
    }

    @PostMapping("/contratos")
    public Map<String, UUID> contrato(@RequestBody RepositorioDeCadastro.Contrato corpo) {
        Ator ator = exigir(Permissao.CADASTRAR);
        return Map.of("id", cadastro.cadastrarContrato(corpo, ator.identificador(),
                papelDe(ator)));
    }

    /** Os contratos-serviço de um cliente — três, no caso da CAIXA (F0-02). */
    @GetMapping("/clientes/{clienteId}/contratos")
    public List<RepositorioDeCadastro.ContratoCadastrado> contratos(
            @PathVariable UUID clienteId) {
        exigir(Permissao.VER_PAINEL);
        return cadastro.contratosDoCliente(clienteId);
    }

    // --- F0-03: tipo documental e aliases ------------------------------------

    @PostMapping("/tipos")
    public Map<String, UUID> tipo(@RequestBody RepositorioDeCadastro.Tipo corpo) {
        Ator ator = exigir(Permissao.CADASTRAR);
        return Map.of("id", cadastro.cadastrarTipo(corpo, ator.identificador(), papelDe(ator)));
    }

    @PostMapping("/tipos/{tipoId}/aliases")
    public Map<String, UUID> alias(@PathVariable UUID tipoId, @RequestBody Alias corpo) {
        Ator ator = exigir(Permissao.CADASTRAR);
        return Map.of("id", cadastro.cadastrarAlias(tipoId, corpo.texto(),
                ator.identificador(), papelDe(ator)));
    }

    @PostMapping("/tipos/{tipoId}/desativar")
    public Map<String, String> desativar(@PathVariable UUID tipoId,
                                         @RequestBody Justificativa corpo) {
        Ator ator = exigir(Permissao.CADASTRAR);
        cadastro.desativarTipo(tipoId, corpo.motivo(), ator.identificador(), papelDe(ator));
        return Map.of("situacao", "DESATIVADO");
    }

    // --- F0-05: rascunho → publicação ----------------------------------------

    /**
     * O que a publicação faria — antes do clique.
     *
     * <p>Exposto separado de propósito: o cap. 12 manda avisar que "alterações
     * não travam o faturamento", e aqui esse aviso é a lista nomeada dos ciclos
     * abertos que continuam na versão antiga, não uma frase fixa na tela.
     */
    @GetMapping("/matriz/rascunhos/{rascunhoId}")
    public RepositorioDeRascunho.Analise analise(@PathVariable UUID rascunhoId) {
        exigir(Permissao.VER_PAINEL);
        return rascunhos.analisar(rascunhoId);
    }

    @PostMapping("/matriz/rascunhos/{rascunhoId}/publicar")
    public RepositorioDeRascunho.Publicada publicar(@PathVariable UUID rascunhoId) {
        Ator ator = exigir(Permissao.PUBLICAR_MATRIZ);
        boolean daf = Autorizador.pode(ator, Permissao.ALTERAR_CRITICIDADE).permitida();
        return rascunhos.publicar(rascunhoId, ator.identificador(), papelDe(ator), daf);
    }

    /** Histórico com autor e motivo — critério de aceite da F0-05. */
    @GetMapping("/matriz/versoes")
    public List<RepositorioDeRascunho.VersaoNoHistorico> versoes(
            @RequestParam(defaultValue = "50") int limite) {
        exigir(Permissao.VER_PAINEL);
        return rascunhos.historico(limite);
    }

    // -------------------------------------------------------------------------

    private Ator exigir(Permissao permissao) {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }

    private static String papelDe(Ator ator) {
        return ator.papeis().stream().map(Papel::name).sorted().collect(Collectors.joining("+"));
    }

    public record NovoCliente(String nome, String cnpj, String esfera) {}

    public record Alias(String texto) {}

    public record Justificativa(String motivo) {}
}
