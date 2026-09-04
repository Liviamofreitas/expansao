package br.com.engesoftware.sgdf.validacao;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Datas como os documentos as escrevem.
 *
 * <p><b>Achado A25.</b> A certidão negativa do GDF não imprime a validade em
 * {@code dd/mm/aaaa}: escreve <i>"Válida até 27 de agosto de 2026."</i>. E o
 * documento contém outra data em formato numérico — {@code 04/07/2003}, do
 * decreto que o ampara. Um extrator que pegasse "a primeira data do documento"
 * daria uma certidão vencida há vinte anos, e V5 reprovaria um documento
 * perfeitamente válido.
 *
 * <p>Por isso a extração de data é sempre <b>ancorada num rótulo</b>, e o
 * formato por extenso é uma das formas que o rótulo pode apresentar.
 */
public final class Datas {

    private static final List<String> MESES = List.of("janeiro", "fevereiro", "março",
            "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro",
            "novembro", "dezembro");

    /** {@code 27 de agosto de 2026} */
    public static final Pattern POR_EXTENSO = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(" + String.join("|", MESES) + ")\\s+de\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    /** {@code 27/08/2026} */
    public static final Pattern NUMERICA = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

    private Datas() {}

    /**
     * A primeira data do texto, em qualquer das duas formas, ou nulo.
     *
     * <p>Não use sobre o documento inteiro: use sobre o trecho que o rótulo
     * delimita. A justificativa está no cabeçalho da classe.
     */
    public static LocalDate primeira(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher extenso = POR_EXTENSO.matcher(texto);
        if (extenso.find()) {
            return montar(Integer.parseInt(extenso.group(1)),
                    MESES.indexOf(extenso.group(2).toLowerCase(Locale.ROOT)) + 1,
                    Integer.parseInt(extenso.group(3)));
        }
        Matcher numerica = NUMERICA.matcher(texto);
        if (numerica.find()) {
            return montar(Integer.parseInt(numerica.group(1)),
                    Integer.parseInt(numerica.group(2)),
                    Integer.parseInt(numerica.group(3)));
        }
        return null;
    }

    /**
     * A data que vem depois de {@code rotulo}, na mesma frase.
     *
     * <p>A janela para no primeiro ponto final: sem isso, "Válida até 27 de
     * agosto de 2026. Certidão expedida conforme Decreto de 04/07/2003" daria
     * a data errada quando a primeira estivesse ausente.
     */
    public static LocalDate depoisDe(String texto, String rotulo) {
        if (texto == null || rotulo == null) {
            return null;
        }
        int i = texto.toLowerCase(Locale.ROOT).indexOf(rotulo.toLowerCase(Locale.ROOT));
        if (i < 0) {
            return null;
        }
        String resto = texto.substring(i + rotulo.length());
        int ponto = resto.indexOf('.');
        // Um ponto de milhar ou de data numérica não encerra a frase.
        while (ponto >= 0 && ponto + 1 < resto.length()
                && Character.isDigit(resto.charAt(ponto + 1))) {
            int proximo = resto.indexOf('.', ponto + 1);
            if (proximo < 0) {
                ponto = -1;
                break;
            }
            ponto = proximo;
        }
        return primeira(ponto > 0 ? resto.substring(0, ponto) : resto);
    }

    private static LocalDate montar(int dia, int mes, int ano) {
        try {
            return LocalDate.of(ano, mes, dia);
        } catch (DateTimeException e) {
            return null;   // 31 de fevereiro não é data, é erro de digitação
        }
    }
}
