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
    private final String prefixo;
    private final String autorizacao;
    private final Duration timeout;

    public ClienteWebDav(URI base, String usuario, String senha, Duration timeout) {
        this.base = base;
        // O CAMINHO DA BASE É PREFIXO, E ANTES ERA DESCARTADO EM SILÊNCIO.
        //
        // `new URI(esquema, null, host, porta, caminho, null, null)` monta a
        // URI a partir das PARTES — o caminho que viesse em SGDF_WEBDAV_BASE
        // simplesmente não entrava nela. Configurar
        // `https://cloud/remote.php/dav/files/svc` e pedir `/FATURAMENTO`
        // produzia GET em `https://cloud/FATURAMENTO`: 404, reportado como
        // falha de coleta, apontando para o arquivo em vez de para a
        // configuração.
        //
        // Com o prefixo honrado, `pasta_origem` e `documento.caminho` passam a
        // ser RELATIVOS à base — e é isso que torna a troca de nuvem uma
        // mudança de variável de ambiente em vez de um UPDATE em todo caminho
        // já registrado. Ver ADR-005.
        this.prefixo = prefixoDe(base);
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

    /**
     * Monta a URI reencodando o caminho.
     *
     * <p><b>O caminho chega DECODIFICADO.</b> {@code CaminhoRemoto.canonicalizar}
     * decodifica o href do PROPFIND para poder validar {@code ..} e caracteres de
     * controle — e devolve, por exemplo, {@code /BNB/2026/04/052.190.471-40
     * folha.pdf}. Entregar isso a {@code URI.resolve} estoura com
     * "Illegal character in path": espaço, parêntese e acento não são válidos numa
     * URI crua, e nome de arquivo digitado por gente tem os três.
     *
     * <p>Não era hipótese: apareceu ao baixar a primeira cópia de conflito
     * (F1-10), cujo nome tem espaço e parêntese por construção. A massa real já
     * traz nomes com espaço, então o defeito atingia arquivos legítimos — só não
     * tinha sido exercitado porque os testes de F1-01 usavam nomes sem espaço.
     *
     * <p>O construtor de sete argumentos de {@link URI} é quem faz o quoting
     * certo: montar a string à mão erraria em {@code +} e {@code %}.
     */
    private URI alvo(String caminho) {
        try {
            return new URI(base.getScheme(), null, base.getHost(), base.getPort(),
                    prefixo + caminho, null, null);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("caminho remoto inválido: " + caminho, e);
        }
    }

    private HttpRequest.Builder requisicao(String caminho) {
        HttpRequest.Builder b = HttpRequest.newBuilder(alvo(caminho)).timeout(timeout);
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

    List<EntradaRemota> interpretarMultistatus(byte[] xml, String caminhoPedido)
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
            String caminho = relativizar(CaminhoRemoto.canonicalizar(href));
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
     * O caminho da base, decodificado e sem barra final — ou vazio.
     *
     * <p>Decodificado porque é assim que ele precisa chegar ao construtor de
     * sete argumentos de {@link URI}, que faz o quoting; e porque é no espaço
     * decodificado que {@link CaminhoRemoto#canonicalizar} devolve o href,
     * onde a comparação de {@link #relativizar} acontece. Os dois lados no
     * mesmo espaço, ou a comparação erraria em toda base com espaço ou acento.
     */
    static String prefixoDe(URI base) {
        String caminho = base.getRawPath();
        if (caminho == null || caminho.isBlank()) {
            return "";
        }
        String canonico = CaminhoRemoto.canonicalizar(caminho);
        if (canonico.endsWith("/")) {
            canonico = canonico.substring(0, canonico.length() - 1);
        }
        return canonico.equals("/") ? "" : canonico;
    }

    /**
     * Tira o prefixo da base do caminho que o servidor devolveu.
     *
     * <p><b>Fora do prefixo, o caminho volta INTEIRO e absoluto</b>, de
     * propósito. Ele então não estará dentro da raiz do contrato — que é
     * relativa — e a {@link Varredura} o registra como <i>"fora da raiz"</i>,
     * com o mesmo controle do cap. 14.1 que já existe e já é testado. Devolver
     * {@code null} e descartar aqui esconderia do painel um href que aponta
     * para fora do que foi configurado, que é exatamente o que alguém precisa
     * ver.
     *
     * <p><b>A comparação exige fronteira de segmento</b>, e não é zelo: com
     * prefixo {@code /dav/files/livia}, um {@code startsWith} cru aceitaria
     * {@code /dav/files/liviaOUTRA/x.pdf} e o relativizaria para
     * {@code OUTRA/x.pdf} — um caminho de OUTRA conta entrando no sistema como
     * se fosse desta, e sem barra inicial, o que ainda quebraria a contenção
     * de raiz logo adiante.
     */
    String relativizar(String caminho) {
        if (prefixo.isEmpty()) {
            return caminho;
        }
        if (caminho.equals(prefixo)) {
            return "/";
        }
        if (caminho.startsWith(prefixo + "/")) {
            return caminho.substring(prefixo.length());
        }
        return caminho;
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
