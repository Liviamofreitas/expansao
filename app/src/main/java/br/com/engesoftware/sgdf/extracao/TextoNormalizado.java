package br.com.engesoftware.sgdf.extracao;

import java.text.Normalizer;

/**
 * Texto normalizado que <b>não perde a origem</b> de cada caractere.
 *
 * <p>O cap. 8.3 casa âncoras contra texto sem acento e em minúsculas. Normalizar
 * e depois procurar seria fácil; o problema é que a normalização muda o
 * comprimento, e o deslocamento do casamento deixa de apontar para o glifo
 * certo. Por isso a normalização guarda de qual índice do texto original cada
 * caractere normalizado veio. Sem isso, o campo extraído aponta para a região
 * errada — pior do que não apontar para nenhuma, porque o auditor confere um
 * trecho e conclui que o sistema leu errado.
 *
 * <p><b>Três tratamentos vieram do primeiro contato com documentos reais</b>
 * (certidões da RFB e do GDF, DCTFWeb e comprovantes bancários):
 *
 * <ol>
 *   <li><b>Espaço rígido.</b> A CND da Receita Federal traz 266 ocorrências de
 *       {@code U+00A0} no corpo: onde parece haver um espaço, há um NO-BREAK
 *       SPACE. Uma âncora escrita com espaço comum não casaria.</li>
 *   <li><b>Ligaduras tipográficas.</b> Os comprovantes trazem {@code U+FB01}
 *       ("ﬁ" em "deﬁciência"). Uma âncora com "fi" não casaria.</li>
 *   <li><b>Quebra de linha dentro da âncora.</b> O título da CND ocupa duas
 *       linhas, e "TRIBUTOS FEDERAIS" aparece como {@code TRIBUTOS\nFEDERAIS}.
 *       Como os títulos são justamente as âncoras do cap. 8.5, e títulos longos
 *       sempre quebram, qualquer sequência de separadores vira um único espaço.
 *       Sem isso, praticamente nenhuma âncora multi-palavra funcionaria.</li>
 * </ol>
 *
 * <p>Os dois primeiros saem de graça trocando NFD por <b>NFKD</b>: a
 * decomposição de compatibilidade já converte NBSP em espaço e desfaz
 * ligaduras. O terceiro é o colapso de separadores, feito aqui.
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
        int[] mapa = new int[Math.max(16, original.length() * 2)];
        int n = 0;
        boolean espacoPendente = false;

        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);

            // Qualquer separador — espaço, NBSP, tabulação, quebra de linha —
            // colapsa num único espaço. Espaço no início é descartado.
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                if (n > 0) {
                    espacoPendente = true;
                }
                continue;
            }

            // NFKD: desfaz ligaduras e converte formas de compatibilidade.
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFKD)
                    .replaceAll("\\p{M}+", "");
            if (base.isEmpty()) {
                continue;   // o próprio caractere era um acento combinante
            }

            if (espacoPendente) {
                // O espaço herda a origem do caractere que vem depois dele: é o
                // que faz um casamento iniciado no espaço apontar para texto.
                mapa = garantir(mapa, n);
                saida.append(' ');
                mapa[n++] = i;
                espacoPendente = false;
            }

            for (int j = 0; j < base.length(); j++) {
                mapa = garantir(mapa, n);
                saida.append(Character.toLowerCase(base.charAt(j)));
                mapa[n++] = i;   // todos os caracteres gerados vêm do mesmo original
            }
        }

        int[] ajustado = new int[n];
        System.arraycopy(mapa, 0, ajustado, 0, n);
        return new TextoNormalizado(saida.toString(), ajustado);
    }

    private static int[] garantir(int[] mapa, int n) {
        if (n < mapa.length) {
            return mapa;
        }
        int[] maior = new int[mapa.length * 2];
        System.arraycopy(mapa, 0, maior, 0, mapa.length);
        return maior;
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
