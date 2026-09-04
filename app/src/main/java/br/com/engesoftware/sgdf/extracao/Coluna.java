package br.com.engesoftware.sgdf.extracao;

/**
 * Uma coluna da tabela, delimitada por uma faixa horizontal.
 *
 * @param rotulo   nome da coluna, como sai no cabeçalho
 * @param xInicio  borda esquerda, inclusive
 * @param xFim     borda direita, exclusiva
 */
public record Coluna(String rotulo, float xInicio, float xFim) {

    public Coluna {
        if (xFim <= xInicio) {
            throw new IllegalArgumentException(
                    "coluna '" + rotulo + "' com faixa vazia: " + xInicio + ".." + xFim);
        }
    }
}
