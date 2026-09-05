package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeExcecao;
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
 * Fluxo de exceção — história F0-09.
 *
 * <p>Critério de aceite: <i>"solicitante não consegue aprovar a própria exceção;
 * aprovação exige APROVADOR_DAF"</i>.
 *
 * <p>A decisão passa o <b>solicitante</b> ao {@code Autorizador} como alvo, e é
 * o que permite a ele aplicar a segregação de funções do cap. 15.1 na fronteira,
 * com mensagem. O repositório repete a checagem e o banco a impõe: três camadas,
 * cada uma cobrindo uma classe diferente de erro.
 */
@RestController
@RequestMapping("/api")
public class ExcecaoController {

    private final AtorDaRequisicao atores;
    private final RepositorioDeExcecao excecoes;

    public ExcecaoController(AtorDaRequisicao atores, RepositorioDeExcecao excecoes) {
        this.atores = atores;
        this.excecoes = excecoes;
    }

    /** Pedir não é obter: a exigência não se move aqui. */
    @PostMapping("/exigencias/{exigenciaId}/excecao")
    public Map<String, UUID> solicitar(@PathVariable UUID exigenciaId,
                                       @RequestBody Solicitacao corpo) {
        Ator ator = exigir(Permissao.SOLICITAR_EXCECAO, Autorizador.Alvo.nenhum());
        return Map.of("id", excecoes.solicitar(exigenciaId, corpo.motivo(), corpo.evidencia(),
                ator.identificador(), papelDe(ator)));
    }

    @PostMapping("/excecoes/{excecaoId}/aprovar")
    public RepositorioDeExcecao.Decidida aprovar(@PathVariable UUID excecaoId,
                                                 @RequestBody(required = false)
                                                 Solicitacao corpo) {
        Ator ator = decisor(excecaoId);
        return excecoes.aprovar(excecaoId, ator.identificador(), papelDe(ator), true);
    }

    @PostMapping("/excecoes/{excecaoId}/negar")
    public RepositorioDeExcecao.Decidida negar(@PathVariable UUID excecaoId,
                                               @RequestBody Solicitacao corpo) {
        Ator ator = decisor(excecaoId);
        return excecoes.negar(excecaoId, corpo.motivo(), ator.identificador(), papelDe(ator),
                true);
    }

    /** A fila do APROVADOR_DAF. */
    @GetMapping("/excecoes")
    public List<RepositorioDeExcecao.EmAberto> emAberto(
            @RequestParam(defaultValue = "50") int limite) {
        exigir(Permissao.APROVAR_EXCECAO, Autorizador.Alvo.nenhum());
        return excecoes.emAberto(limite);
    }

    // -------------------------------------------------------------------------

    /**
     * Autoriza a decisão com o SOLICITANTE no alvo.
     *
     * <p>Sem passar o solicitante, o {@code Autorizador} não teria contra o que
     * comparar e a segregação de funções não incidiria — o mesmo defeito que o
     * painel tinha ao receber o contrato por parâmetro (achados § 30.1).
     */
    private Ator decisor(UUID excecaoId) {
        String solicitante = excecoes.emAberto(500).stream()
                .filter(e -> e.id().equals(excecaoId))
                .map(RepositorioDeExcecao.EmAberto::solicitante)
                .findFirst()
                .orElse(null);
        if (solicitante == null) {
            throw new AcessoNegado("exceção " + excecaoId + " não está em aberto");
        }
        return exigir(Permissao.APROVAR_EXCECAO, Autorizador.Alvo.excecaoDe(solicitante));
    }

    private Ator exigir(Permissao permissao, Autorizador.Alvo alvo) {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao, alvo);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }

    private static String papelDe(Ator ator) {
        return ator.papeis().stream().map(Papel::name).sorted().collect(Collectors.joining("+"));
    }

    /** @param evidencia link ou referência; nunca o documento em si (cap. 11.2) */
    public record Solicitacao(String motivo, String evidencia) {}
}
