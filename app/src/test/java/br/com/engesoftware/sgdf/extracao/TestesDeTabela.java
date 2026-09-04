package br.com.engesoftware.sgdf.extracao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Testes do leitor de tabela por coordenadas.
 *
 * <p>O layout reproduzido e o do contracheque real: proventos a esquerda,
 * descontos a direita, e linhas em que so ha desconto. Lido linearmente, um
 * desconto de beneficio vira provento — e o liquido deixa de fechar.
 */
public final class TestesDeTabela {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        agrupamentoEmLinhas();
        colunasPorProjecao();
        linhaSoComDesconto();
        descricaoMaisLargaQueORotulo();
        reconstrucaoDeEspacos();

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    static void agrupamentoEmLinhas() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);

        ok("Tabela . as linhas visuais sao agrupadas por coordenada Y",
                linhas.size() == 5);
        ok("Tabela . a primeira linha e o cabecalho",
                linhas.get(0).texto().startsWith("Descricao"));
        ok("Tabela . dentro da linha os glifos vem da esquerda para a direita",
                linhas.get(1).texto().startsWith("SALARIO"));
    }

    static void colunasPorProjecao() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        List<Coluna> colunas = LeitorDeTabela.colunasPorProjecao(
                linhas.subList(1, linhas.size()), 6,
                List.of("prov", "prov_valor", "desc", "desc_valor"));

        ok("Tabela . a projecao encontra as quatro colunas", colunas.size() == 4);

        List<Map<String, String>> dados =
                LeitorDeTabela.lerAbaixoDe(linhas, 0, colunas, null);
        ok("Tabela . todas as linhas de dados sao lidas", dados.size() == 4);
        ok("Tabela . a primeira linha tem provento e desconto",
                dados.get(0).get("prov").equals("SALARIO")
                        && dados.get(0).get("prov_valor").equals("6.452,92")
                        && dados.get(0).get("desc").equals("INSS MES")
                        && dados.get(0).get("desc_valor").equals("823,61"));
    }

    /**
     * O caso que motivou tudo. No contracheque real, "TIT ASS ODT BRADESCO" e
     * "VALE ALIMENTACAO" aparecem sozinhos na linha, na coluna da direita. Lidos
     * linearmente pareceriam comecar na coluna de proventos.
     */
    static void linhaSoComDesconto() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        List<Coluna> colunas = LeitorDeTabela.colunasPorProjecao(
                linhas.subList(1, linhas.size()), 6,
                List.of("prov", "prov_valor", "desc", "desc_valor"));
        List<Map<String, String>> dados =
                LeitorDeTabela.lerAbaixoDe(linhas, 0, colunas, null);

        Map<String, String> terceira = dados.get(2);
        ok("Tabela . linha so com desconto nao inventa provento",
                terceira.get("prov").isBlank() && terceira.get("prov_valor").isBlank());
        ok("Tabela . e o desconto vai para a coluna certa",
                terceira.get("desc").equals("TIT ASS ODT BRADESCO")
                        && terceira.get("desc_valor").equals("11,49"));

        Map<String, String> quarta = dados.get(3);
        ok("Tabela . o vale alimentacao tambem e desconto, nao provento",
                quarta.get("prov").isBlank()
                        && quarta.get("desc").equals("VALE ALIMENTACAO"));
    }

    /**
     * A descricao e mais larga que o rotulo "Descricao" que a encabeca. Derivar
     * a fronteira do cabecalho truncaria — foi o que aconteceu na primeira
     * versao, que devolvia "TIT ASS ODT BRADES".
     */
    static void descricaoMaisLargaQueORotulo() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        List<Coluna> colunas = LeitorDeTabela.colunasPorProjecao(
                linhas.subList(1, linhas.size()), 6,
                List.of("prov", "prov_valor", "desc", "desc_valor"));
        List<Map<String, String>> dados =
                LeitorDeTabela.lerAbaixoDe(linhas, 0, colunas, null);

        ok("Tabela . descricao mais larga que o rotulo nao e truncada",
                dados.get(2).get("desc").length() == "TIT ASS ODT BRADESCO".length());
    }

    /**
     * O PDF nao desenha espacos: a separacao entre palavras e a lacuna entre
     * glifos. Sem reconstrui-la, "VALE ALIMENTACAO" viraria "VALEALIMENTACAO".
     */
    static void reconstrucaoDeEspacos() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        ok("Tabela . o espaco entre palavras e reconstruido pela lacuna",
                linhas.get(4).texto().contains("VALE ALIMENTACAO"));
    }

    // -------------------------------------------------------------------------

    /** Layout de duas colunas, como o contracheque: proventos | descontos. */
    static byte[] tabela() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                escrever(f, 40, 700, "Descricao");
                escrever(f, 160, 700, "Valor");
                escrever(f, 300, 700, "Descricao");
                escrever(f, 430, 700, "Valor");

                escrever(f, 40, 685, "SALARIO");
                escrever(f, 160, 685, "6.452,92");
                escrever(f, 300, 685, "INSS MES");
                escrever(f, 430, 685, "823,61");

                escrever(f, 40, 670, "DIF SALARIO MENSAL");
                escrever(f, 160, 670, "847,87");
                escrever(f, 300, 670, "IRRF MES");
                escrever(f, 430, 670, "865,93");

                // Linha so com desconto — o caso que motivou o leitor.
                escrever(f, 300, 655, "TIT ASS ODT BRADESCO");
                escrever(f, 430, 655, "11,49");

                escrever(f, 300, 640, "VALE ALIMENTACAO");
                escrever(f, 430, 640, "64,89");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    static void escrever(PDPageContentStream f, float x, float y, String texto)
            throws IOException {
        f.beginText();
        f.newLineAtOffset(x, y);
        f.showText(texto);
        f.endText();
    }

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    private TestesDeTabela() {}
}
