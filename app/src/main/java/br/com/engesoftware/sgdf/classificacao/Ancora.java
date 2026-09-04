package br.com.engesoftware.sgdf.classificacao;

import java.util.regex.Pattern;

/**
 * Expressão que deve ocorrer no texto de um documento do tipo, com o peso que
 * ela tem na decisão.
 *
 * <p>Corresponde a uma entrada de {@code regra_reconhecimento.ancoras}. É
 * cadastro, não código: acrescentar um tipo documental não deve exigir compilar
 * nada (cap. 1, princípio 2).
 *
 * <p>A expressão é aplicada sobre o texto NORMALIZADO — sem acento, em
 * minúsculas, com espaços colapsados. O achado A1 explica o colapso: no
 * documento real o título quebra linha ("TRIBUTOS\nFEDERAIS") e qualquer âncora
 * de mais de uma palavra falharia sem ele.
 *
 * @param expressao   forma a procurar
 * @param peso        contribuição para o score quando ocorre
 * @param discriminante verdadeira quando a âncora é o que separa este tipo de
 *                      um parecido; a ausência dela zera o tipo em vez de só
 *                      descontar peso
 */
public record Ancora(Pattern expressao, double peso, boolean discriminante,
                     boolean ignorandoEspacos) {

    public Ancora {
        if (peso <= 0) {
            throw new IllegalArgumentException("âncora com peso não positivo não decide nada: "
                    + expressao + " peso=" + peso);
        }
    }

    public static Ancora de(String expressao, double peso) {
        return new Ancora(Pattern.compile(expressao), peso, false, false);
    }

    /**
     * Âncora que separa este tipo de outro parecido.
     *
     * <p>Existe porque o peso sozinho não resolve a ambiguidade real da massa:
     * a certidão de ações cíveis e criminais e a de falências e recuperações
     * judiciais compartilham o cabeçalho inteiro — mesmo tribunal, mesma
     * fórmula, mesmas instâncias — e diferem por uma expressão. Com pesos, as
     * duas pontuam alto nas duas regras; com discriminante, cada uma só pontua
     * na sua.
     */
    public static Ancora discriminante(String expressao, double peso) {
        return new Ancora(Pattern.compile(expressao), peso, true, false);
    }

    /**
     * Âncora casada contra o texto SEM NENHUM ESPAÇO — para títulos com
     * espaçamento entre letras.
     *
     * <p>Achado A22: a FOPAG imprime o rótulo do centro de custo com tracking, e
     * o extrator devolve {@code CENTRO D  E   C   U  S   T  O   :} — com número
     * VARIÁVEL de espaços entre as letras. O limite entre "DE" e "CUSTO" não é
     * recuperável: nenhuma contagem de espaços o distingue dos espaços internos.
     *
     * <p>Por isso a regra de ouro é <b>não ancorar num título com tracking</b>
     * quando o documento oferece alternativa — e a FOPAG oferece várias. Este
     * construtor existe para quando não oferece: a expressão é escrita sem
     * espaços e casa contra o texto sem espaços, onde a ambiguidade some porque
     * a informação perdida deixa de ser necessária.
     */
    public static Ancora semEspacos(String expressaoSemEspacos, double peso,
                                    boolean discriminante) {
        return new Ancora(Pattern.compile(expressaoSemEspacos), peso, discriminante, true);
    }

    public boolean ocorreEm(String textoNormalizado) {
        return expressao.matcher(textoNormalizado).find();
    }

    /**
     * @param normalizado  texto normalizado com os espaços colapsados
     * @param semEspacos   o mesmo texto sem espaço nenhum
     */
    public boolean ocorreEm(String normalizado, String semEspacos) {
        return expressao.matcher(ignorandoEspacos ? semEspacos : normalizado).find();
    }
}
