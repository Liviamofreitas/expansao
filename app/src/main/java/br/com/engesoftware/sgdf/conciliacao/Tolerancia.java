package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Quanto uma comparação pode divergir e ainda ser conforme.
 *
 * <p>Corresponde a {@code regra_conciliacao.tolerancia}. É <b>cadastro</b>: o
 * cap. 9 manda que regra sem tolerância cadastrada opere em modo ALERTA e nunca
 * bloqueie, e o banco impõe isso numa restrição.
 *
 * <p>A distinção entre absoluta e percentual não é preciosismo. A conciliação
 * real da base do FGTS deu delta de R$ 0,01 sobre R$ 49.693,18 — arredondamento
 * por colaborador, inevitável. Uma tolerância absoluta de R$ 0,01 sobre o total
 * absorveria por acaso; com o dobro de colaboradores, não absorveria mais. A
 * tolerância dessa regra tem de ser percentual, e é o que o cap. 9 já cadastra
 * (±0,5%).
 *
 * @param absoluta   diferença aceita em reais; nula quando não se aplica
 * @param percentual fração do valor esperado aceita como diferença (0,005 = 0,5%)
 * @param carenciaEmDias dias que o pagamento pode passar do vencimento
 */
public record Tolerancia(BigDecimal absoluta, BigDecimal percentual, int carenciaEmDias) {

    /** Sem tolerância cadastrada — a regra opera em ALERTA (cap. 9). */
    public static final Tolerancia NENHUMA = new Tolerancia(null, null, 0);

    public static Tolerancia deCentavos(int centavos) {
        return new Tolerancia(new BigDecimal(centavos).movePointLeft(2), null, 0);
    }

    public static Tolerancia dePercentual(String percentual) {
        return new Tolerancia(null, new BigDecimal(percentual), 0);
    }

    public Tolerancia comCarencia(int dias) {
        return new Tolerancia(absoluta, percentual, dias);
    }

    public boolean cadastrada() {
        return absoluta != null || percentual != null || carenciaEmDias > 0;
    }

    /** A margem em reais admitida para um valor esperado. */
    public BigDecimal margemPara(BigDecimal esperado) {
        BigDecimal porAbsoluta = absoluta == null ? BigDecimal.ZERO : absoluta;
        BigDecimal porPercentual = percentual == null || esperado == null
                ? BigDecimal.ZERO
                : esperado.abs().multiply(percentual, MathContext.DECIMAL64);
        return porAbsoluta.max(porPercentual);
    }

    public boolean absorve(BigDecimal esperado, BigDecimal obtido) {
        if (esperado == null || obtido == null) {
            return false;
        }
        return esperado.subtract(obtido).abs().compareTo(margemPara(esperado)) <= 0;
    }

    /** Como a margem aparece na mensagem ao usuário. */
    public String descricao() {
        if (!cadastrada()) {
            return "sem tolerância cadastrada";
        }
        StringBuilder sb = new StringBuilder();
        if (absoluta != null) {
            sb.append("± R$ ").append(absoluta);
        }
        if (percentual != null) {
            if (sb.length() > 0) {
                sb.append(" ou ");
            }
            sb.append("± ").append(percentual.movePointRight(2)).append("%");
        }
        if (carenciaEmDias > 0) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append("carência de ").append(carenciaEmDias).append(" dia(s)");
        }
        return sb.toString();
    }
}
