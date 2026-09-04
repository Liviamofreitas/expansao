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
        espacoRigidoNoTextoReal();
        ligaduraTipografica();
        ancoraAtravessandoQuebraDeLinha();
        arquivoSemExtensao();

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
        ok("Cap. 8.2 . pagina abaixo do limiar padrao (50) e sinalizada",
                comPadrao.paginasSemRecuperacao().equals(List.of(1)));
        // Sem imagem, o OCR nao teria o que recuperar: sinalizar nao e mandar
        // para OCR. Sao coisas diferentes, e confundi-las gera falso positivo.
        ok("Cap. 8.2 . mas sem imagem nao ha OCR a fazer", !comPadrao.exigeOcr());

        TextoExtraido comLimiarBaixo = new ExtratorPdfBox(
                new ExtratorPdfBox.Limites(500, 10)).extrair(curto);
        ok("Cap. 8.2 . o limiar e parametro: com 10, a mesma pagina passa como suficiente",
                comLimiarBaixo.paginasSemRecuperacao().isEmpty());
    }

    /** Uma pagina digitalizada entre paginas nativas nao pode passar despercebida. */
    static void paginaDigitalizadaNoMeio() throws Exception {
        String corpo = "CERTIDAO NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS FEDERAIS "
                + "E A DIVIDA ATIVA DA UNIAO EMITIDA EM 01/04/2026";
        byte[] pdf = pdfComPaginas(corpo, "", corpo);

        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);
        // Sem imagem, e folha em branco: um comprovante bancario real de 8
        // paginas trouxe exatamente isso, e trata-lo como digitalizado mandaria
        // o documento inteiro para triagem sem necessidade (risco P01).
        ok("Cap. 8.2 . folha em branco entre nativas NAO dispara OCR",
                !t.exigeOcr() && t.paginasSemRecuperacao().equals(List.of(2)));
        ok("Cap. 8.2 . o documento continua marcado como nativo",
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

        // Pagina em branco (sem texto E sem imagem) nao exige OCR: nao ha
        // conteudo a recuperar. Ver paginaEmBrancoNoMeio.
        ok("Cap. 8.2 . pagina em branco e identificada como tal, nao como digitalizada",
                !t.exigeOcr() && t.paginasSemRecuperacao().equals(List.of(1)));
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
    // Achados do primeiro contato com documentos reais do OwnCloud
    // (certidoes RFB e GDF, DCTFWeb, comprovantes bancarios)
    // =========================================================================

    /**
     * A CND da Receita Federal traz 266 NO-BREAK SPACE no corpo. Onde parece
     * haver espaco, ha U+00A0 — e uma ancora escrita com espaco comum nao
     * casaria.
     */
    static void espacoRigidoNoTextoReal() throws Exception {
        String comNbsp = "CERTIDAO\u00A0NEGATIVA\u00A0DE\u00A0DEBITOS";
        TextoNormalizado n = TextoNormalizado.de(comNbsp);
        ok("Real . NO-BREAK SPACE vira espaco comum na normalizacao",
                n.texto().equals("certidao negativa de debitos"));

        byte[] pdf = pdfComLinhas(80, 750, 11, comNbsp,
                "Texto de apoio para a pagina atingir o minimo de caracteres exigido.");
        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);
        ok("Real . ancora com espaco comum casa em texto que usa espaco rigido",
                LocalizadorDeCampos.primeiro(t,
                        PadraoDeCampo.de("x", "certidao negativa de debitos"), 1.0) != null);
    }

    /**
     * Os comprovantes bancarios trazem a ligadura U+FB01 em "deficiencia".
     * NFKD a desfaz; NFD nao.
     */
    static void ligaduraTipografica() {
        TextoNormalizado n = TextoNormalizado.de("de\uFB01ciencia auditiva");
        ok("Real . ligadura tipografica e desfeita",
                n.texto().equals("deficiencia auditiva"));
        ok("Real . a ligadura, que era 1 caractere, aponta seus 2 caracteres a origem certa",
                n.origemDe(n.texto().indexOf("fi")) == n.origemDe(n.texto().indexOf("fi") + 1));
    }

    /**
     * O achado mais grave: o titulo da CND ocupa duas linhas, e "TRIBUTOS
     * FEDERAIS" sai como "TRIBUTOS\nFEDERAIS". Como os titulos SAO as ancoras
     * do cap. 8.5, e titulos longos sempre quebram, sem colapsar separadores
     * praticamente nenhuma ancora multi-palavra funcionaria.
     */
    static void ancoraAtravessandoQuebraDeLinha() throws Exception {
        byte[] pdf = pdfComLinhas(80, 750, 11,
                "CERTIDAO POSITIVA COM EFEITOS DE NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS",
                "FEDERAIS E A DIVIDA ATIVA DA UNIAO",
                "Nome: ENGESOFTWARE TECNOLOGIA S/A");
        TextoExtraido t = new ExtratorPdfBox().extrair(pdf);

        CampoExtraido achado = LocalizadorDeCampos.primeiro(t,
                PadraoDeCampo.de("ancora", "tributos federais"), 1.0);
        ok("Real . ancora multi-palavra casa atravessando a quebra de linha",
                achado != null);
        ok("Real . e a regiao devolve DOIS retangulos, um por linha",
                achado != null && achado.posicao().retangulos().size() == 2);
    }

    /**
     * Um comprovante real chegou do OwnCloud sem extensao nenhuma. Rejeita-lo
     * perderia documento legitimo; D-07 ja avisa que os arquivos reais nao
     * seguem a nomenclatura do checklist.
     */
    static void arquivoSemExtensao() throws Exception {
        byte[] pdf = pdfCom("comprovante de pagamento");
        ok("Real . extensao AUSENTE nao e divergencia: o conteudo decide",
                DetectorDeMime.conferir(pdf, "COMPROVANTE_PG_IRRF") == DetectorDeMime.Tipo.PDF);
        // Mas extensao PRESENTE e contraditoria continua sendo rejeitada.
        ok("Cap. 8.2 . extensao presente e divergente continua rejeitada",
                codigoDeErro(() -> DetectorDeMime.conferir(pdf, "planilha.xlsx"))
                        .equals("MIME_DIVERGENTE"));
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
