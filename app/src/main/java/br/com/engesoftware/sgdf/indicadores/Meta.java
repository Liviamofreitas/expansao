package br.com.engesoftware.sgdf.indicadores;

import java.math.BigDecimal;

/**
 * O alvo de um indicador do cap. 21 — quando existe.
 *
 * <p><b>Metade da tabela do capítulo não tem alvo, e tratá-la como se tivesse é
 * um erro de categoria.</b> "tendência ↑", "tendência ↓" e "monitorar" não são
 * números a atingir: são pedidos para olhar a série ao longo do tempo. Um
 * {@code boolean atingiu()} obrigaria cada um desses a responder sim ou não, e
 * qualquer das duas respostas seria inventada — pior, a resposta "sim" faria o
 * painel declarar sucesso sobre um KRI cuja única leitura possível é a
 * comparação com o mês anterior.
 *
 * <p>Por isso {@link Sentido#MONITORAR} é um sentido de primeira classe, e a
 * situação resultante é {@link Indicador.Situacao#MONITORADO} — nem atingida
 * nem descumprida.
 */
public record Meta(Sentido sentido, BigDecimal limite) {

    public Meta {
        if (sentido == Sentido.MONITORAR && limite != null) {
            throw new IllegalArgumentException(
                    "meta de monitoramento não tem limite: se há número a atingir, o "
                            + "sentido é MAIOR_OU_IGUAL, MENOR_OU_IGUAL ou IGUAL");
        }
        if (sentido != Sentido.MONITORAR && limite == null) {
            throw new IllegalArgumentException(sentido + " exige limite");
        }
    }

    public static Meta noMinimo(String limite) {
        return new Meta(Sentido.MAIOR_OU_IGUAL, new BigDecimal(limite));
    }

    public static Meta noMaximo(String limite) {
        return new Meta(Sentido.MENOR_OU_IGUAL, new BigDecimal(limite));
    }

    public static Meta exatamente(String limite) {
        return new Meta(Sentido.IGUAL, new BigDecimal(limite));
    }

    /** KRI: acompanhar a série, sem número a bater. */
    public static Meta monitorar() {
        return new Meta(Sentido.MONITORAR, null);
    }

    /** Nulo quando não há meta a avaliar — o chamador decide o que dizer. */
    public Boolean cumpre(BigDecimal valor) {
        if (sentido == Sentido.MONITORAR || valor == null) {
            return null;
        }
        int c = valor.compareTo(limite);
        return switch (sentido) {
            case MAIOR_OU_IGUAL -> c >= 0;
            case MENOR_OU_IGUAL -> c <= 0;
            case IGUAL -> c == 0;
            case MONITORAR -> null;
        };
    }

    @Override
    public String toString() {
        return switch (sentido) {
            case MAIOR_OU_IGUAL -> "≥ " + limite;
            case MENOR_OU_IGUAL -> "≤ " + limite;
            case IGUAL -> "= " + limite;
            case MONITORAR -> "monitorar";
        };
    }

    public enum Sentido { MAIOR_OU_IGUAL, MENOR_OU_IGUAL, IGUAL, MONITORAR }
}
