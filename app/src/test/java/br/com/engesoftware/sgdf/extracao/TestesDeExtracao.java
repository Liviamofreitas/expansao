package br.com.engesoftware.sgdf.extracao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/**
 * Testes da historia F1-02 — extracao de texto PDF com posicoes.
 *
 * <p>Criterio de aceite: "Campos extraidos exibem a regiao de origem no
 * documento".
 *
 * <p>Os PDFs sao construidos aqui, com posicoes conhecidas, para que as
 * assercoes possam conferir a regiao contra a coordenada em que o texto foi
 * de fato desenhado. Testar contra um PDF opaco so verificaria que "alguma"
 * regiao voltou.
 */
public final class TestesDeExtracao {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        deteccaoDeMime();
        normalizacaoPreservaOrigem();
        extracaoBasica();
        limiarDeTexto();
        paginaDigitalizadaNoMeio();
        regiaoDeCampo();
        multiplasPaginas();
        valorEmDuasLinhas();
        pdfCifrado();
        pdfSemCamadaDeTexto();
        pdfCorrompido();
        serializacaoDaPosicao();

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    // =========================================================================
    // Cap. 8.2 — mime real por assinatura binaria
    // =========================================================================
    static void deteccaoDeMime() throws Exception {
        byte[] pdf = pdfCom("teste");
        ok("Cap. 8.2 . PDF e reconhecido pela assinatura",
                DetectorDeMime.detectar(pdf) == DetectorDeMime.Tipo.PDF);
        ok("Cap. 8.2 . PDF com extensao .pdf passa na conferencia",
                DetectorDeMime.conferir(pdf, "nota.pdf") == DetectorDeMime.Tipo.PDF);

        // Um executavel renomeado para .pdf passaria por qualquer verificacao
        // baseada em nome.
        byte[] disfarcado = new byte[] {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03};
        ok("Cap. 8.2 . conteudo irreconhecivel e rejeitado",
                codigoDeErro(() -> DetectorDeMime.conferir(disfarcado, "nota.pdf"))
                        .equals("MIME_DESCONHECIDO"));

        ok("Cap. 8.2 . divergencia extensao x conteudo e rejeitada com motivo",
                codigoDeErro(() -> DetectorDeMime.conferir(pdf, "planilha.xlsx"))
                        .equals("MIME_DIVERGENTE"));

        byte[] csv = "matricula;nome;valor\n0001;Fulano;1234,56\n"
                .getBytes(StandardCharsets.UTF_8);
        ok("Cap. 8.2 . CSV e aceito como texto",
                DetectorDeMime.conferir(csv, "folha.csv") == DetectorDeMime.Tipo.TEXTO);
    }

    // =========================================================================
    // Normalizacao que nao perde a origem
    // =========================================================================
    static void normalizacaoPreservaOrigem() {
        TextoNormalizado n = TextoNormalizado.de("Certidão NEGATIVA");
        ok("Cap. 8.3 . normalizacao remove acento e caixa",
                n.texto().equals("certidao negativa"));

        // "Certidão" tem o 'ã' no indice 6; apos normalizar, 'a' continua no 6.
        int pos = n.texto().indexOf("negativa");
        ok("F1-02 . o deslocamento normalizado mapeia de volta ao original",
                n.origemDe(pos) == "Certidão NEGATIVA".indexOf("NEGATIVA"));

        // O caso que quebra a implementacao ingenua: normalizar antes de casar.
        TextoNormalizado c = TextoNormalizado.de("ÇÃO ção");
        ok("F1-02 . cada caractere normalizado aponta para o seu original",
                c.origemDe(c.texto().indexOf("cao ")) == 0
                        && c.origemDe(c.texto().lastIndexOf("cao")) == 4);
    }

    // =========================================================================
    // Extracao
    // =========================================================================
    static void extracaoBasica() throws Exception {
        // Texto de tamanho realista: uma pagina de certidao tem centenas de
        // caracteres. Um PDF com uma linha so ficaria abaixo do limiar que
        // separa pagina nativa de digitalizada, e o teste passaria a medir o
        // limiar em vez da extracao.
        byte[] pdf = pdfComLinhas(80, 750, 11,
                "CERTIDAO NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS FEDERAIS",
                "E A DIVIDA ATIVA DA UNIAO",
                "Nome: ENGESOFTWARE TECNOLOGIA S.A.",
                "CNPJ 07.237.373/0001-20",
                "Ressalvado o direito de a Fazenda Nacional cobrar e inscrever",
                "quaisquer dividas de responsabilidade do sujeito passivo acima",
                "identificado que vierem a ser apuradas.",
                "Emitida em 01/04/2026 - valida ate 14/10/2026",
                "Codigo de controle da certidao: A1B2.C3D4.E5F6.7890");
        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);

        ok("F1-02 . o texto e extraido",
                t.textoCompleto().contains("07.237.373/0001-20"));
        ok("F1-02 . a origem e PDF nativo", t.origem() == TextoExtraido.Origem.PDF_NATIVO);
        ok("F1-02 . texto e glifos ficam alinhados",
                t.paginas().get(0).texto().length() == t.paginas().get(0).glifos().size());
        ok("F1-02 . PDF com camada de texto nao exige OCR", !t.exigeOcr());
    }

    /**
     * O limiar que separa pagina nativa de digitalizada e parametro, e a fronteira
     * merece teste proprio: e ele que decide se um documento vai direto para o
     * reconhecimento ou desvia para o OCR e a triagem.
     */
    static void limiarDeTexto() throws Exception {
        byte[] curto = pdfCom("CNPJ 07.237.373/0001-20");   // 22 caracteres

        TextoExtraido comPadrao = new ExtratorPdfBox().extrair(curto);
        ok("Cap. 8.2 . pagina abaixo do limiar padrao (50) e mandada para OCR",
                comPadrao.exigeOcr());

        TextoExtraido comLimiarBaixo = new ExtratorPdfBox(
                new ExtratorPdfBox.Limites(500, 10)).extrair(curto);
        ok("Cap. 8.2 . o limiar e parametro: com 10, a mesma pagina passa como nativa",
                !comLimiarBaixo.exigeOcr());
    }

    /** Uma pagina digitalizada entre paginas nativas nao pode passar despercebida. */
    static void paginaDigitalizadaNoMeio() throws Exception {
        String corpo = "CERTIDAO NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS FEDERAIS "
                + "E A DIVIDA ATIVA DA UNIAO EMITIDA EM 01/04/2026";
        byte[] pdf = pdfComPaginas(corpo, "", corpo);

        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);
        ok("Cap. 8.2 . uma pagina sem texto entre nativas e detectada",
                t.exigeOcr() && t.paginasSemTexto().equals(List.of(2)));
        ok("Cap. 8.2 . o documento continua marcado como nativo, nao como OCR",
                t.origem() == TextoExtraido.Origem.PDF_NATIVO);
    }

    // =========================================================================
    // CRITERIO DE ACEITE: a regiao de origem
    // =========================================================================
    static void regiaoDeCampo() throws Exception {
        // Texto desenhado em (100, 700), fonte 12pt.
        byte[] pdf = pdfCom("Emitente CNPJ 07.237.373/0001-20 valida ate 14/10/2026",
                100, 700, 12);
        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);

        CampoExtraido cnpj = LocalizadorDeCampos.primeiro(t,
                PadraoDeCampo.de("cnpj", "\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}"), 1.0);

        ok("F1-02 . o campo e localizado", cnpj != null);
        ok("F1-02 . o valor extraido esta correto",
                cnpj != null && cnpj.valor().equals("07.237.373/0001-20"));
        ok("F1-02 . a regiao aponta para a pagina certa",
                cnpj != null && cnpj.posicao().pagina() == 1);

        Retangulo r = cnpj.posicao().envolvente();
        // O CNPJ vem depois de "Emitente CNPJ ", entao comeca a direita de x=100
        // e na mesma altura (y=700), com tolerancia para a metrica da fonte.
        ok("F1-02 . a regiao esta na coordenada em que o texto foi desenhado",
                r.x() > 100 && r.x() < 300 && Math.abs(r.y() - 700) < 15);
        ok("F1-02 . a largura da regiao corresponde ao tamanho do valor",
                r.largura() > 60 && r.largura() < 160);

        // A data esta mais a direita que o CNPJ: se as posicoes fossem
        // aproximadas, esta ordem nao se sustentaria.
        CampoExtraido validade = LocalizadorDeCampos.primeiro(t,
                PadraoDeCampo.de("validade", "\\d{2}/\\d{2}/\\d{4}"), 1.0);
        ok("F1-02 . campos distintos tem regioes distintas e na ordem do texto",
                validade != null && validade.posicao().envolvente().x() > r.x() + r.largura());
    }

    static void multiplasPaginas() throws Exception {
        byte[] pdf = pdfComPaginas(
                "Pagina um sem o valor",
                "Competencia 04/2026 nesta pagina");
        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);

        ok("F1-02 . as duas paginas sao extraidas", t.totalDePaginas() == 2);

        CampoExtraido comp = LocalizadorDeCampos.primeiro(t,
                PadraoDeCampo.de("competencia", "\\d{2}/\\d{4}"), 1.0);
        ok("F1-02 . a regiao aponta para a pagina 2, nao para a 1",
                comp != null && comp.posicao().pagina() == 2);
    }

    static void valorEmDuasLinhas() throws Exception {
        // Duas linhas com o mesmo padrao de data em cada uma.
        byte[] pdf = pdfComLinhas(100, 700, 12, "Emissao 01/04/2026", "Validade 14/10/2026");
        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);

        List<CampoExtraido> datas = LocalizadorDeCampos.localizar(t,
                PadraoDeCampo.de("data", "\\d{2}/\\d{2}/\\d{4}"), 1.0);

        ok("F1-02 . todas as ocorrencias sao devolvidas, nao so a primeira",
                datas.size() == 2);
        ok("F1-02 . cada ocorrencia tem regiao propria, em alturas diferentes",
                datas.size() == 2 && Math.abs(datas.get(0).posicao().envolvente().y()
                        - datas.get(1).posicao().envolvente().y()) > 5);
        ok("F1-02 . um valor numa unica linha produz um unico retangulo",
                datas.get(0).posicao().retangulos().size() == 1);
    }

    // =========================================================================
    // Documentos que nao extraem
    // =========================================================================
    static void pdfCifrado() throws Exception {
        byte[] pdf = pdfCifradoCom("conteudo protegido");
        ok("V1 . PDF cifrado e rejeitado com motivo",
                codigoDeErro(() -> new ExtratorPdfBox().extrair(pdf)).equals("PDF_CIFRADO"));
    }

    static void pdfSemCamadaDeTexto() throws Exception {
        byte[] pdf = pdfVazio();   // pagina em branco: sem camada de texto
        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);

        ok("Cap. 8.2 . pagina sem texto e sinalizada para OCR", t.exigeOcr());
        ok("Cap. 8.2 . a pagina sem texto e identificada pelo numero",
                t.paginasSemTexto().equals(List.of(1)));
        ok("Cap. 8.2 . documento inteiro sem texto tem origem OCR",
                t.origem() == TextoExtraido.Origem.OCR);
    }

    static void pdfCorrompido() {
        byte[] lixo = "%PDF-1.4 isto nao e um PDF valido".getBytes(StandardCharsets.UTF_8);
        ok("V1 . PDF corrompido e rejeitado com motivo",
                codigoDeErro(() -> new ExtratorPdfBox().extrair(lixo)).equals("PDF_CORROMPIDO"));
    }

    static void serializacaoDaPosicao() {
        RegiaoNoDocumento r = new RegiaoNoDocumento(3,
                List.of(new Retangulo(10, 20, 30, 40)));
        String json = r.paraJson();
        ok("F1-02 . a posicao serializa para campo_extraido.posicao",
                json.contains("\"pagina\":3") && json.contains("\"x\":10.0")
                        && json.contains("\"largura\":30.0"));

        ok("F1-02 . regiao sem retangulo e recusada, pois nao seria auditavel",
                codigoDeErro(() -> new RegiaoNoDocumento(1, List.of()))
                        .equals("IllegalArgumentException"));
    }

    // =========================================================================
    // Apoio
    // =========================================================================

    static byte[] pdfCom(String texto) throws IOException {
        return pdfCom(texto, 100, 700, 12);
    }

    static byte[] pdfCom(String texto, float x, float y, float tamanho) throws IOException {
        return pdfComLinhas(x, y, tamanho, texto);
    }

    static byte[] pdfComLinhas(float x, float y, float tamanho, String... linhas)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream fluxo = new PDPageContentStream(doc, pagina)) {
                fluxo.beginText();
                fluxo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), tamanho);
                fluxo.setLeading(tamanho * 1.5f);
                fluxo.newLineAtOffset(x, y);
                for (String linha : linhas) {
                    fluxo.showText(linha);
                    fluxo.newLine();
                }
                fluxo.endText();
            }
            return bytes(doc);
        }
    }

    static byte[] pdfComPaginas(String... textos) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String texto : textos) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                doc.addPage(pagina);
                if (texto.isEmpty()) {
                    continue;   // pagina em branco: simula folha digitalizada
                }
                try (PDPageContentStream fluxo = new PDPageContentStream(doc, pagina)) {
                    fluxo.beginText();
                    fluxo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    fluxo.newLineAtOffset(100, 700);
                    fluxo.showText(texto);
                    fluxo.endText();
                }
            }
            return bytes(doc);
        }
    }

    static byte[] pdfVazio() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            return bytes(doc);
        }
    }

    static byte[] pdfCifradoCom(String texto) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream fluxo = new PDPageContentStream(doc, pagina)) {
                fluxo.beginText();
                fluxo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                fluxo.newLineAtOffset(100, 700);
                fluxo.showText(texto);
                fluxo.endText();
            }
            AccessPermission permissoes = new AccessPermission();
            permissoes.setCanExtractContent(false);
            doc.protect(new StandardProtectionPolicy("dono", "usuario", permissoes));
            return bytes(doc);
        }
    }

    static byte[] bytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        doc.save(saida);
        return saida.toByteArray();
    }

    static String codigoDeErro(Runnable acao) {
        try {
            acao.run();
            return "(sem erro)";
        } catch (ExtracaoInvalida e) {
            return e.codigo;
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
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

    private TestesDeExtracao() {}
}
