package br.com.engesoftware.sgdf.coleta;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes da historia F1-01 — conector WebDAV com delta por ETag e antivirus.
 *
 * <p>Criterio de aceite: "Varredura de pasta real; arquivo infectado (EICAR)
 * rejeitado e logado".
 *
 * <p>Executor proprio em vez de JUnit porque o ambiente ainda nao tem espelho
 * Maven (D-06, sem internet). Quando o espelho existir, estes casos migram para
 * JUnit 5 sem mudar o que verificam.
 */
public final class TestesDeColeta {

    /**
     * String de teste EICAR — padrao da industria, criada justamente para
     * verificar antivirus sem usar codigo malicioso. E inerte.
     */
    static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    static final char NUL = (char) 0;

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        caminhoRemoto();
        politicaDeArquivos();
        interpretacaoDeRespostaClamd();
        parsingDeMultistatus();
        antivirusComEicar();
        varreduraCompleta();
        deltaPorEtag();
        antivirusIndisponivel();
        recursaoLimitada();
        hrefForaDaRaiz();

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    // =========================================================================
    // Contencao de caminho — premissa R-03
    // =========================================================================
    static void caminhoRemoto() {
        ok("R-03 . '..' e colapsado no caminho",
                CaminhoRemoto.canonicalizar("/BNB/2026/04/../../etc/senha")
                        .equals("/BNB/etc/senha"));

        ok("R-03 . codificacao percentual e decodificada uma unica vez",
                CaminhoRemoto.canonicalizar("/BNB/%2e%2e/fora").equals("/fora"));

        // Decodificar em laco transformaria %252e em '..' num segundo passe.
        ok("R-03 . codificacao DUPLA nao vira travessia",
                CaminhoRemoto.canonicalizar("/BNB/%252e%252e/x").equals("/BNB/%2e%2e/x"));

        ok("R-03 . caractere de controle no caminho e rejeitado",
                lanca(() -> CaminhoRemoto.canonicalizar("/BNB/nota" + (char) 7 + ".pdf")));

        ok("Cap. 14.1 . caminho dentro da raiz e aceito",
                CaminhoRemoto.dentroDe("/BNB - 482023", "/BNB - 482023/2026/04/nota.pdf"));

        ok("Cap. 14.1 . caminho fora da raiz e recusado",
                !CaminhoRemoto.dentroDe("/BNB - 482023", "/OUTRO/2026/04/nota.pdf"));

        // "/BNB - 4820239" nao esta dentro de "/BNB - 482023".
        ok("Cap. 14.1 . prefixo parecido nao conta como dentro",
                !CaminhoRemoto.dentroDe("/BNB - 482023", "/BNB - 4820239/nota.pdf"));
    }

    // =========================================================================
    // Politica de arquivos — cap. 8.1
    // =========================================================================
    static void politicaDeArquivos() {
        PoliticaDeArquivos p = PoliticaDeArquivos.padrao();

        ok("Cap. 8.1 . PDF e aceito",
                p.avaliar(arquivo("/x/nota.pdf", 1000)).aceito());
        ok("Cap. 8.1 . temporario do Office e ignorado",
                p.avaliar(arquivo("/x/~$folha.xlsx", 10)).motivo()
                        == PoliticaDeArquivos.Motivo.ARQUIVO_TEMPORARIO);
        ok("Cap. 8.1 . .tmp e ignorado",
                p.avaliar(arquivo("/x/parcial.tmp", 10)).motivo()
                        == PoliticaDeArquivos.Motivo.ARQUIVO_TEMPORARIO);
        ok("Cap. 8.1 . copia de conflito de sincronizacao e ignorada",
                p.avaliar(arquivo("/x/guia (conflicted copy 2026-04-01).pdf", 10)).motivo()
                        == PoliticaDeArquivos.Motivo.COPIA_DE_CONFLITO);
        ok("Cap. 8.1 . sufixo de copia tambem e conflito",
                p.avaliar(arquivo("/x/guia - Copia.pdf", 10)).motivo()
                        == PoliticaDeArquivos.Motivo.COPIA_DE_CONFLITO);
        ok("D-10 . checklist em planilha e ignorado",
                p.avaliar(arquivo("/x/CHECKLIST BNB.xlsx", 10)).motivo()
                        == PoliticaDeArquivos.Motivo.CHECKLIST_EM_PLANILHA);
        // Um PDF com "checklist" no nome pode ser documento legitimo do cliente.
        ok("D-10 . checklist em PDF NAO e descartado",
                p.avaliar(arquivo("/x/checklist assinado.pdf", 10)).aceito());
        ok("Cap. 8.1 . extensao fora da lista e ignorada",
                p.avaliar(arquivo("/x/foto.jpg", 10)).motivo()
                        == PoliticaDeArquivos.Motivo.EXTENSAO_NAO_ACEITA);
        ok("Cap. 8.1 . arquivo acima do limite e ignorado",
                p.avaliar(arquivo("/x/enorme.pdf", 60L * 1024 * 1024)).motivo()
                        == PoliticaDeArquivos.Motivo.TAMANHO_EXCEDIDO);
    }

    // =========================================================================
    // Protocolo clamd
    // =========================================================================
    static void interpretacaoDeRespostaClamd() {
        ok("clamd . stream OK e limpo",
                AntivirusClamd.interpretar("x", "stream: OK").permitido());
        ok("clamd . FOUND e infectado, com a assinatura preservada",
                AntivirusClamd.interpretar("x", "stream: Eicar-Test-Signature FOUND")
                        .assinatura().equals("Eicar-Test-Signature"));
        // Um ERROR lido como limpo seria arquivo nao verificado entrando.
        ok("clamd . resposta de erro e INDISPONIVEL, nunca limpo",
                AntivirusClamd.interpretar("x", "ERROR: size limit exceeded").situacao()
                        == VeredictoAntivirus.Situacao.INDISPONIVEL);
    }

    // =========================================================================
    // PROPFIND
    // =========================================================================
    static void parsingDeMultistatus() throws Exception {
        String xml = multistatus(
                entradaXml("/c/2026/04/", null, null, true),
                entradaXml("/c/2026/04/nota.pdf", "\"abc123\"", "2048", false));

        List<EntradaRemota> entradas =
                ClienteWebDav.interpretarMultistatus(xml.getBytes(StandardCharsets.UTF_8), "/c");

        ok("PROPFIND . colecao e arquivo sao distinguidos",
                entradas.size() == 2 && entradas.get(0).colecao() && !entradas.get(1).colecao());
        ok("PROPFIND . ETag chega sem aspas", "abc123".equals(entradas.get(1).etag()));
        ok("PROPFIND . tamanho e lido", entradas.get(1).tamanho() == 2048);

        // XXE: um multistatus e XML de terceiro (R-03).
        String comDoctype = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>&x;</d:href>"
                + "</d:response></d:multistatus>";
        ok("R-03 . multistatus com DOCTYPE e rejeitado (XXE)",
                lanca(() -> {
                    try {
                        ClienteWebDav.interpretarMultistatus(
                                comDoctype.getBytes(StandardCharsets.UTF_8), "/c");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    // =========================================================================
    // Criterio de aceite: EICAR rejeitado e registrado
    // =========================================================================
    static void antivirusComEicar() throws Exception {
        try (ClamdSimulado clamd = new ClamdSimulado()) {
            Antivirus av = new AntivirusClamd("127.0.0.1", clamd.porta(), 5000);

            VeredictoAntivirus limpo = av.verificar("/x/nota.pdf",
                    "conteudo inofensivo".getBytes(StandardCharsets.UTF_8));
            ok("F1-01 . arquivo comum passa no antivirus", limpo.permitido());

            VeredictoAntivirus infectado = av.verificar("/x/malicioso.pdf",
                    EICAR.getBytes(StandardCharsets.US_ASCII));
            ok("F1-01 . EICAR e detectado como infectado",
                    infectado.situacao() == VeredictoAntivirus.Situacao.INFECTADO);
            ok("F1-01 . a assinatura do EICAR e preservada para o registro",
                    "Eicar-Test-Signature".equals(infectado.assinatura()));
        }
    }

    // =========================================================================
    // Varredura de ponta a ponta
    // =========================================================================
    static void varreduraCompleta() throws Exception {
        Map<String, byte[]> arquivos = new HashMap<>();
        arquivos.put("/BNB/2026/04/nota.pdf", "nota fiscal".getBytes(StandardCharsets.UTF_8));
        arquivos.put("/BNB/2026/04/encargos/guia-fgts.pdf", "guia".getBytes(StandardCharsets.UTF_8));
        arquivos.put("/BNB/2026/04/malicioso.pdf", EICAR.getBytes(StandardCharsets.US_ASCII));
        arquivos.put("/BNB/2026/04/~$rascunho.xlsx", "temp".getBytes(StandardCharsets.UTF_8));
        arquivos.put("/BNB/2026/04/CHECKLIST.xlsx", "planilha".getBytes(StandardCharsets.UTF_8));
        arquivos.put("/BNB/2026/04/foto.jpg", "imagem".getBytes(StandardCharsets.UTF_8));

        try (WebDavSimulado servidor = new WebDavSimulado(arquivos);
             ClamdSimulado clamd = new ClamdSimulado()) {

            ResultadoVarredura r = varredura(servidor, clamd)
                    .varrer("/BNB", "2026-04", caminho -> null);

            ok("F1-01 . varredura de pasta real coleta os validos, inclusive em subpasta",
                    r.coletados.size() == 2);
            ok("F1-01 . o arquivo infectado NAO e coletado",
                    r.coletados.stream().noneMatch(c -> c.caminho().endsWith("malicioso.pdf")));
            ok("F1-01 . o arquivo infectado e registrado com a assinatura",
                    r.infectados.size() == 1
                            && r.infectados.get(0).caminho().endsWith("malicioso.pdf")
                            && r.infectados.get(0).assinatura().equals("Eicar-Test-Signature"));
            ok("Cap. 8.1 . temporario, checklist e extensao invalida sao ignorados COM registro",
                    r.ignorados.size() == 3);
            ok("Cap. 8.1 . nada e descartado em silencio", r.visitados() == 6);
            ok("Cap. 8.1 . o hash do conteudo e calculado",
                    r.coletados.stream().allMatch(c -> c.sha256().length() == 64));
        }
    }

    // =========================================================================
    // Delta por ETag
    // =========================================================================
    static void deltaPorEtag() throws Exception {
        Map<String, byte[]> arquivos = Map.of(
                "/BNB/2026/04/nota.pdf", "nota".getBytes(StandardCharsets.UTF_8),
                "/BNB/2026/04/guia.pdf", "guia".getBytes(StandardCharsets.UTF_8));

        try (WebDavSimulado servidor = new WebDavSimulado(arquivos);
             ClamdSimulado clamd = new ClamdSimulado()) {

            ResultadoVarredura primeira = varredura(servidor, clamd)
                    .varrer("/BNB", "2026-04", caminho -> null);
            ok("F1-01 . a primeira varredura coleta tudo", primeira.coletados.size() == 2);

            Map<String, String> conhecido = new HashMap<>();
            primeira.coletados.forEach(c -> conhecido.put(c.caminho(), c.versao()));

            int antes = servidor.downloads.get();
            ResultadoVarredura segunda = varredura(servidor, clamd)
                    .varrer("/BNB", "2026-04", conhecido::get);

            ok("F1-01 . a segunda varredura nao recoleta o que nao mudou",
                    segunda.coletados.isEmpty() && segunda.inalterados.size() == 2);
            ok("F1-01 . e nem baixa os bytes de novo, que e o que torna o incremental barato",
                    servidor.downloads.get() == antes);

            servidor.substituir("/BNB/2026/04/guia.pdf",
                    "guia corrigida".getBytes(StandardCharsets.UTF_8));
            ResultadoVarredura terceira = varredura(servidor, clamd)
                    .varrer("/BNB", "2026-04", conhecido::get);

            ok("F1-01 . arquivo com ETag nova e recoletado",
                    terceira.coletados.size() == 1
                            && terceira.coletados.get(0).caminho().endsWith("guia.pdf"));
            ok("F1-01 . e o que nao mudou continua fora", terceira.inalterados.size() == 1);
        }
    }

    // =========================================================================
    // Antivirus fora do ar
    // =========================================================================
    static void antivirusIndisponivel() throws Exception {
        Map<String, byte[]> arquivos = Map.of(
                "/BNB/2026/04/nota.pdf", "nota".getBytes(StandardCharsets.UTF_8));

        try (WebDavSimulado servidor = new WebDavSimulado(arquivos)) {
            Antivirus indisponivel = new AntivirusClamd("127.0.0.1", 1, 500);
            ResultadoVarredura r = new Varredura(
                    servidor.cliente(), indisponivel, PoliticaDeArquivos.padrao(),
                    Varredura.Limites.padrao())
                    .varrer("/BNB", "2026-04", caminho -> null);

            ok("V1 . antivirus indisponivel NAO aprova o arquivo", r.coletados.isEmpty());
            ok("V1 . vira falha registrada, para o arquivo voltar na proxima varredura",
                    r.falhas.size() == 1 && r.falhas.get(0).erro().contains("antivírus indisponível"));
        }
    }

    // =========================================================================
    // Limites
    // =========================================================================
    static void recursaoLimitada() throws Exception {
        Map<String, byte[]> arquivos = Map.of(
                "/BNB/2026/04/n0.pdf", "a".getBytes(StandardCharsets.UTF_8),
                "/BNB/2026/04/a/n1.pdf", "b".getBytes(StandardCharsets.UTF_8),
                "/BNB/2026/04/a/b/n2.pdf", "c".getBytes(StandardCharsets.UTF_8),
                "/BNB/2026/04/a/b/c/n3.pdf", "d".getBytes(StandardCharsets.UTF_8),
                "/BNB/2026/04/a/b/c/d/n4.pdf", "e".getBytes(StandardCharsets.UTF_8));

        try (WebDavSimulado servidor = new WebDavSimulado(arquivos);
             ClamdSimulado clamd = new ClamdSimulado()) {

            ResultadoVarredura r = new Varredura(
                    servidor.cliente(), new AntivirusClamd("127.0.0.1", clamd.porta(), 5000),
                    PoliticaDeArquivos.padrao(),
                    new Varredura.Limites(2, 2000, Duration.ofMinutes(1)))
                    .varrer("/BNB", "2026-04", caminho -> null);

            // Profundidade 2 a partir da raiz do mes alcanca n0, n1 e n2.
            ok("Cap. 8.1 . a recursao respeita o limite de profundidade",
                    r.coletados.size() == 3);
        }
    }

    static void hrefForaDaRaiz() throws Exception {
        try (WebDavSimulado servidor = new WebDavSimulado(
                Map.of("/BNB/2026/04/nota.pdf", "n".getBytes(StandardCharsets.UTF_8)));
             ClamdSimulado clamd = new ClamdSimulado()) {

            servidor.hrefIntruso = "/OUTRO-CONTRATO/segredo.pdf";
            ResultadoVarredura r = varredura(servidor, clamd)
                    .varrer("/BNB", "2026-04", caminho -> null);

            ok("Cap. 14.1 . href apontando para fora da raiz e descartado e registrado",
                    r.coletados.size() == 1
                            && r.falhas.stream().anyMatch(f -> f.erro().contains("fora da raiz")));
        }
    }

    // =========================================================================
    // Apoio
    // =========================================================================

    static Varredura varredura(WebDavSimulado servidor, ClamdSimulado clamd) {
        return new Varredura(servidor.cliente(),
                new AntivirusClamd("127.0.0.1", clamd.porta(), 5000),
                PoliticaDeArquivos.padrao(), Varredura.Limites.padrao());
    }

    static EntradaRemota arquivo(String caminho, long tamanho) {
        return new EntradaRemota(caminho, "etag", tamanho, null, false);
    }

    static String entradaXml(String href, String etag, String tamanho, boolean colecao) {
        return "<d:response><d:href>" + href + "</d:href><d:propstat><d:prop>"
                + (etag == null ? "" : "<d:getetag>" + etag + "</d:getetag>")
                + (tamanho == null ? "" : "<d:getcontentlength>" + tamanho + "</d:getcontentlength>")
                + "<d:resourcetype>" + (colecao ? "<d:collection/>" : "") + "</d:resourcetype>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>";
    }

    static String multistatus(String... respostas) {
        return "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\">"
                + String.join("", respostas) + "</d:multistatus>";
    }

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    static boolean lanca(Runnable acao) {
        try {
            acao.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // -------------------------------------------------------------------------
    // Servidor WebDAV simulado
    // -------------------------------------------------------------------------
    static final class WebDavSimulado implements AutoCloseable {
        private final HttpServer servidor;
        private final Map<String, byte[]> arquivos;
        private final Map<String, String> etags = new HashMap<>();
        final AtomicInteger downloads = new AtomicInteger();
        String hrefIntruso;

        WebDavSimulado(Map<String, byte[]> arquivos) throws IOException {
            this.arquivos = new HashMap<>(arquivos);
            this.arquivos.forEach((k, v) -> etags.put(k, "etag-" + v.length + "-" + k.hashCode()));
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", troca -> {
                String caminho = troca.getRequestURI().getPath();
                if (troca.getRequestMethod().equals("PROPFIND")) {
                    responder(troca, 207, propfind(caminho).getBytes(StandardCharsets.UTF_8));
                } else if (this.arquivos.containsKey(caminho)) {
                    downloads.incrementAndGet();
                    responder(troca, 200, this.arquivos.get(caminho));
                } else {
                    responder(troca, 404, new byte[0]);
                }
            });
            servidor.start();
        }

        void substituir(String caminho, byte[] conteudo) {
            arquivos.put(caminho, conteudo);
            etags.put(caminho, "etag-" + conteudo.length + "-" + System.nanoTime());
        }

        ClienteWebDav cliente() {
            return new ClienteWebDav(
                    URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()),
                    null, null, Duration.ofSeconds(5));
        }

        /** Depth 1: a propria colecao, os filhos diretos e as subpastas imediatas. */
        private String propfind(String pasta) {
            String prefixo = pasta.endsWith("/") ? pasta : pasta + "/";
            List<String> partes = new ArrayList<>();
            partes.add(entradaXml(prefixo, null, null, true));

            Set<String> subpastas = new TreeSet<>();
            for (Map.Entry<String, byte[]> e : arquivos.entrySet()) {
                if (!e.getKey().startsWith(prefixo)) {
                    continue;
                }
                String resto = e.getKey().substring(prefixo.length());
                int barra = resto.indexOf('/');
                if (barra < 0) {
                    partes.add(entradaXml(e.getKey(), "\"" + etags.get(e.getKey()) + "\"",
                            String.valueOf(e.getValue().length), false));
                } else {
                    subpastas.add(prefixo + resto.substring(0, barra) + "/");
                }
            }
            subpastas.forEach(s -> partes.add(entradaXml(s, null, null, true)));
            if (hrefIntruso != null) {
                partes.add(entradaXml(hrefIntruso, "\"x\"", "10", false));
            }
            return multistatus(partes.toArray(String[]::new));
        }

        private static void responder(HttpExchange troca, int codigo, byte[] corpo)
                throws IOException {
            troca.sendResponseHeaders(codigo, corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        }

        @Override
        public void close() {
            servidor.stop(0);
        }
    }

    // -------------------------------------------------------------------------
    // clamd simulado — protocolo INSTREAM
    // -------------------------------------------------------------------------
    static final class ClamdSimulado implements AutoCloseable {
        private final ServerSocket socket;
        private volatile boolean ativo = true;

        ClamdSimulado() throws IOException {
            socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            Thread laco = new Thread(this::atender);
            laco.setDaemon(true);
            laco.start();
        }

        int porta() {
            return socket.getLocalPort();
        }

        private void atender() {
            String comando = "zINSTREAM" + NUL;
            while (ativo) {
                try (Socket conexao = socket.accept();
                     InputStream entrada = conexao.getInputStream();
                     OutputStream saida = conexao.getOutputStream()) {

                    byte[] cabecalho = entrada.readNBytes(comando.length());
                    if (!new String(cabecalho, StandardCharsets.US_ASCII).equals(comando)) {
                        saida.write(("ERROR: comando inesperado" + NUL)
                                .getBytes(StandardCharsets.US_ASCII));
                        continue;
                    }
                    ByteArrayOutputStream acumulado = new ByteArrayOutputStream();
                    while (true) {
                        byte[] tam = entrada.readNBytes(4);
                        if (tam.length < 4) {
                            break;
                        }
                        int n = ByteBuffer.wrap(tam).getInt();
                        if (n == 0) {
                            break;
                        }
                        acumulado.write(entrada.readNBytes(n));
                    }
                    boolean infectado =
                            acumulado.toString(StandardCharsets.US_ASCII).contains(EICAR);
                    saida.write(((infectado
                            ? "stream: Eicar-Test-Signature FOUND"
                            : "stream: OK") + NUL).getBytes(StandardCharsets.US_ASCII));
                    saida.flush();
                } catch (IOException e) {
                    // conexao encerrada pelo cliente; segue atendendo
                }
            }
        }

        @Override
        public void close() throws IOException {
            ativo = false;
            socket.close();
        }
    }

    private TestesDeColeta() {}
}
