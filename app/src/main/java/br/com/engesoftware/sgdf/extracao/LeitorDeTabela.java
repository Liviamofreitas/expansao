package br.com.engesoftware.sgdf.extracao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê tabelas de PDF pela POSIÇÃO dos glifos, não pela ordem de leitura.
 *
 * <p>Por que isso é necessário, com dois casos reais da massa:
 *
 * <p><b>Contracheque.</b> O layout tem proventos à esquerda e descontos à
 * direita. Uma linha com desconto e sem provento sai assim no texto:
 *
 * <pre>
 *   SALARIO  30,00     6.452,92 INSS MES       823,61
 *   TIT ASS ODT BRADESCO        11,49
 * </pre>
 *
 * A segunda linha tem apenas um desconto, mas lida linearmente parece começar na
 * coluna de proventos. Somar as duas colunas erradas troca um desconto de
 * benefício por um provento — e o líquido deixa de fechar.
 *
 * <p><b>Comprovante de pagamento em lote.</b> O nome do funcionário ocupa duas
 * linhas e sai intercalado com a linha de dados:
 *
 * <pre>
 *   DOUGLAS VINISIOS
 *   900059648 00000010225306072026 06/07/2026 CC 2.232,21
 *   NUNES SOUZA
 * </pre>
 *
 * Lido linha a linha, o pagamento de uma pessoa vai para outra.
 *
 * <p>As coordenadas necessárias já vêm da história F1-02; falta usá-las.
 */
public final class LeitorDeTabela {

    /**
     * Como os dados de uma coluna se alinham ao seu rótulo.
     *
     * <p>Precisa ser declarado porque nenhuma heurística acerta os dois casos.
     * O rótulo marca o INÍCIO de uma coluna alinhada à esquerda — a descrição
     * pode ser bem mais larga que ele. E marca aproximadamente o FIM de uma
     * alinhada à direita — o número cresce para a esquerda, invadindo a faixa
     * anterior. Uma fronteira no meio da lacuna dos rótulos erra em ambos, e foi
     * o que aconteceu ao calibrar contra o contracheque e o comprovante em lote:
     * cada um exigia o oposto do outro.
     */
    public enum Alinhamento { ESQUERDA, DIREITA }

    private LeitorDeTabela() {}

    /**
     * Agrupa os glifos da página em linhas visuais.
     *
     * @param toleranciaRelativa fração da altura do glifo que ainda conta como a
     *                           mesma linha. Subscritos e fontes mistas fazem o
     *                           Y variar dentro de uma linha; zero seria rígido
     *                           demais e quebraria cada linha em várias
     */
    public static List<LinhaVisual> agruparEmLinhas(PaginaExtraida pagina,
                                                    float toleranciaRelativa) {
        List<Glifo> ordenados = new ArrayList<>(pagina.glifos());
        ordenados.removeIf(g -> Character.isWhitespace(g.caractere()) || g.largura() <= 0);
        ordenados.sort(Comparator.comparing(Glifo::y).reversed()
                .thenComparing(Glifo::x));   // Y do PDF cresce para cima

        List<LinhaVisual> linhas = new ArrayList<>();
        List<Glifo> atual = new ArrayList<>();
        Float yLinha = null;

        for (Glifo g : ordenados) {
            float tolerancia = Math.max(g.altura(), 1f) * toleranciaRelativa;
            if (yLinha == null || Math.abs(g.y() - yLinha) <= tolerancia) {
                if (yLinha == null) {
                    yLinha = g.y();
                }
                atual.add(g);
            } else {
                linhas.add(fechar(pagina.numero(), yLinha, atual));
                atual = new ArrayList<>(List.of(g));
                yLinha = g.y();
            }
        }
        if (!atual.isEmpty()) {
            linhas.add(fechar(pagina.numero(), yLinha, atual));
        }
        return linhas;
    }

    public static List<LinhaVisual> agruparEmLinhas(PaginaExtraida pagina) {
        return agruparEmLinhas(pagina, 0.5f);
    }

    private static LinhaVisual fechar(int pagina, float y, List<Glifo> glifos) {
        List<Glifo> ordenados = new ArrayList<>(glifos);
        ordenados.sort(Comparator.comparing(Glifo::x));
        return new LinhaVisual(pagina, y, ordenados);
    }

    /**
     * Deduz as faixas das colunas a partir da linha de cabeçalho.
     *
     * <p>Cada rótulo define o início da sua coluna; o fim é o início do rótulo
     * seguinte. Deduzir do cabeçalho, em vez de fixar coordenadas no cadastro,
     * sobrevive a mudanças de margem e de tamanho de papel — que acontecem sem
     * aviso quando o emissor troca o gerador de relatório.
     *
     * <p>Rótulos repetidos são aceitos e desambiguados por ordem: o contracheque
     * tem "Descrição Qtde Valor" duas vezes, uma para proventos e outra para
     * descontos.
     *
     * @param cabecalho linha onde os rótulos aparecem
     * @param rotulos   na ordem em que ocorrem, da esquerda para a direita
     * @param nomes     nome de cada coluna no resultado, mesmo tamanho de {@code rotulos}
     */
    public static List<Coluna> colunasPorCabecalho(LinhaVisual cabecalho,
                                                   List<String> rotulos, List<String> nomes,
                                                   List<Alinhamento> alinhamentos) {
        if (rotulos.size() != nomes.size() || rotulos.size() != alinhamentos.size()) {
            throw new IllegalArgumentException(
                    "rótulos, nomes e alinhamentos precisam ter o mesmo tamanho");
        }
        List<float[]> extremos = new ArrayList<>();   // {início, fim} de cada rótulo
        int posicaoBusca = 0;
        String texto = cabecalho.texto();

        for (String rotulo : rotulos) {
            int achado = texto.indexOf(rotulo, posicaoBusca);
            if (achado < 0) {
                throw new IllegalArgumentException(
                        "rótulo '" + rotulo + "' não encontrado no cabeçalho: " + texto);
            }
            extremos.add(new float[] {
                    xDoCaractere(cabecalho, texto, achado),
                    xFinalDoCaractere(cabecalho, texto, achado + rotulo.length() - 1)});
            posicaoBusca = achado + rotulo.length();
        }

        // A fronteira entre a coluna i e a i+1 é ditada pelo alinhamento da
        // COLUNA SEGUINTE: se ela é alinhada à esquerda, seus dados começam sob
        // o próprio rótulo, e a fronteira é o início dele; se é alinhada à
        // direita, seus dados crescem para trás, e a fronteira tem de recuar até
        // logo depois do rótulo anterior.
        List<Float> fronteiras = new ArrayList<>();
        for (int i = 0; i + 1 < extremos.size(); i++) {
            fronteiras.add(alinhamentos.get(i + 1) == Alinhamento.ESQUERDA
                    ? extremos.get(i + 1)[0] - 1f
                    : extremos.get(i)[1] + 1f);
        }

        List<Coluna> colunas = new ArrayList<>();
        for (int i = 0; i < extremos.size(); i++) {
            colunas.add(new Coluna(nomes.get(i),
                    i == 0 ? Float.NEGATIVE_INFINITY : fronteiras.get(i - 1),
                    i + 1 < extremos.size() ? fronteiras.get(i) : Float.POSITIVE_INFINITY));
        }
        return colunas;
    }

    /**
     * Posição X do caractere que ocupa {@code indice} no texto reconstruído.
     *
     * <p>O texto de {@link LinhaVisual#texto()} insere espaços que não existem
     * como glifo, então o índice do texto não é o índice do glifo. Refaz-se a
     * contagem pulando os espaços inseridos.
     */
    private static float xDoCaractere(LinhaVisual linha, String texto, int indice) {
        int visiveis = 0;
        for (int i = 0; i < indice; i++) {
            if (!Character.isWhitespace(texto.charAt(i))) {
                visiveis++;
            }
        }
        List<Glifo> glifos = linha.glifos();
        return glifos.get(Math.min(visiveis, glifos.size() - 1)).x();
    }

    /** Borda direita do caractere que ocupa {@code indice} no texto reconstruído. */
    private static float xFinalDoCaractere(LinhaVisual linha, String texto, int indice) {
        int visiveis = 0;
        for (int i = 0; i < indice; i++) {
            if (!Character.isWhitespace(texto.charAt(i))) {
                visiveis++;
            }
        }
        List<Glifo> glifos = linha.glifos();
        Glifo g = glifos.get(Math.min(visiveis, glifos.size() - 1));
        return g.x() + g.largura();
    }

    /** Recorta uma linha nas colunas dadas. */
    public static Map<String, String> lerLinha(LinhaVisual linha, List<Coluna> colunas) {
        Map<String, String> celulas = new LinkedHashMap<>();
        for (Coluna c : colunas) {
            celulas.put(c.rotulo(), linha.textoEntre(c.xInicio(), c.xFim()));
        }
        return celulas;
    }

    /**
     * Lê as linhas de dados entre o cabeçalho e o fim da tabela.
     *
     * @param linhas       todas as linhas da página
     * @param indiceCabecalho posição do cabeçalho em {@code linhas}
     * @param colunas      faixas das colunas
     * @param pararEm      texto que encerra a tabela; nulo lê até o fim
     */
    public static List<Map<String, String>> lerAbaixoDe(List<LinhaVisual> linhas,
                                                        int indiceCabecalho,
                                                        List<Coluna> colunas,
                                                        String pararEm) {
        List<Map<String, String>> resultado = new ArrayList<>();
        for (int i = indiceCabecalho + 1; i < linhas.size(); i++) {
            LinhaVisual linha = linhas.get(i);
            if (linha.vazia()) {
                continue;
            }
            if (pararEm != null && linha.texto().contains(pararEm)) {
                break;
            }
            Map<String, String> celulas = lerLinha(linha, colunas);
            if (celulas.values().stream().anyMatch(v -> !v.isBlank())) {
                resultado.add(celulas);
            }
        }
        return resultado;
    }

    /**
     * Deduz as fronteiras das colunas pelas LACUNAS VERTICAIS das linhas de dados.
     *
     * <p>É o método robusto, e o cabeçalho não substitui: o rótulo marca o início
     * de uma coluna alinhada à esquerda e o fim de uma alinhada à direita, então
     * qualquer fronteira derivada dele erra num dos dois casos. No contracheque
     * real isso truncava "TIT ASS ODT BRADESCO" em "TIT ASS ODT BRADES", porque a
     * descrição é mais larga que o rótulo "Descrição" que a encabeça.
     *
     * <p>A lacuna, ao contrário, é observável: uma faixa vertical que nenhuma
     * linha de dados ocupa é uma separação real entre colunas, qualquer que seja
     * o alinhamento.
     *
     * @param linhasDeDados linhas da tabela, sem o cabeçalho
     * @param larguraMinima largura em pontos que uma lacuna precisa ter para
     *                      contar como separação — abaixo disso é só o espaço
     *                      entre palavras
     * @param nomes         nome das colunas, da esquerda para a direita
     */
    public static List<Coluna> colunasPorProjecao(List<LinhaVisual> linhasDeDados,
                                                  float larguraMinima, List<String> nomes) {
        float xMin = Float.MAX_VALUE;
        float xMax = Float.MIN_VALUE;
        for (LinhaVisual l : linhasDeDados) {
            for (Glifo g : l.glifos()) {
                xMin = Math.min(xMin, g.x());
                xMax = Math.max(xMax, g.x() + g.largura());
            }
        }
        if (xMin > xMax) {
            throw new IllegalArgumentException("nenhuma linha de dados com conteúdo");
        }

        int largura = (int) Math.ceil(xMax - xMin) + 1;
        boolean[] ocupado = new boolean[largura];
        for (LinhaVisual l : linhasDeDados) {
            for (Glifo g : l.glifos()) {
                int de = (int) Math.floor(g.x() - xMin);
                int ate = (int) Math.ceil(g.x() + g.largura() - xMin);
                for (int i = Math.max(0, de); i < Math.min(largura, ate); i++) {
                    ocupado[i] = true;
                }
            }
        }

        List<Float> fronteiras = new ArrayList<>();
        int inicioLacuna = -1;
        for (int i = 0; i <= largura; i++) {
            boolean livre = i < largura && !ocupado[i];
            if (livre && inicioLacuna < 0) {
                inicioLacuna = i;
            } else if (!livre && inicioLacuna >= 0) {
                if (i - inicioLacuna >= larguraMinima) {
                    fronteiras.add(xMin + (inicioLacuna + i) / 2f);
                }
                inicioLacuna = -1;
            }
        }

        if (fronteiras.size() != nomes.size() - 1) {
            throw new IllegalArgumentException(
                    "projeção achou " + fronteiras.size() + " separação(ões) para "
                            + nomes.size() + " coluna(s). Ajuste larguraMinima ("
                            + larguraMinima + ") ou declare as colunas pelo cabeçalho.");
        }

        List<Coluna> colunas = new ArrayList<>();
        for (int i = 0; i < nomes.size(); i++) {
            float inicio = i == 0 ? Float.NEGATIVE_INFINITY : fronteiras.get(i - 1);
            float fim = i == nomes.size() - 1 ? Float.POSITIVE_INFINITY : fronteiras.get(i);
            colunas.add(new Coluna(nomes.get(i), inicio, fim));
        }
        return colunas;
    }

    /** Índice da primeira linha que contém {@code marca}, ou -1. */
    public static int indiceDaLinhaCom(List<LinhaVisual> linhas, String marca) {
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).texto().contains(marca)) {
                return i;
            }
        }
        return -1;
    }
}
