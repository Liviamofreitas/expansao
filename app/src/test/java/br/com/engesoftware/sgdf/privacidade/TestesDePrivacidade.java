package br.com.engesoftware.sgdf.privacidade;

import br.com.engesoftware.sgdf.book.DocumentoPublicavel;
import br.com.engesoftware.sgdf.book.MontadorDeBook;
import br.com.engesoftware.sgdf.book.Sigilo;
import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Testes do mascaramento (F2-06) e do tarjamento (F2-07).
 *
 * <p>O teste que importa e um so: <b>extrair o texto do PDF tarjado e verificar
 * que o CPF nao esta mais la</b>. Desenhar um retangulo preto passaria em
 * qualquer teste visual e falharia neste.
 */
public final class TestesDePrivacidade {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    /** CPF real, com DV valido — o do primeiro contracheque da massa. */
    static final String CPF = "052.190.471-40";

    public static void main(String[] args) {
        executar("mascaraDeCpf", TestesDePrivacidade::mascaraDeCpf);
        executar("mascaraSoPegaOQueTemDvValido",
                TestesDePrivacidade::mascaraSoPegaOQueTemDvValido);
        executar("mascaraDeNomeEDeCnpj", TestesDePrivacidade::mascaraDeNomeEDeCnpj);
        executar("cpfDeixaDeSerExtraivel", TestesDePrivacidade::cpfDeixaDeSerExtraivel);
        executar("oRestoDoDocumentoSobrevive", TestesDePrivacidade::oRestoDoDocumentoSobrevive);
        executar("codigoDeBarrasNaoEApagado", TestesDePrivacidade::codigoDeBarrasNaoEApagado);
        executar("tarjaIncompletaERelatada", TestesDePrivacidade::tarjaIncompletaERelatada);
        executar("preparadorSoTarjaOQueExige", TestesDePrivacidade::preparadorSoTarjaOQueExige);
        executar("bookDoClienteAceitaOTarjado",
                TestesDePrivacidade::bookDoClienteAceitaOTarjado);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    static void mascaraDeCpf() {
        ok("F2-06 . o CPF sai mascarado nos extremos",
                "***.190.471-**".equals(Mascara.cpf(CPF)));
        ok("F2-06 . e sem pontuação também",
                "***.190.471-**".equals(Mascara.cpf("05219047140")));
        ok("F2-06 . o que não é CPF passa intacto",
                "abc".equals(Mascara.cpf("abc")));

        String mensagem = "Pendência do colaborador " + CPF + " na competência 06/2026";
        ok("F2-06 . a máscara alcança o CPF dentro de uma mensagem",
                Mascara.texto(mensagem).contains("***.190.471-**")
                        && !Mascara.texto(mensagem).contains(CPF));
    }

    /**
     * Secao 6 dos achados: numero de protocolo com onze digitos casa com o
     * padrao de CPF. Mascara-lo esconderia informacao que a mensagem precisa.
     */
    static void mascaraSoPegaOQueTemDvValido() {
        String comProtocolo = "Protocolo 12345678901 do documento";
        ok("F2-06 . sequência com DV inválido não é mascarada",
                Mascara.texto(comProtocolo).contains("12345678901"));

        String comCpf = "CPF 05219047140 do titular";
        ok("F2-06 . e a que tem DV válido é",
                !Mascara.texto(comCpf).contains("05219047140"));
    }

    static void mascaraDeNomeEDeCnpj() {
        ok("F2-06 . o nome vira primeiro nome e inicial",
                "HERMES O.".equals(Mascara.nome("HERMES LIMA DE OLIVEIRA")));
        ok("F2-06 . nome de uma palavra só fica como está",
                "HERMES".equals(Mascara.nome("HERMES")));
        ok("F2-06 . o CNPJ mascarado tem a forma que os bancos usam",
                "**.681.946/0001-**".equals(Mascara.cnpj("00.681.946/0001-60")));
    }

    /** O unico teste que prova alguma coisa. */
    static void cpfDeixaDeSerExtraivel() throws IOException {
        byte[] original = documentoCom(CPF);
        ok("F2-07 . o CPF está no documento original",
                new ExtratorPdfBox().extrair(original).textoCompleto().contains(CPF));

        RedatorDePdf.Resultado r = new RedatorDePdf().tarjar(original);
        String depois = new ExtratorPdfBox().extrair(r.pdf()).textoCompleto();

        ok("F2-07 . e não está mais no tarjado", !depois.contains(CPF));
        ok("F2-07 . nem sem pontuação", !depois.contains("05219047140"));
        ok("F2-07 . o redator conta o que tarjou", r.tarjados() == 1);
        ok("F2-07 . e confere o próprio trabalho", r.completa());

        // A mascara parcial: os seis do meio ficam, para quem confere
        // distinguir dois colaboradores sem consultar o original.
        ok("F2-07 . os seis dígitos do meio permanecem legíveis",
                depois.contains("190") && depois.contains("471"));
    }

    static void oRestoDoDocumentoSobrevive() throws IOException {
        byte[] tarjado = new RedatorDePdf().tarjar(documentoCom(CPF)).pdf();
        String texto = new ExtratorPdfBox().extrair(tarjado).textoCompleto();

        ok("F2-07 . o nome do colaborador continua no documento",
                texto.contains("ADRIANO LIN SOARES PERRUOLO"));
        ok("F2-07 . e os valores da folha", texto.contains("6.452,92"));
        ok("F2-07 . e a competência", texto.contains("JUNHO/2026"));
    }

    /**
     * Um codigo de barras com onze digitos casa com o padrao e nao e CPF.
     * Apaga-lo destruiria informacao que a conciliacao precisa.
     */
    static void codigoDeBarrasNaoEApagado() throws IOException {
        byte[] original = documentoCom("12345678901");
        RedatorDePdf.Resultado r = new RedatorDePdf().tarjar(original);
        String depois = new ExtratorPdfBox().extrair(r.pdf()).textoCompleto();

        ok("F2-07 . sequência com DV inválido não é tarjada",
                depois.contains("12345678901") && r.tarjados() == 0);
    }

    /**
     * O redator trabalha sobre o fluxo de conteudo e quem copia recebe a ordem
     * de leitura. As duas nao coincidem, e a diferenca fica relatada em vez de
     * virar uma aprovacao silenciosa.
     */
    static void tarjaIncompletaERelatada() throws IOException {
        // Dois grupos que, colados pelo extrator, formam um CPF de DV valido
        // que nao existe contiguo no fluxo de conteudo.
        byte[] original = duasColunas("052.190.", "471-40");
        RedatorDePdf.Resultado r = new RedatorDePdf().tarjar(original);

        String depois = new ExtratorPdfBox().extrair(r.pdf()).textoCompleto();
        boolean saiInteiro = depois.replaceAll("\\s+", "").contains("052.190.471-40");

        // Uma das duas coisas tem de ser verdade, e as duas sao aceitaveis; o
        // que nao pode e sair inteiro SEM ficar relatado.
        ok("F2-07 . ou o CPF não sai inteiro, ou a pendência é relatada",
                !saiInteiro || !r.completa());
    }

    static void preparadorSoTarjaOQueExige() throws IOException {
        List<DocumentoPublicavel> documentos = List.of(
                new DocumentoPublicavel("CER.CND_RFB", "Certidões e regularidade",
                        Sigilo.PUBLICO_CLIENTE, documentoCom(CPF), "owncloud://a",
                        OffsetDateTime.now(), false),
                new DocumentoPublicavel("FOL.CONTRACHEQUE", "Folha", Sigilo.PESSOAL,
                        documentoCom(CPF), "owncloud://b", OffsetDateTime.now(), false));

        var r = new PreparadorDoBookDoCliente().preparar(documentos);

        DocumentoPublicavel publico = r.documentos().get(0);
        DocumentoPublicavel pessoal = r.documentos().get(1);

        ok("F2-07 . o tipo público não é tarjado nem marcado", !publico.tarjado());
        ok("F2-07 . e o pessoal é", pessoal.tarjado());
        ok("F2-07 . só o pessoal perdeu o CPF",
                new ExtratorPdfBox().extrair(publico.conteudo()).textoCompleto().contains(CPF)
                        && !new ExtratorPdfBox().extrair(pessoal.conteudo()).textoCompleto()
                                .contains(CPF));
        ok("F2-07 . o preparador conta os CPFs removidos", r.tarjados() == 1);
        ok("F2-07 . e não há pendência neste caso", r.semPendencia());
    }

    /**
     * O montador recusava o book do cliente com peca pessoal nao tarjada.
     * Depois do preparador, ele aceita — que e o destravamento da F1-08.
     */
    static void bookDoClienteAceitaOTarjado() throws IOException {
        List<DocumentoPublicavel> documentos = List.of(
                new DocumentoPublicavel("FGT.RELATORIO_DIGITAL", "FGTS", Sigilo.PESSOAL,
                        documentoCom(CPF), "owncloud://relatorio", OffsetDateTime.now(), false));

        boolean recusouAntes = false;
        try {
            new MontadorDeBook().numerar(documentos, "2026-06", true);
        } catch (MontadorDeBook.TarjamentoPendente e) {
            recusouAntes = true;
        }
        ok("F2-07 . sem o preparador, o book do cliente continua sendo recusado",
                recusouAntes);

        var preparado = new PreparadorDoBookDoCliente().preparar(documentos);
        var pecas = new MontadorDeBook().numerar(preparado.documentos(), "2026-06", true);
        ok("F2-07 . com o preparador, o book do cliente é montado", pecas.size() == 1);
        ok("F2-07 . e a peça vai marcada como tarjada no manifesto",
                pecas.get(0).tarjado());
    }

    // -------------------------------------------------------------------------

    /** Um recibo com o CPF dado, no formato do contracheque real. */
    static byte[] documentoCom(String cpf) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                escrever(f, 38, 736, "Recibo de Pagamento   JUNHO/2026 MENSAL 1/1");
                escrever(f, 38, 690, "Matricula Nome");
                escrever(f, 38, 681, "000101862 ADRIANO LIN SOARES PERRUOLO");
                escrever(f, 38, 665, "CPF Cargo/Nivel");
                escrever(f, 38, 656, cpf + " TESTADOR - PLENO /");
                escrever(f, 38, 597, "SALARIO 30,00 6.452,92 INSS MES 823,61");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    /** Dois trechos distantes, que o extrator junta e o fluxo mantem separados. */
    static byte[] duasColunas(String esquerda, String direita) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                escrever(f, 40, 700, esquerda);
                escrever(f, 300, 700, direita);
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

    interface Teste {
        void executar() throws Exception;
    }

    static void executar(String nome, Teste teste) {
        try {
            teste.executar();
        } catch (Exception | AssertionError e) {
            falhas.add(nome + " lancou " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    private TestesDePrivacidade() {}

    static {
        // Silencia o aviso de fonte do PDFBox na primeira execucao.
        java.util.logging.Logger.getLogger("org.apache.pdfbox")
                .setLevel(java.util.logging.Level.SEVERE);
    }
}
