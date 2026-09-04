package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeTriagem;
import br.com.engesoftware.sgdf.privacidade.Mascara;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Papel;
import br.com.engesoftware.sgdf.triagem.PedidoDeTriagem;
import br.com.engesoftware.sgdf.triagem.ResultadoDaTriagem;
import java.util.List;
import java.util.Set;
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
 * A tela 3 do Anexo 2, agora com escrita — história F1-06.
 *
 * <p>Critério de aceite: <i>"confirmar tira da fila, cria alias e o mesmo padrão
 * não retorna"</i>.
 *
 * <p><b>O que mudou em relação à leitura.</b> A fila era lida de
 * {@code documento} sozinho e não tinha contrato — o resíduo RA-01. Agora ela
 * vem de {@code fila_de_triagem}, que passa pela exigência e pelo ciclo, e o
 * recorte do cap. 15.1 tem sobre o que incidir. O mesmo vale para cada decisão:
 * o contrato do alvo vem da candidatura, nunca do corpo da requisição.
 */
@RestController
@RequestMapping("/api/triagem")
public class TriagemController {

    private final AtorDaRequisicao atores;
    private final RepositorioDeTriagem triagem;

    public TriagemController(AtorDaRequisicao atores, RepositorioDeTriagem triagem) {
        this.atores = atores;
        this.triagem = triagem;
    }

    /** A fila, já recortada pelos contratos do ator. */
    @GetMapping
    public List<ItemDaFila> fila(@RequestParam(defaultValue = "50") int limite) {
        Ator ator = exigir(Autorizador.Alvo.nenhum());
        Set<UUID> recorte = ator.enxergaTodosOsContratos() ? null : ator.contratos();
        return triagem.fila(recorte, limite).stream()
                .map(i -> ItemDaFila.de(i, podeVerConteudo(ator, i.sigilo())))
                .toList();
    }

    /** Aceita o tipo proposto: vincula, move a exigência e aprende o alias. */
    @PostMapping("/{candidaturaId}/confirmar")
    public ResultadoDaTriagem confirmar(@PathVariable UUID candidaturaId) {
        Ator ator = exigirSobre(candidaturaId);
        return triagem.decidir(new PedidoDeTriagem.Confirmar(candidaturaId),
                ator.identificador(), papelDe(ator));
    }

    /** Corrige o tipo. Cap. 12: exige escolher tipo — e aqui, também o motivo. */
    @PostMapping("/{candidaturaId}/reclassificar")
    public ResultadoDaTriagem reclassificar(@PathVariable UUID candidaturaId,
                                            @RequestBody Reclassificacao corpo) {
        Ator ator = exigirSobre(candidaturaId);
        return triagem.decidir(
                new PedidoDeTriagem.Reclassificar(candidaturaId, corpo.tipoId(), corpo.motivo()),
                ator.identificador(), papelDe(ator));
    }

    /** Devolve a exigência a PENDENTE com motivo. Não aprende nada. */
    @PostMapping("/{candidaturaId}/ilegivel")
    public ResultadoDaTriagem ilegivel(@PathVariable UUID candidaturaId,
                                       @RequestBody Justificativa corpo) {
        Ator ator = exigirSobre(candidaturaId);
        return triagem.decidir(new PedidoDeTriagem.Ilegivel(candidaturaId, corpo.motivo()),
                ator.identificador(), papelDe(ator));
    }

    // -------------------------------------------------------------------------

    /**
     * Autoriza sobre o contrato da candidatura.
     *
     * <p>Candidatura inexistente recebe o mesmo 403 de candidatura alheia, pela
     * razão do § 30.2 dos achados: a diferença entre 403 e 404 diria a quem não
     * pode ver a fila de um contrato que aquele item existe.
     */
    private Ator exigirSobre(UUID candidaturaId) {
        UUID contrato = triagem.contratoDaCandidatura(candidaturaId);
        if (contrato == null) {
            throw new AcessoNegado("candidatura " + candidaturaId + " não existe");
        }
        return exigir(Autorizador.Alvo.doContrato(contrato));
    }

    private Ator exigir(Autorizador.Alvo alvo) {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao =
                Autorizador.pode(ator, br.com.engesoftware.sgdf.seguranca.Permissao.TRIAR, alvo);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }

    private boolean podeVerConteudo(Ator ator, String sigilo) {
        boolean profissional = "PESSOAL".equals(sigilo) || "PESSOAL_SENSIVEL".equals(sigilo);
        return Autorizador.pode(ator,
                br.com.engesoftware.sgdf.seguranca.Permissao.VER_CONTEUDO_DOCUMENTO,
                new Autorizador.Alvo(null, profissional, null)).permitida();
    }

    /** O papel que vai para a trilha (cap. 16). */
    private static String papelDe(Ator ator) {
        return ator.papeis().stream().map(Papel::name).sorted()
                .collect(Collectors.joining("+"));
    }

    /** Corpo do reclassificar. */
    public record Reclassificacao(UUID tipoId, String motivo) {}

    /** Corpo do ilegível. */
    public record Justificativa(String motivo) {}

    /**
     * Um item da fila para a tela.
     *
     * <p>Nome e caminho saem mascarados (SEC-02): a massa real trouxe nome de
     * arquivo digitado por gente, com CPF dentro.
     */
    public record ItemDaFila(UUID candidaturaId, String competencia, String nomeArquivo,
                             String caminho, String tipoProposto, double score,
                             String motivo, boolean conteudoVisivel) {

        static ItemDaFila de(RepositorioDeTriagem.ItemDaFila i, boolean conteudoVisivel) {
            return new ItemDaFila(i.candidaturaId(), i.competencia(),
                    Mascara.texto(i.nomeArquivo()), Mascara.texto(i.caminho()),
                    i.tipoProposto(), i.score(), i.motivo(), conteudoVisivel);
        }
    }
}
