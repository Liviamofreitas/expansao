package br.com.engesoftware.sgdf.extracao;

import java.text.Normalizer;

/**
 * Texto normalizado que <b>não perde a origem</b> de cada caractere.
 *
 * <p>O cap. 8.3 casa âncoras contra texto sem acento e em minúsculas. Normalizar
 * e depois procurar seria fácil; o problema é que a normalização muda o
 * comprimento — "ção" tem 3 caracteres, a forma decomposta tem 4 — e o
 * deslocamento do casamento deixa de apontar para o glifo certo.
 *
 * <p>Por isso a normalização é feita caractere a caractere, guardando de qual
 * índice do texto original cada caractere normalizado veio. Sem isso, o campo
 * extraído aponta para a região errada do documento, que é pior do que não
 * apontar para nenhuma: o auditor confere um trecho e conclui que o sistema
 * leu errado.
 */
public final class TextoNormalizado {

    private final String texto;
    private final int[] origem;

    private TextoNormalizado(String texto, int[] origem) {
        this.texto = texto;
        this.origem = origem;
    }

    public static TextoNormalizado de(String original) {
        StringBuilder saida = new StringBuilder(original.length());
        int[] mapa = new int[original.length() * 2];
        int n = 0;

        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            String semAcento = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
            if (semAcento.isEmpty()) {
                continue;   // o próprio caractere era um acento combinante
            }
            for (int j = 0; j < semAcento.length(); j++) {
                if (n == mapa.length) {
                    int[] maior = new int[mapa.length * 2];
                    System.arraycopy(mapa, 0, maior, 0, mapa.length);
                    mapa = maior;
                }
                saida.append(Character.toLowerCase(semAcento.charAt(j)));
                mapa[n++] = i;   // todos os caracteres gerados vêm do mesmo original
            }
        }
        int[] ajustado = new int[n];
        System.arraycopy(mapa, 0, ajustado, 0, n);
        return new TextoNormalizado(saida.toString(), ajustado);
    }

    public String texto() {
        return texto;
    }

    /** Índice no texto original do caractere normalizado em {@code posicao}. */
    public int origemDe(int posicao) {
        return origem[posicao];
    }

    /**
     * Índice no original logo APÓS o trecho normalizado que termina em
     * {@code fimExclusivo}.
     */
    public int origemDoFim(int fimExclusivo) {
        return fimExclusivo == 0 ? 0 : origem[fimExclusivo - 1] + 1;
    }

    public int comprimento() {
        return texto.length();
    }
}
