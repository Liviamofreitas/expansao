package br.com.engesoftware.sgdf.book;

import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Testes da publicacao do book — historia F1-08.
 *
 * <p>Criterio de aceite do cap. 18: <i>"objeto imutavel (delete negado); hash
 * confere; indice legivel"</i>. Os tres estao verificados aqui, e o "indice
 * legivel" e verificado LENDO o PDF gerado com o extrator do proprio sistema —
 * gerar um PDF que abre nao prova que ele tem o conteudo certo.
 */
public final class TestesDeBook {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        executar("numeracaoSequencialSemLacunas", TestesDeBook::numeracaoSequencialSemLacunas);
        executar("ordenadaPorFamilia", TestesDeBook::ordenadaPorFamilia);
        executar("familiaDesconhecidaVaiParaOFim", TestesDeBook::familiaDesconhecidaVaiParaOFim);
        executar("tarjamentoPendenteRecusaAPublicacao",
                TestesDeBook::tarjamentoPendenteRecusaAPublicacao);
        executar("bookInternoNaoExigeTarjamento", TestesDeBook::bookInternoNaoExigeTarjamento);
        executar("hashDoConjuntoEReproduzivel", TestesDeBook::hashDoConjuntoEReproduzivel);
        executar("objetoEImutavel", TestesDeBook::objetoEImutavel);
        executar("republicarCriaVersaoNova", TestesDeBook::republicarCriaVersaoNova);
        executar("cicloNaoProntoNaoPublica", TestesDeBook::cicloNaoProntoNaoPublica);
        executar("conteudoTrocadoEntreMontarEPublicar",
                TestesDeBook::conteudoTrocadoEntreMontarEPublicar);
        executar("manifestoEConferivelSemOSistema",
                TestesDeBook::manifestoEConferivelSemOSistema);
        executar("indiceELegivel", TestesDeBook::indiceELegivel);
        executar("retencaoEALegalHoldDaA08", TestesDeBook::retencaoEALegalHoldDaA08);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    static void numeracaoSequencialSemLacunas() {
        List<Peca> pecas = new MontadorDeBook().numerar(tresDocumentos(), "2026-06", true);

        ok("F1-08 . as peças são numeradas a partir de 1",
                pecas.get(0).sequencia() == 1);
        boolean semLacuna = true;
        for (int i = 0; i < pecas.size(); i++) {
            semLacuna &= pecas.get(i).sequencia() == i + 1;
        }
        ok("Cap. 10 . a numeração é sequencial e sem lacunas", semLacuna);
        ok("Cap. 10 . o nome do arquivo carrega a sequência e a competência",
                pecas.get(0).arquivo().startsWith("01_")
                        && pecas.get(0).arquivo().endsWith("_2026-06.pdf"));
    }

    static void ordenadaPorFamilia() {
        List<Peca> pecas = new MontadorDeBook().numerar(tresDocumentos(), "2026-06", true);

        ok("Cap. 10 . as certidões vêm antes dos tributos",
                pecas.get(0).tipo().equals("CER.CND_RFB"));
        ok("Cap. 10 . e os tributos antes do FGTS",
                pecas.get(1).tipo().equals("INS.DCTFWEB")
                        && pecas.get(2).tipo().equals("FGT.GUIA"));
    }

    /**
     * Uma familia nova aparecendo antes das certidoes mudaria a numeracao de
     * todo o book — e o indice de um book ja entregue remete a numeros que
     * deixariam de corresponder.
     */
    static void familiaDesconhecidaVaiParaOFim() {
        List<DocumentoPublicavel> documentos = new ArrayList<>(tresDocumentos());
        documentos.add(new DocumentoPublicavel("XYZ.NOVO", "Familia Inventada",
                Sigilo.PUBLICO_CLIENTE, "novo".getBytes(StandardCharsets.UTF_8),
                "owncloud://x", OffsetDateTime.now(), false));

        List<Peca> pecas = new MontadorDeBook().numerar(documentos, "2026-06", true);

        ok("Cap. 10 . família desconhecida vai para o fim",
                pecas.get(3).tipo().equals("XYZ.NOVO"));
        ok("Cap. 10 . e a numeração das peças conhecidas não muda",
                pecas.get(0).tipo().equals("CER.CND_RFB") && pecas.get(0).sequencia() == 1);
    }

    /**
     * O redator e a historia F2-07 e nao existe. Publicar o original vazaria
     * dado pessoal — a massa real tem um relatorio do FGTS com 160 CPFs.
     */
    static void tarjamentoPendenteRecusaAPublicacao() {
        List<DocumentoPublicavel> comPessoal = new ArrayList<>(tresDocumentos());
        comPessoal.add(new DocumentoPublicavel("FGT.RELATORIO_DIGITAL", "FGTS",
                Sigilo.PESSOAL, "160 CPFs".getBytes(StandardCharsets.UTF_8),
                "owncloud://relatorio", OffsetDateTime.now(), false));

        boolean recusou = false;
        String motivo = "";
        try {
            new MontadorDeBook().numerar(comPessoal, "2026-06", true);
        } catch (MontadorDeBook.TarjamentoPendente e) {
            recusou = true;
            motivo = e.getMessage();
        }
        ok("LGPD . peça PESSOAL sem tarjamento recusa o book do cliente", recusou);
        ok("LGPD . e a recusa nomeia a peça",
                motivo.contains("FGT.RELATORIO_DIGITAL"));
        ok("LGPD . dizendo por que omitir também não serve",
                motivo.contains("parece completo"));

        // Tarjada, passa.
        List<DocumentoPublicavel> tarjado = new ArrayList<>(tresDocumentos());
        tarjado.add(new DocumentoPublicavel("FGT.RELATORIO_DIGITAL", "FGTS",
                Sigilo.PESSOAL, "CPFs mascarados".getBytes(StandardCharsets.UTF_8),
                "owncloud://relatorio", OffsetDateTime.now(), true));
        List<Peca> pecas = new MontadorDeBook().numerar(tarjado, "2026-06", true);
        ok("LGPD . a mesma peça tarjada entra no book",
                pecas.stream().anyMatch(p -> p.tipo().equals("FGT.RELATORIO_DIGITAL")
                        && p.tarjado()));
    }

    /** O original integro permanece no repositorio interno (cap. 10). */
    static void bookInternoNaoExigeTarjamento() {
        List<DocumentoPublicavel> comPessoal = new ArrayList<>(tresDocumentos());
        comPessoal.add(new DocumentoPublicavel("FGT.RELATORIO_DIGITAL", "FGTS",
                Sigilo.PESSOAL, "160 CPFs".getBytes(StandardCharsets.UTF_8),
                "owncloud://relatorio", OffsetDateTime.now(), false));

        List<Peca> pecas = new MontadorDeBook().numerar(comPessoal, "2026-06", false);
        ok("Cap. 10 . o book interno leva o original íntegro", pecas.size() == 4);
    }

    /**
     * O cliente precisa reproduzir o hash com o book na mao. Uma definicao
     * implicita nao e reproduzivel.
     */
    static void hashDoConjuntoEReproduzivel() {
        List<Peca> pecas = new MontadorDeBook().numerar(tresDocumentos(), "2026-06", true);
        String uma = Hash.doConjunto(pecas);
        String outra = Hash.doConjunto(pecas);
        ok("F1-08 . o hash do conjunto é determinístico", uma.equals(outra));

        // Mesmas pecas em ordem diferente sao books diferentes: a numeracao e
        // parte do que se entrega, e o indice remete a ela.
        List<Peca> invertidas = new ArrayList<>(pecas);
        java.util.Collections.reverse(invertidas);
        ok("F1-08 . e depende da ordem, porque a numeração é parte do book",
                !Hash.doConjunto(invertidas).equals(uma));

        ok("F1-08 . tem a forma de um SHA-256", uma.matches("[0-9a-f]{64}"));
    }

    /** Criterio de aceite: objeto imutavel, delete negado. */
    static void objetoEImutavel() throws IOException {
        Path raiz = Files.createTempDirectory("sgdf-book-");
        ArmazenamentoEmDiretorio bucket = new ArmazenamentoEmDiretorio(raiz);

        bucket.gravar("books/x/2026-06/v1/01_A.pdf", "primeiro".getBytes(StandardCharsets.UTF_8),
                ArmazenamentoImutavel.Retencao.semPrazo());

        boolean recusou = false;
        try {
            bucket.gravar("books/x/2026-06/v1/01_A.pdf",
                    "segundo".getBytes(StandardCharsets.UTF_8),
                    ArmazenamentoImutavel.Retencao.semPrazo());
        } catch (ArmazenamentoImutavel.ObjetoJaExiste e) {
            recusou = true;
        }
        ok("F1-08 . sobrescrever um objeto publicado é recusado", recusou);
        ok("F1-08 . e o conteúdo original continua lá",
                "primeiro".equals(new String(bucket.ler("books/x/2026-06/v1/01_A.pdf"),
                        StandardCharsets.UTF_8)));
        ok("F1-08 . a interface não tem método de remover — é garantia estrutural",
                java.util.Arrays.stream(ArmazenamentoImutavel.class.getMethods())
                        .noneMatch(m -> m.getName().toLowerCase(java.util.Locale.ROOT)
                                .contains("remov")
                                || m.getName().toLowerCase(java.util.Locale.ROOT)
                                        .contains("delet")
                                || m.getName().toLowerCase(java.util.Locale.ROOT)
                                        .contains("apag")));
    }

    static void republicarCriaVersaoNova() throws IOException {
        Path raiz = Files.createTempDirectory("sgdf-book-");
        ArmazenamentoEmDiretorio bucket = new ArmazenamentoEmDiretorio(raiz);
        PublicadorDeBook publicador = new PublicadorDeBook(bucket);
        MontadorDeBook montador = new MontadorDeBook();

        List<DocumentoPublicavel> documentos = tresDocumentos();
        List<Peca> pecas = montador.numerar(documentos, "2026-06", true);
        List<byte[]> conteudos = documentos.stream().sorted(
                java.util.Comparator.comparingInt(d -> ordem(d.tipo())))
                .map(DocumentoPublicavel::conteudo).toList();

        Book v1 = montador.montar("CT-01", "2026-06", 1, "svc-sgdf-publica", "1.0", pecas);
        publicador.publicar(v1, conteudos, true, UUID.randomUUID(),
                ArmazenamentoImutavel.Retencao.semPrazo());

        Book v2 = montador.montar("CT-01", "2026-06", 2, "svc-sgdf-publica", "1.0", pecas);
        publicador.publicar(v2, conteudos, true, UUID.randomUUID(),
                ArmazenamentoImutavel.Retencao.semPrazo());

        ok("Cap. 10 . a versão 1 continua no bucket",
                bucket.existe("books/CT-01/2026-06/v1/" + Manifesto.NOME));
        ok("Cap. 10 . e a versão 2 fica ao lado, sem tocar na anterior",
                bucket.existe("books/CT-01/2026-06/v2/" + Manifesto.NOME));
        ok("Cap. 10 . o prefixo segue a estrutura do capítulo",
                v2.caminhoBucket().equals("books/CT-01/2026-06/v2/"));
    }

    static void cicloNaoProntoNaoPublica() throws IOException {
        Path raiz = Files.createTempDirectory("sgdf-book-");
        PublicadorDeBook publicador = new PublicadorDeBook(new ArmazenamentoEmDiretorio(raiz));
        MontadorDeBook montador = new MontadorDeBook();
        List<DocumentoPublicavel> documentos = tresDocumentos();
        Book book = montador.montar("CT-01", "2026-06", 1, "svc", "1.0",
                montador.numerar(documentos, "2026-06", true));

        boolean recusou = false;
        String motivo = "";
        try {
            publicador.publicar(book, conteudosNaOrdem(documentos), false, UUID.randomUUID(),
                    ArmazenamentoImutavel.Retencao.semPrazo());
        } catch (PublicadorDeBook.CicloNaoPronto e) {
            recusou = true;
            motivo = e.getMessage();
        }
        ok("Cap. 10 . ciclo que não está PRONTO não publica", recusou);
        ok("Cap. 10 . e o motivo diz o que a publicação afirmaria",
                motivo.contains("regularidade que ainda não foi verificada"));
    }

    static void conteudoTrocadoEntreMontarEPublicar() throws IOException {
        Path raiz = Files.createTempDirectory("sgdf-book-");
        PublicadorDeBook publicador = new PublicadorDeBook(new ArmazenamentoEmDiretorio(raiz));
        MontadorDeBook montador = new MontadorDeBook();
        List<DocumentoPublicavel> documentos = tresDocumentos();
        Book book = montador.montar("CT-01", "2026-06", 1, "svc", "1.0",
                montador.numerar(documentos, "2026-06", true));

        List<byte[]> adulterados = new ArrayList<>(conteudosNaOrdem(documentos));
        adulterados.set(0, "outro conteudo".getBytes(StandardCharsets.UTF_8));

        boolean recusou = false;
        try {
            publicador.publicar(book, adulterados, true, UUID.randomUUID(),
                    ArmazenamentoImutavel.Retencao.semPrazo());
        } catch (IllegalStateException e) {
            recusou = true;
        }
        ok("F1-08 . conteúdo que mudou entre montar e publicar é recusado", recusou);
    }

    /** Criterio de aceite: hash confere. */
    static void manifestoEConferivelSemOSistema() throws IOException {
        Path raiz = Files.createTempDirectory("sgdf-book-");
        ArmazenamentoEmDiretorio bucket = new ArmazenamentoEmDiretorio(raiz);
        PublicadorDeBook publicador = new PublicadorDeBook(bucket);
        MontadorDeBook montador = new MontadorDeBook();
        List<DocumentoPublicavel> documentos = tresDocumentos();
        Book book = montador.montar("CT-01", "2026-06", 1, "svc-sgdf-publica", "1.3",
                montador.numerar(documentos, "2026-06", true));

        publicador.publicar(book, conteudosNaOrdem(documentos), true, UUID.randomUUID(),
                ArmazenamentoImutavel.Retencao.semPrazo());

        String json = new String(bucket.ler(book.caminhoBucket() + Manifesto.NOME),
                StandardCharsets.UTF_8);
        ok("Cap. 10 . o manifesto traz o hash do conjunto",
                json.contains(book.hashDoConjunto()));
        ok("Cap. 10 . a versão da matriz, para reprodutibilidade",
                json.contains("\"versao_matriz\": \"1.3\""));
        ok("Cap. 10 . e a origem de cada peça", json.contains("owncloud://"));

        // O que "hash confere" quer dizer: com os arquivos na mao, o hash de
        // cada um bate com o declarado.
        boolean todosConferem = true;
        for (Peca p : book.pecas()) {
            byte[] doArquivo = bucket.ler(book.caminhoBucket() + p.arquivo());
            todosConferem &= Hash.de(doArquivo).equals(p.hashSha256());
        }
        ok("F1-08 . o hash de cada arquivo publicado bate com o manifesto", todosConferem);
        ok("F1-08 . e o hash do conjunto é o dos hashes na ordem",
                Hash.doConjunto(book.pecas()).equals(book.hashDoConjunto()));
    }

    /** Criterio de aceite: indice legivel — conferido LENDO o PDF gerado. */
    static void indiceELegivel() throws IOException {
        MontadorDeBook montador = new MontadorDeBook();
        List<DocumentoPublicavel> documentos = tresDocumentos();
        Book book = montador.montar("CAIXA-09705.2025", "2026-06", 2, "svc-sgdf-publica", "1.3",
                montador.numerar(documentos, "2026-06", true));

        byte[] pdf = new GeradorDeIndice().gerar(book);
        String texto = new ExtratorPdfBox().extrair(pdf).textoCompleto();

        ok("F1-08 . o índice traz o contrato", texto.contains("CAIXA-09705.2025"));
        ok("F1-08 . a competência e a versão",
                texto.contains("2026-06") && texto.contains("versao 2"));
        ok("F1-08 . o hash do conjunto, quebrado para ser conferido a olho",
                texto.contains(book.hashDoConjunto().substring(0, 32)));
        ok("F1-08 . e a relação de peças com a sequência",
                texto.contains("001") && texto.contains("CER.CND_RFB"));
    }

    /** A08: o padrao e LEGAL_HOLD — indefinido e reversivel por papel autorizado. */
    static void retencaoEALegalHoldDaA08() throws IOException {
        Path raiz = Files.createTempDirectory("sgdf-book-");
        ArmazenamentoEmDiretorio bucket = new ArmazenamentoEmDiretorio(raiz);
        bucket.gravar("x", "y".getBytes(StandardCharsets.UTF_8),
                ArmazenamentoImutavel.Retencao.semPrazo());

        ok("A08 . o padrão é LEGAL_HOLD",
                bucket.retencaoDe("x").modo()
                        == ArmazenamentoImutavel.Retencao.Modo.LEGAL_HOLD);
        ok("A08 . LEGAL_HOLD não tem prazo", bucket.retencaoDe("x").ate() == null);
        ok("A08 . LEGAL_HOLD com data é recusado",
                recusa(() -> new ArmazenamentoImutavel.Retencao(
                        ArmazenamentoImutavel.Retencao.Modo.LEGAL_HOLD, OffsetDateTime.now())));
        ok("A08 . COMPLIANCE sem data também — é irreversível e exige temporalidade",
                recusa(() -> new ArmazenamentoImutavel.Retencao(
                        ArmazenamentoImutavel.Retencao.Modo.COMPLIANCE, null)));
    }

    // -------------------------------------------------------------------------

    static List<DocumentoPublicavel> tresDocumentos() {
        OffsetDateTime agora = OffsetDateTime.now();
        // Fora de ordem de proposito: quem ordena e o montador.
        return List.of(
                new DocumentoPublicavel("FGT.GUIA", "FGTS", Sigilo.INTERNO,
                        "guia do fgts".getBytes(StandardCharsets.UTF_8),
                        "owncloud://2026/06/guia.pdf", agora, false),
                new DocumentoPublicavel("CER.CND_RFB", "Certidões e regularidade",
                        Sigilo.PUBLICO_CLIENTE, "certidao rfb".getBytes(StandardCharsets.UTF_8),
                        "owncloud://2026/06/cnd.pdf", agora, false),
                new DocumentoPublicavel("INS.DCTFWEB", "INSS e tributos", Sigilo.INTERNO,
                        "dctfweb".getBytes(StandardCharsets.UTF_8),
                        "owncloud://2026/06/dctf.pdf", agora, false));
    }

    static int ordem(String tipo) {
        return switch (tipo) {
            case "CER.CND_RFB" -> 0;
            case "INS.DCTFWEB" -> 1;
            default -> 2;
        };
    }

    static List<byte[]> conteudosNaOrdem(List<DocumentoPublicavel> documentos) {
        return documentos.stream()
                .sorted(java.util.Comparator.comparingInt(d -> ordem(d.tipo())))
                .map(DocumentoPublicavel::conteudo).toList();
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

    static boolean recusa(Runnable acao) {
        try {
            acao.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
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

    private TestesDeBook() {}
}
