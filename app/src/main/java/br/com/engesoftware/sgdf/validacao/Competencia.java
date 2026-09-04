package br.com.engesoftware.sgdf.validacao;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Competência no formato MM/AAAA, e a defasagem do cap. 7.4.
 *
 * <p>A defasagem é <b>atributo do tipo documental, imutável por contrato</b>: a
 * folha é do mês (M), o FGTS e os tributos são do mês anterior (M−1), as
 * certidões valem pela vigência na data da NF, e os documentos de evento seguem
 * o evento. Comparar a competência extraída com a do ciclo sem aplicar a
 * defasagem reprovaria toda guia de FGTS — que é sempre do mês anterior.
 */
public final class Competencia {

    /** Cap. 7.4. */
    public enum Defasagem {
        /** Mesmo mês do ciclo: folha, benefícios, medição, NF. */
        M,
        /** Mês anterior: FGTS, DCTFWeb, DARF, INSS, IRRF. */
        M_MENOS_1,
        /** Certidão: não tem competência, tem vigência na data prevista da NF. */
        VIGENCIA_NF,
        /** Férias, rescisão, admissão: segue a data do evento. */
        EVENTO
    }

    private static final Pattern MM_AAAA = Pattern.compile("(\\d{2})/(\\d{4})");

    private static final java.util.List<String> MESES = java.util.List.of("JANEIRO",
            "FEVEREIRO", "MARÇO", "ABRIL", "MAIO", "JUNHO", "JULHO", "AGOSTO",
            "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO");

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("MM/yyyy");

    private Competencia() {}

    /**
     * Lê a competência em qualquer das formas que os documentos usam.
     *
     * <p>{@code 06/2026} na guia do FGTS, {@code Junho/2026} na DCTFWeb e na
     * FOPAG. Aceitar só a numérica deixaria metade dos documentos sem
     * competência — e V4 sem o que comparar.
     */
    public static YearMonth ler(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = MM_AAAA.matcher(texto);
        if (m.find()) {
            int mes = Integer.parseInt(m.group(1));
            return mes >= 1 && mes <= 12 ? YearMonth.of(Integer.parseInt(m.group(2)), mes) : null;
        }
        String maiusculo = texto.toUpperCase(Locale.ROOT);
        for (int i = 0; i < MESES.size(); i++) {
            int onde = maiusculo.indexOf(MESES.get(i) + "/");
            if (onde >= 0) {
                Matcher ano = Pattern.compile("(\\d{4})")
                        .matcher(maiusculo.substring(onde + MESES.get(i).length()));
                if (ano.find()) {
                    return YearMonth.of(Integer.parseInt(ano.group(1)), i + 1);
                }
            }
        }
        return null;
    }

    /** A competência que um documento com esta defasagem deve trazer. */
    public static YearMonth exigidaPara(YearMonth competenciaDoCiclo, Defasagem defasagem) {
        return switch (defasagem) {
            case M -> competenciaDoCiclo;
            case M_MENOS_1 -> competenciaDoCiclo.minusMonths(1);
            case VIGENCIA_NF, EVENTO -> null;   // não se compara por competência
        };
    }

    public static String formatar(YearMonth competencia) {
        return competencia == null ? null : competencia.format(FORMATO);
    }
}
