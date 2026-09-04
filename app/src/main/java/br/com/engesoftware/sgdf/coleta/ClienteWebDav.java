package br.com.engesoftware.sgdf.coleta;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Cliente WebDAV somente-leitura — integração INT-01, cap. 14.1.
 *
 * <p>Usa apenas o JDK: {@link HttpClient} aceita métodos arbitrários, e
 * PROPFIND é HTTP comum com corpo XML. Numa aplicação Spring isto vira um
 * componente com as credenciais vindas do cofre; a lógica não muda.
 *
 * <p><b>Somente leitura, por construção.</b> Não existe método de escrita nesta
 * classe. O cap. 14.1 e a decisão R-02 exigem que a conta {@code svc-sgdf-leitura}
 * não escreva na origem; ter o método e confiar em não chamá-lo seria depender
 * de disciplina em vez de estrutura.
 */
public final class ClienteWebDav {

    private static final String CORPO_PROPFIND = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:getetag/>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
            """;

    private final HttpClient http;
    private final URI base;
    private final String autorizacao;
    private final Duration timeout;

    public ClienteWebDav(URI base, String usuario, String senha, Duration timeout) {
        this.base = base;
        this.timeout = timeout;
        this.autorizacao = usuario == null ? null : "Basic " + Base64.getEncoder()
                .encodeToString((usuario + ":" + senha).getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                // Redirecionamento seguido às cegas permitiria ao servidor
                // apontar a coleta para outro host.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * PROPFIND numa coleção.
     *
     * @param caminho     caminho absoluto no servidor
     * @param profundidade 1 lista os filhos diretos; a recursão é feita pela
     *                     {@link Varredura}, com limite explícito de níveis —
     *                     Depth: infinity é recusado por muitos servidores e
     *                     não dá controle sobre o volume da resposta
     */
    public List<EntradaRemota> listar(String caminho, int profundidade) throws IOException {
        HttpRequest requisicao = requisicao(caminho)
                .header("Depth", String.valueOf(profundidade))
                .header("Content-Type", "application/xml; charset=utf-8")
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(CORPO_PROPFIND))
                .build();

        HttpResponse<byte[]> resposta = enviar(requisicao);
        if (resposta.statusCode() != 207) {
            throw new IOException("PROPFIND " + caminho + " devolveu " + resposta.statusCode());
        }
        return interpretarMultistatus(resposta.body(), caminho);
    }

    /**
     * GET de um arquivo, com teto de bytes.
     *
     * <p>O teto é aplicado no fluxo, não depois: confiar no
     * {@code getcontentlength} do PROPFIND deixaria um servidor hostil declarar
     * 1 KB e entregar gigabytes (R-03).
     */
    public byte[] baixar(String caminho, long limiteBytes) throws IOException {
        HttpResponse<byte[]> resposta = enviar(requisicao(caminho).GET().build());
        if (resposta.statusCode() != 200) {
            throw new IOException("GET " + caminho + " devolveu " + resposta.statusCode());
        }
        byte[] corpo = resposta.body();
        if (corpo.length > limiteBytes) {
            throw new IOException("conteúdo de " + caminho + " excede o limite: "
                    + corpo.length + " > " + limiteBytes + " bytes");
        }
        return corpo;
    }

    private HttpRequest.Builder requisicao(String caminho) {
        HttpRequest.Builder b = HttpRequest.newBuilder(base.resolve(caminho)).timeout(timeout);
        if (autorizacao != null) {
            b.header("Authorization", autorizacao);
        }
        return b;
    }

    private HttpResponse<byte[]> enviar(HttpRequest requisicao) throws IOException {
        try {
            return http.send(requisicao, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("varredura interrompida", e);
        }
    }

    // -------------------------------------------------------------------------
    // XML
    // -------------------------------------------------------------------------

    static List<EntradaRemota> interpretarMultistatus(byte[] xml, String caminhoPedido)
            throws IOException {
        Document documento;
        try {
            DocumentBuilder construtor = construtorSeguro();
            // Sem isto, o parser escreve o erro em stderr ANTES de lançar, e o
            // log da aplicação ganha uma linha que ninguém configurou. A
            // exceção continua sendo lançada e tratada logo abaixo.
            construtor.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException e) {}
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            documento = construtor.parse(new ByteArrayInputStream(xml));
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("multistatus ilegível para " + caminhoPedido, e);
        }

        List<EntradaRemota> entradas = new ArrayList<>();
        NodeList respostas = documento.getElementsByTagNameNS("DAV:", "response");
        for (int i = 0; i < respostas.getLength(); i++) {
            Element resposta = (Element) respostas.item(i);
            String href = texto(resposta, "href");
            if (href == null) {
                continue;
            }
            String caminho = CaminhoRemoto.canonicalizar(href);
            boolean colecao = elemento(resposta, "collection") != null;
            String etag = texto(resposta, "getetag");
            if (etag != null) {
                etag = etag.replace("\"", "").replaceFirst("^W/", "").trim();
            }
            entradas.add(new EntradaRemota(
                    caminho,
                    etag,
                    numero(texto(resposta, "getcontentlength")),
                    data(texto(resposta, "getlastmodified")),
                    colecao));
        }
        return entradas;
    }

    /**
     * Parser com resolução de entidades externas desligada.
     *
     * <p>Cap. 8.1 trata o conteúdo da origem como hostil, e um multistatus é
     * XML de terceiro: sem estas travas, uma resposta forjada lê arquivos do
     * worker ou dispara requisições internas (XXE, OWASP A05).
     */
    private static DocumentBuilder construtorSeguro() throws ParserConfigurationException {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrica.setXIncludeAware(false);
        fabrica.setExpandEntityReferences(false);
        fabrica.setNamespaceAware(true);
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return fabrica.newDocumentBuilder();
    }

    private static Element elemento(Element raiz, String nomeLocal) {
        NodeList achados = raiz.getElementsByTagNameNS("DAV:", nomeLocal);
        return achados.getLength() == 0 ? null : (Element) achados.item(0);
    }

    private static String texto(Element raiz, String nomeLocal) {
        Element e = elemento(raiz, nomeLocal);
        if (e == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        NodeList filhos = e.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            if (filhos.item(i).getNodeType() == Node.TEXT_NODE) {
                sb.append(filhos.item(i).getNodeValue());
            }
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static long numero(String valor) {
        try {
            return valor == null ? -1 : Long.parseLong(valor);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Instant data(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(valor, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException e) {
            return null;   // data ilegível não invalida a entrada; o delta cai para o ETag
        }
    }
}
