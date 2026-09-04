package br.com.engesoftware.sgdf.book;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Gera o {@code _indice.pdf} — a folha de rosto entregue ao cliente.
 *
 * <p>Cap. 10: <i>"traz contrato, competência, versão, relação de peças com
 * páginas e o hash do conjunto"</i>.
 *
 * <p>O hash do conjunto vai no índice, e não só no manifesto, por uma razão
 * prática: o manifesto é um arquivo que alguém precisa saber abrir; o índice é
 * o que a pessoa imprime. Quem confere o book fisicamente tem de conseguir
 * comparar o hash sem depender de ferramenta.
 */
public final class GeradorDeIndice {

    public static final String NOME = "_indice.pdf";

    private static final float MARGEM = 42f;
    private static final float ENTRELINHA = 14f;

    public byte[] gerar(Book book) {
        try (PDDocument doc = new PDDocument()) {
            List<String> linhas = linhas(book);
            int porPagina = (int) ((PDRectangle.A4.getHeight() - 2 * MARGEM) / ENTRELINHA) - 1;

            for (int i = 0; i < linhas.size(); i += porPagina) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                doc.addPage(pagina);
                try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                    float y = PDRectangle.A4.getHeight() - MARGEM;
                    for (int j = i; j < Math.min(i + porPagina, linhas.size()); j++) {
                        String linha = linhas.get(j);
                        boolean titulo = j < 2 || linha.startsWith("Seq");
                        f.setFont(new PDType1Font(titulo
                                ? Standard14Fonts.FontName.HELVETICA_BOLD
                                : Standard14Fonts.FontName.HELVETICA), titulo ? 11 : 9);
                        f.beginText();
                        f.newLineAtOffset(MARGEM, y);
                        f.showText(linha);
                        f.endText();
                        y -= ENTRELINHA;
                    }
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gerar o índice do book", e);
        }
    }

    private static List<String> linhas(Book book) {
        List<String> linhas = new ArrayList<>();
        linhas.add("BOOK DE FATURAMENTO — " + book.contrato());
        linhas.add("Competencia " + book.competencia() + "   versao " + book.versao());
        linhas.add("");
        linhas.add("Publicado em " + book.publicadoEm()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                + " por " + book.publicadoPor());
        linhas.add("Versao da matriz: " + book.versaoMatriz());
        linhas.add("");
        linhas.add("Hash do conjunto (SHA-256):");
        // Quebrado em dois: 64 caracteres numa linha só ficam ilegíveis no papel,
        // e este hash existe para ser conferido a olho.
        linhas.add("  " + book.hashDoConjunto().substring(0, 32));
        linhas.add("  " + book.hashDoConjunto().substring(32));
        linhas.add("");
        linhas.add(String.format("Seq  %-32s %-10s %s", "TIPO", "TARJADO", "HASH (12 primeiros)"));
        for (Peca p : book.pecas()) {
            linhas.add(String.format("%03d  %-32s %-10s %s",
                    p.sequencia(), p.tipo(), p.tarjado() ? "sim" : "nao",
                    p.hashSha256().substring(0, 12)));
        }
        linhas.add("");
        linhas.add(book.pecas().size() + " peca(s). Cada arquivo esta nomeado com a sua "
                + "sequencia neste indice.");
        return linhas;
    }
}
