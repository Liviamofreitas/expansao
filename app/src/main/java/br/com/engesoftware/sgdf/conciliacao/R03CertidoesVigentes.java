package br.com.engesoftware.sgdf.conciliacao;

import br.com.engesoftware.sgdf.validacao.NaturezaDaCertidao;
import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeVigencia;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R03 — toda certidão do ciclo está vigente na data prevista da NF.
 *
 * <p>Cap. 9: <i>"para cada CER.*: validade ≥ data_prevista_NF(ciclo)"</i>.
 *
 * <p><b>Por que é conciliação e não só V5.</b> V5 confere <i>uma</i> certidão
 * contra a data da NF; R03 confere o <i>conjunto</i>, e o conjunto tem uma
 * propriedade que nenhuma certidão sozinha tem: <b>a primeira a vencer manda</b>.
 * Um book com seis certidões vigentes e uma vencendo em três dias não está
 * confortável — está a três dias de não poder ser reemitido.
 *
 * <p>Por isso R03 devolve, além do veredito, qual certidão vence primeiro. É o
 * que a régua de cobrança precisa para avisar antes, e não depois.
 */
public final class R03CertidoesVigentes implements RegraDeConciliacao {

    /**
     * Uma certidão do ciclo, como R03 a enxerga.
     *
     * @param tipo             CER.CND_RFB, CER.CNDT...
     * @param validade         até quando vale
     * @param natureza         lida do título
     * @param positivaAceita   o que o cadastro decidiu para este tipo (achado A10)
     */
    public record CertidaoDoCiclo(String tipo, LocalDate validade, NaturezaDaCertidao natureza,
                                  boolean positivaAceita) {
    }

    private final List<CertidaoDoCiclo> certidoes;
    private final LocalDate dataPrevistaDaNf;
    private final ResultadoDaConciliacao.Modo modoCadastrado;

    public R03CertidoesVigentes(List<CertidaoDoCiclo> certidoes, LocalDate dataPrevistaDaNf,
                                ResultadoDaConciliacao.Modo modoCadastrado) {
        this.certidoes = List.copyOf(certidoes);
        this.dataPrevistaDaNf = dataPrevistaDaNf;
        this.modoCadastrado = modoCadastrado;
    }

    @Override
    public String codigo() {
        return "R03";
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado, tolerancia);

        if (certidoes.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                    modo, null, null, null,
                    "o ciclo não tem certidão nenhuma validada: R03 não tem o que conferir",
                    Map.of());
        }

        List<String> reprovadas = new ArrayList<>();
        List<String> ressalvas = new ArrayList<>();
        CertidaoDoCiclo primeiraAVencer = null;

        for (CertidaoDoCiclo c : certidoes) {
            ResultadoDeValidacao v = new ValidacaoDeVigencia(c.positivaAceita())
                    .validar(c.validade(), c.natureza(), dataPrevistaDaNf);
            if (v.reprovado()) {
                reprovadas.add(c.tipo() + ": " + v.motivo());
            } else if (v.detalhe().containsKey("ressalva")) {
                ressalvas.add(c.tipo() + ": " + v.detalhe().get("ressalva").get(0));
            }
            if (c.validade() != null
                    && (primeiraAVencer == null
                        || c.validade().isBefore(primeiraAVencer.validade()))) {
                primeiraAVencer = c;
            }
        }

        Map<String, List<String>> itens = new LinkedHashMap<>();
        itens.put("certidoes", certidoes.stream()
                .map(c -> c.tipo() + " vence " + c.validade() + " (" + c.natureza() + ")")
                .toList());
        if (primeiraAVencer != null) {
            // A propriedade do conjunto: a primeira a vencer manda.
            itens.put("primeira_a_vencer", List.of(primeiraAVencer.tipo() + " em "
                    + primeiraAVencer.validade()));
        }
        if (!ressalvas.isEmpty()) {
            itens.put("ressalvas", List.copyOf(ressalvas));
        }

        if (reprovadas.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.CONFORME,
                    modo, null, null, null, null, itens);
        }
        itens.put("reprovadas", List.copyOf(reprovadas));
        return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.DIVERGENTE,
                modo, null, null, null,
                reprovadas.size() + " de " + certidoes.size() + " certidão(ões) não atendem: "
                        + String.join(". ", reprovadas),
                itens);
    }
}
