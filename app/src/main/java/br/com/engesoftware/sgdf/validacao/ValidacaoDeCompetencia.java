package br.com.engesoftware.sgdf.validacao;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * V4 — a competência do documento é a exigida, depois da defasagem.
 *
 * <p>Cap. 8.4 e 7.4. A guia do FGTS de um ciclo de julho traz competência
 * 06/2026, e está certa: a defasagem do tipo é M−1. Comparar com a competência
 * do ciclo sem aplicar a defasagem reprovaria <b>toda</b> guia de FGTS.
 *
 * <p>Tipos de defasagem {@code VIGENCIA_NF} e {@code EVENTO} não se comparam por
 * competência — as certidões valem por vigência (V5) e os documentos de evento
 * seguem a data do evento. Para eles V4 responde {@code NAO_APLICAVEL} com
 * motivo, e não silêncio.
 */
public final class ValidacaoDeCompetencia {

    public static final String CODIGO = "V4";

    /**
     * @param competenciaExtraida o que o documento traz, em qualquer forma
     * @param competenciaDoCiclo  a competência do ciclo de faturamento
     * @param defasagem           atributo do tipo documental
     */
    public ResultadoDeValidacao validar(String competenciaExtraida, YearMonth competenciaDoCiclo,
                                        Competencia.Defasagem defasagem) {
        YearMonth exigida = Competencia.exigidaPara(competenciaDoCiclo, defasagem);
        if (exigida == null) {
            return new ResultadoDeValidacao(CODIGO, Veredito.NAO_APLICAVEL,
                    "o tipo tem defasagem " + defasagem + ": não se valida por competência, "
                            + (defasagem == Competencia.Defasagem.VIGENCIA_NF
                                    ? "e sim por vigência na data prevista da NF (V5)"
                                    : "e sim pela data do evento"),
                    Map.of());
        }

        YearMonth lida = Competencia.ler(competenciaExtraida);
        if (lida == null) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "não foi possível ler a competência do documento"
                            + (competenciaExtraida == null || competenciaExtraida.isBlank()
                                    ? ": o campo não foi extraído"
                                    : ": o valor lido foi \"" + competenciaExtraida.trim() + "\"")
                            + ". Esperada: " + Competencia.formatar(exigida),
                    Map.of());
        }

        if (lida.equals(exigida)) {
            return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null,
                    Map.of("competencia", List.of(Competencia.formatar(lida))));
        }

        long meses = java.time.temporal.ChronoUnit.MONTHS.between(exigida, lida);
        return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                "o documento é da competência " + Competencia.formatar(lida)
                        + " e o ciclo de " + Competencia.formatar(competenciaDoCiclo)
                        + " exige " + Competencia.formatar(exigida)
                        + " (defasagem " + defasagem + ") — "
                        + Math.abs(meses) + " mês(es) de diferença",
                Map.of("lida", List.of(Competencia.formatar(lida)),
                        "exigida", List.of(Competencia.formatar(exigida))));
    }
}
