package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;

/**
 * Uma linha de provento ou desconto da folha.
 *
 * <p>O CÓDIGO é a chave estável. A FOPAG o imprime ("08305 VALE ALIMENTACAO"),
 * o contracheque não — e é por isso que o contracheque é fonte de segunda
 * escolha: a descrição varia de grafia entre competências e entre sistemas,
 * o código não. Onde houver código, a junção é por ele.
 *
 * @param codigo     código da rubrica na folha; nulo quando o documento não imprime
 * @param descricao  rubrica como impressa ("VALE ALIMENTACAO", "INSS MES")
 * @param quantidade referência ou quantidade, quando a rubrica traz uma
 * @param valor      valor da rubrica
 */
public record Rubrica(String codigo, String descricao, BigDecimal quantidade, BigDecimal valor) {

    /** Rubrica de documento que não imprime código — o contracheque. */
    public static Rubrica semCodigo(String descricao, BigDecimal quantidade, BigDecimal valor) {
        return new Rubrica(null, descricao, quantidade, valor);
    }

    public boolean temCodigo(String codigo) {
        return this.codigo != null && this.codigo.equals(codigo);
    }

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
