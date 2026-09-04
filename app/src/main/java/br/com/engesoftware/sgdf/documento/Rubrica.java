package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;

/**
 * Uma linha de provento ou desconto do contracheque.
 *
 * @param descricao rubrica como impressa ("VALE ALIMENTACAO", "INSS MES")
 * @param quantidade quantidade, quando a rubrica traz uma; nula quando não
 * @param valor      valor da rubrica
 */
public record Rubrica(String descricao, BigDecimal quantidade, BigDecimal valor) {

    /** Compara a descrição sem acento e sem caixa — a mesma rubrica varia de grafia. */
    public boolean e(String rubrica) {
        return normalizar(descricao).equals(normalizar(rubrica));
    }

    public boolean contem(String trecho) {
        return normalizar(descricao).contains(normalizar(trecho));
    }

    private static String normalizar(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
