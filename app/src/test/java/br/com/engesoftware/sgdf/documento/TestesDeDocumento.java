package br.com.engesoftware.sgdf.documento;

import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Testes dos leitores de documento.
 *
 * <p>As coordenadas dos fixtures sao as medidas nos documentos reais da massa,
 * porque o que quebra estes leitores e geometria, nao logica.
 */
public final class TestesDeDocumento {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        loteCompleto();
        loteComNomeEmTresLinhas();
        loteSemRodapeNaoConfere();
        loteComRodapeQueNaoBate();
        matriculaSaiDoNumeroDoCliente();

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    static void loteCompleto() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(3, "15.319,82"));

        ok("Lote . os tres pagamentos sao lidos", c.pagamentos().size() == 3);
        ok("Lote . a soma reproduz o valor declarado no rodape",
                c.somaLida().compareTo(new BigDecimal("15319.82")) == 0);
        ok("Lote . e a leitura confere com o rodape", c.confere());

        PagamentoDoLote p = c.pagamentos().get(1);
        ok("Lote . o numero do pagamento sai da coluna certa",
                p.numeroPagamento().equals("900059649"));
        ok("Lote . o nome de uma linha so fica inteiro",
                p.favorecido().equals("RONI DA SILVA ROSA"));
        ok("Lote . o valor nao leva o tipo de pagamento junto",
                p.valor().compareTo(new BigDecimal("5935.22")) == 0);
    }

    /**
     * O caso que motivou o achado A18: o nome ocupa tres linhas visuais, uma
     * acima e uma abaixo da linha dos numeros.
     */
    static void loteComNomeEmTresLinhas() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(3, "15.319,82"));

        ok("Lote . nome acima e abaixo da linha dos numeros e reunido",
                c.pagamentos().get(0).favorecido().equals("DOUGLAS VINISIOS NUNES SOUZA"));
        ok("Lote . e o registro do vizinho nao entra no nome",
                c.pagamentos().get(2).favorecido().equals("ADILON SANTIAGO DA SILVA"));
        ok("Lote . o numero do cliente do registro de tres linhas esta certo",
                c.pagamentos().get(0).numeroCliente().equals("00000010225306072026"));
    }

    /**
     * Achado A12: o comprovante do Itau traz so os rotulos, sem dado nenhum.
     * Ler zero pagamentos e dizer que confere seria o pior resultado possivel —
     * uma falha silenciosa num documento que ja passou por antivirus, MIME e
     * legibilidade.
     */
    static void loteSemRodapeNaoConfere() throws Exception {
        ComprovanteEmLote c = lerLote(null);

        // A prova de que a delimitacao por ancora funciona: sem o literal do
        // rodape, o texto de atendimento nao entra nos dados, e os tres
        // registros continuam sendo lidos.
        ok("Lote . sem rodape o texto de atendimento nao entra nos dados",
                c.pagamentos().size() == 3);
        ok("Lote . e a projecao das colunas nao e destruida por ele",
                c.problemasDeLeitura().isEmpty());
        ok("Lote . documento sem rodape nao e dado por conferido", !c.confere());
        ok("Lote . e a recusa diz que a leitura nao pode ser verificada",
                c.divergenciasComORodape().get(0).contains("não pôde ser verificada"));
    }

    static void loteComRodapeQueNaoBate() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(4, "15.319,82"));

        ok("Lote . rodape com contagem diferente da lida acusa divergencia",
                !c.confere() && c.divergenciasComORodape().size() == 1);
        ok("Lote . e a divergencia nomeia os dois numeros",
                c.divergenciasComORodape().get(0).contains("4")
                        && c.divergenciasComORodape().get(0).contains("3"));
    }

    /** Achado A14: a matricula esta embutida no numero do cliente. */
    static void matriculaSaiDoNumeroDoCliente() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(3, "15.319,82"));
        ok("Lote . a matricula sai do numero do cliente sem a data e sem zeros",
                c.pagamentos().get(0).matricula().equals("102253"));
        ok("Lote . numero do cliente curto demais nao inventa matricula",
                new PagamentoDoLote("9", "06072026", "X", "06/07/2026", "CC",
                        BigDecimal.ONE).matricula() == null);
    }

    // -------------------------------------------------------------------------

    static ComprovanteEmLote lerLote(String rodape) throws Exception {
        TextoExtraido t = new ExtratorPdfBox().extrair(comprovanteEmLote(rodape));
        return new LeitorDeComprovanteEmLote().ler(t);
    }

    static String rodape(int compromissos, String valor) {
        return "Total Compromissos: " + compromissos + " Valor Total: R$ " + valor;
    }

    /**
     * Geometria medida em COMPROVANTE_PG_FOLHA_2.pdf: numeros na linha
     * principal em x 59..552, nome em x 245, 31 pontos entre registros e 6,5
     * dentro do registro.
     */
    static byte[] comprovanteEmLote(String rodape) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);

                // Texto corrido acima do cabecalho: e o que apaga as lacunas
                // quando a projecao e calculada sobre a pagina inteira.
                escrever(f, 37.2f, 600.3f,
                        "Clique sobre o titulo da respectiva coluna que deseja ordenar");
                escrever(f, 38.3f, 567.7f, "Número do Pagamento Número do Cliente Funcionário "
                        + "Data de Pagamento Tipo de Pagamento Valor R$");

                // Registro 1: nome em tres linhas.
                escrever(f, 245.5f, 541.6f, "DOUGLAS VINISIOS");
                linhaPrincipal(f, 535.1f, "900059648", "00000010225306072026", "2.232,21");
                escrever(f, 245.5f, 528.6f, "NUNES SOUZA");

                // Registro 2: tudo numa linha.
                escrever(f, 245.5f, 497.6f, "RONI DA SILVA ROSA");
                linhaPrincipal(f, 497.6f, "900059649", "00000010196806072026", "5.935,22");

                // Registro 3: nome em tres linhas outra vez.
                escrever(f, 245.5f, 466.6f, "ADILON SANTIAGO DA");
                linhaPrincipal(f, 460.1f, "900059650", "00000010206206072026", "7.152,39");
                escrever(f, 245.5f, 453.6f, "SILVA");

                if (rodape != null) {
                    escrever(f, 37.2f, 87.7f, rodape);
                }
                escrever(f, 37.2f, 60.0f, "Central de Atendimento Empresarial SAC");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    static void linhaPrincipal(PDPageContentStream f, float y, String pagamento,
                               String cliente, String valor) throws IOException {
        escrever(f, 59.4f, y, pagamento);
        escrever(f, 133.0f, y, cliente);
        escrever(f, 369.0f, y, "06/07/2026");
        escrever(f, 462.0f, y, "CC");
        escrever(f, 518.0f, y, valor);
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

    private TestesDeDocumento() {}
}
