package br.com.engesoftware.sgdf.extracao;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o valor que fica ABAIXO de um rótulo, pela coordenada — não pela
 * vizinhança no texto.
 *
 * <p><b>Achado A20.</b> Metade dos campos rotulados dos documentos reais não se
 * extrai por regex de vizinhança. Na guia do FGTS Digital:
 *
 * <pre>
 *   Pagar este documento até | CPF/CNPJ do Empregador | Nome/Razão Social
 *   20/07/2026               | 00.681.946             | ENGESOFTWARE ...
 * </pre>
 *
 * <p>No texto linear isso vira {@code ... pagar este documento ate cpf/cnpj do
 * empregador nome/razao social do empregador 20/07/2026 00.681.946
 * engesoftware ...} — os rótulos todos primeiro, os valores todos depois.
 * Nenhuma janela de vizinhança alcança o valor sem atravessar os outros
 * rótulos, e alargá-la faz capturar o campo errado.
 *
 * <p>A regra que funciona é geométrica e simples: <b>o valor é o primeiro
 * conteúdo abaixo do rótulo, dentro da faixa horizontal dele</b>. Vale para o
 * rótulo sozinho na linha e para a linha com vários rótulos.
 */
public final class ExtratorPorRotulo {

    private ExtratorPorRotulo() {}

    /**
     * O valor abaixo de {@code rotulo}.
     *
     * @param pagina         página onde procurar
     * @param rotulosDaLinha todos os rótulos da linha de cabeçalho, da esquerda
     *                       para a direita — necessários para saber onde a
     *                       coluna do rótulo pedido termina
     * @param rotulo         qual deles se quer, entre os de {@code rotulosDaLinha}
     * @return o valor com a região de onde foi lido, ou nulo
     */
    public static CampoExtraido valorAbaixoDe(PaginaExtraida pagina, List<String> rotulosDaLinha,
                                              String rotulo, String nomeDoCampo) {
        return valorAbaixoDe(pagina, rotulosDaLinha, rotulo, nomeDoCampo,
                java.util.Collections.nCopies(rotulosDaLinha.size(),
                        LeitorDeTabela.Alinhamento.ESQUERDA));
    }

    /**
     * Idem, declarando o alinhamento de cada coluna.
     *
     * <p>É necessário quando a coluna é numérica: os valores crescem para a
     * ESQUERDA a partir da borda direita, e a fronteira deduzida do início do
     * rótulo os corta. Na guia do FGTS, a coluna "FGTS Total" alinhada à
     * esquerda devolvia {@code 0,00} — o valor da coluna anterior — em vez de
     * {@code 119.301,51}.
     */
    public static CampoExtraido valorAbaixoDe(PaginaExtraida pagina, List<String> rotulosDaLinha,
                                              String rotulo, String nomeDoCampo,
                                              List<LeitorDeTabela.Alinhamento> alinhamentos) {
        int indice = rotulosDaLinha.indexOf(rotulo);
        if (indice < 0) {
            throw new IllegalArgumentException(
                    "rótulo '" + rotulo + "' não está entre os declarados: " + rotulosDaLinha);
        }
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pagina);
        int iCabecalho = LeitorDeTabela.indiceDaLinhaCom(linhas, rotulo);
        if (iCabecalho < 0) {
            return null;
        }
        LinhaVisual cabecalho = linhas.get(iCabecalho);

        // A LINHA DE VALORES do cabeçalho: a primeira abaixo dele com conteúdo
        // na faixa da PRIMEIRA coluna. Ancorar na primeira coluna, e não em
        // "qualquer coluna", é o que impede que um rótulo solto de outro bloco,
        // impresso entre o cabeçalho e a sua linha de valores, seja tomado como
        // valor — foi o que aconteceu na DCTFWeb, onde "Pagar este documento
        // até" fica entre o cabeçalho e a linha dos números.
        Coluna primeira = colunaDo(cabecalho, rotulosDaLinha, 0, alinhamentos);
        Coluna coluna = colunaDo(cabecalho, rotulosDaLinha, indice, alinhamentos);

        for (int i = iCabecalho + 1; i < linhas.size(); i++) {
            LinhaVisual linha = linhas.get(i);
            if (linha.textoEntre(primeira.xInicio(), primeira.xFim()).isBlank()) {
                continue;
            }
            String valor = linha.textoEntre(coluna.xInicio(), coluna.xFim());
            return valor.isBlank() ? null
                    : new CampoExtraido(nomeDoCampo, valor.trim(), 1.0, regiaoDa(linha, coluna));
        }
        return null;
    }

    /**
     * Idem, recortando do valor lido só o que casa com {@code padraoDoValor}.
     *
     * <p>A célula pode trazer mais que o campo — {@code "1 0126071549847969-2
     * CAIXA 10415"} traz número da página, identificador e tag na mesma faixa
     * quando os rótulos são mais estreitos que os dados. O padrão recorta.
     */
    public static CampoExtraido valorAbaixoDe(PaginaExtraida pagina, List<String> rotulosDaLinha,
                                              String rotulo, String nomeDoCampo,
                                              Pattern padraoDoValor) {
        CampoExtraido bruto = valorAbaixoDe(pagina, rotulosDaLinha, rotulo, nomeDoCampo);
        if (bruto == null) {
            return null;
        }
        Matcher m = padraoDoValor.matcher(bruto.valor());
        if (!m.find()) {
            return null;
        }
        return new CampoExtraido(nomeDoCampo, m.group(m.groupCount() >= 1 ? 1 : 0),
                bruto.confianca(), bruto.posicao());
    }

    public static CampoExtraido valorAbaixoDe(PaginaExtraida pagina, List<String> rotulosDaLinha,
                                              String rotulo, String nomeDoCampo,
                                              List<LeitorDeTabela.Alinhamento> alinhamentos,
                                              Pattern padraoDoValor) {
        CampoExtraido bruto =
                valorAbaixoDe(pagina, rotulosDaLinha, rotulo, nomeDoCampo, alinhamentos);
        if (bruto == null || padraoDoValor == null) {
            return bruto;
        }
        Matcher m = padraoDoValor.matcher(bruto.valor());
        if (!m.find()) {
            return null;
        }
        return new CampoExtraido(nomeDoCampo, m.group(m.groupCount() >= 1 ? 1 : 0),
                bruto.confianca(), bruto.posicao());
    }

    /** Procura o rótulo em todas as páginas e devolve o primeiro valor achado. */
    public static CampoExtraido valorAbaixoDe(TextoExtraido texto, List<String> rotulosDaLinha,
                                              String rotulo, String nomeDoCampo,
                                              Pattern padraoDoValor) {
        return valorAbaixoDe(texto, rotulosDaLinha, rotulo, nomeDoCampo,
                java.util.Collections.nCopies(rotulosDaLinha.size(),
                        LeitorDeTabela.Alinhamento.ESQUERDA), padraoDoValor);
    }

    public static CampoExtraido valorAbaixoDe(TextoExtraido texto, List<String> rotulosDaLinha,
                                              String rotulo, String nomeDoCampo,
                                              List<LeitorDeTabela.Alinhamento> alinhamentos,
                                              Pattern padraoDoValor) {
        for (PaginaExtraida pagina : texto.paginas()) {
            CampoExtraido achado = valorAbaixoDe(pagina, rotulosDaLinha, rotulo, nomeDoCampo,
                    alinhamentos, padraoDoValor);
            if (achado != null) {
                return achado;
            }
        }
        return null;
    }

    /**
     * A faixa horizontal do rótulo pedido.
     *
     * <p>Rótulo sozinho na linha ocupa a linha inteira; com vizinhos, a faixa
     * vai do início dele ao início do próximo. Os rótulos são alinhados à
     * esquerda nestes documentos, e é isso que a fronteira assume — o valor
     * pode ficar centralizado sob o rótulo e ainda cair na faixa certa.
     */
    private static Coluna colunaDo(LinhaVisual cabecalho, List<String> rotulos, int indice,
                                   List<LeitorDeTabela.Alinhamento> alinhamentos) {
        if (rotulos.size() == 1) {
            // Rótulo sem vizinho: a faixa é a extensão dele mesmo. Usar a linha
            // inteira faria o valor ser procurado em qualquer lugar abaixo, e o
            // primeiro texto que aparecesse — na guia do FGTS, o cabeçalho
            // "CPF/CNPJ do Empregador", que fica entre o rótulo e o seu valor —
            // seria tomado como valor.
            return LeitorDeTabela.faixaDo(cabecalho, rotulos.get(0));
        }
        return LeitorDeTabela.colunasPorCabecalho(cabecalho, rotulos, rotulos, alinhamentos)
                .get(indice);
    }

    private static RegiaoNoDocumento regiaoDa(LinhaVisual linha, Coluna coluna) {
        List<Retangulo> retangulos = new java.util.ArrayList<>();
        float x0 = Float.MAX_VALUE;
        float x1 = Float.MIN_VALUE;
        float y0 = Float.MAX_VALUE;
        float y1 = Float.MIN_VALUE;
        for (Glifo g : linha.glifos()) {
            if (g.x() < coluna.xInicio() || g.x() >= coluna.xFim()) {
                continue;
            }
            x0 = Math.min(x0, g.x());
            x1 = Math.max(x1, g.x() + g.largura());
            y0 = Math.min(y0, g.y());
            y1 = Math.max(y1, g.y() + g.altura());
        }
        retangulos.add(new Retangulo(x0, y0, x1 - x0, y1 - y0));
        return new RegiaoNoDocumento(linha.pagina(), retangulos);
    }
}
