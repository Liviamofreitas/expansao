package br.com.engesoftware.sgdf.extracao;

import java.util.List;

/**
 * Os glifos de uma mesma linha da página, ordenados da esquerda para a direita.
 *
 * <p>É a unidade que o {@link LeitorDeTabela} usa. Uma linha visual não é uma
 * linha do texto extraído: o texto vem na ordem de leitura, que num layout de
 * duas colunas alterna entre elas.
 */
public record LinhaVisual(int pagina, float y, List<Glifo> glifos) {

    public LinhaVisual {
        glifos = List.copyOf(glifos);
    }

    /** Texto dos glifos entre {@code xInicio} (inclusive) e {@code xFim} (exclusivo). */
    public String textoEntre(float xInicio, float xFim) {
        StringBuilder sb = new StringBuilder();
        Glifo anterior = null;
        for (Glifo g : glifos) {
            if (g.x() < xInicio || g.x() >= xFim) {
                continue;
            }
            // O PDF não desenha espaços: a separação entre palavras é a lacuna
            // entre glifos. Sem reconstruí-la, "VALE ALIMENTACAO" viraria
            // "VALEALIMENTACAO" e nenhuma âncora casaria.
            if (anterior != null) {
                float lacuna = g.x() - (anterior.x() + anterior.largura());
                if (lacuna > anterior.largura() * 0.3f) {
                    sb.append(' ');
                }
            }
            if (!Character.isWhitespace(g.caractere())) {
                sb.append(g.caractere());
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
            anterior = g;
        }
        return sb.toString().trim().replaceAll("\\s{2,}", " ");
    }

    public String texto() {
        return textoEntre(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    public boolean vazia() {
        return texto().isBlank();
    }
}
