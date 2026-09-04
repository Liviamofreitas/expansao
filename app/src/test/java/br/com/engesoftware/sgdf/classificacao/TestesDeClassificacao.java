package br.com.engesoftware.sgdf.classificacao;

import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import br.com.engesoftware.sgdf.validacao.DigitoVerificador;
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
 * Testes do motor de classificacao (F1-03).
 *
 * <p>Os textos dos fixtures sao trechos dos documentos reais da massa, porque o
 * que quebra um classificador de ancoras e o texto que os documentos de fato
 * tem — nao o que se imagina que eles teriam.
 */
public final class TestesDeClassificacao {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    static final Classificador MOTOR = new Classificador(CargaDeRegras.todas());

    public static void main(String[] args) throws Exception {
        executar("digitoVerificador", TestesDeClassificacao::digitoVerificador);
        executar("classificaPeloConteudo", TestesDeClassificacao::classificaPeloConteudo);
        executar("certidoesParecidasNaoSeConfundem", TestesDeClassificacao::certidoesParecidasNaoSeConfundem);
        executar("documentoQueContemOutroNaoViraOOutro", TestesDeClassificacao::documentoQueContemOutroNaoViraOOutro);
        executar("oNomeDoArquivoNaoClassifica", TestesDeClassificacao::oNomeDoArquivoNaoClassifica);
        executar("empateVaiParaTriagem", TestesDeClassificacao::empateVaiParaTriagem);
        executar("documentoVazioNaoEReconhecido", TestesDeClassificacao::documentoVazioNaoEReconhecido);
        executar("aDecisaoDizPorQue", TestesDeClassificacao::aDecisaoDizPorQue);
        executar("cadastroIncoerenteERecusado", TestesDeClassificacao::cadastroIncoerenteERecusado);
        executar("cargaJavaEseedNaoDivergem", TestesDeClassificacao::cargaJavaEseedNaoDivergem);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    /**
     * Secao 6 dos achados: o padrao de CPF sozinho casa com codigo de barras e
     * numero de autenticacao. Um falso positivo num documento classificado como
     * sem dado pessoal e uma falha de privacidade silenciosa.
     */
    static void digitoVerificador() {
        ok("DV . CPF real e aceito", DigitoVerificador.cpfValido("052.190.471-40"));
        ok("DV . CPF com um digito trocado e recusado",
                !DigitoVerificador.cpfValido("052.190.471-41"));
        ok("DV . sequencia de digito repetido nao e CPF",
                !DigitoVerificador.cpfValido("111.111.111-11"));
        ok("DV . trecho de codigo de barras com formato de CPF e recusado",
                !DigitoVerificador.cpfValido("858.600.005-02"));
        ok("DV . CNPJ real e aceito", DigitoVerificador.cnpjValido("00.681.946/0001-60"));
        ok("DV . CNPJ com digito trocado e recusado",
                !DigitoVerificador.cnpjValido("00.681.946/0001-61"));
        ok("DV . CNPJ truncado nao passa", !DigitoVerificador.cnpjValido("00.681.946"));
    }

    static void classificaPeloConteudo() throws Exception {
        Classificacao r = classificar(
                "ministerio da fazenda secretaria da receita federal do brasil",
                "procuradoria-geral da fazenda nacional",
                "certidao positiva com efeitos de negativa de debitos relativos aos",
                "tributos federais e a divida ativa da uniao",
                "nome: engesoftware tecnologia s/a cnpj: 00.681.946/0001-60");

        ok("F1-03 . a certidao da RFB e reconhecida pelo conteudo",
                r.automatica() && r.tipo().equals("CER.CND_RFB"));
        ok("F1-03 . certidao POSITIVA com efeitos de negativa tambem casa",
                r.melhor().orElseThrow().scoreDeConteudo() == 1.0);
    }

    /**
     * As duas certidoes do TJDFT compartilham o cabecalho inteiro: mesmo
     * tribunal, mesma formula, mesmas instancias. So a expressao da
     * distribuicao as separa.
     */
    static void certidoesParecidasNaoSeConfundem() throws Exception {
        String[] comum = {
                "poder judiciario da uniao tribunal de justica do distrito federal",
                "1a e 2a instancias",
                "certificamos que, apos consulta aos registros eletronicos de distribuicao"};

        Classificacao civel = classificar(concatenar(comum,
                "certidao positiva de distribuicao (especial - acoes civeis e criminais)"));
        Classificacao falencia = classificar(concatenar(comum,
                "certidao negativa de distribuicao (acoes de falencias e "
                        + "recuperacoes judiciais)"));

        ok("F1-03 . a certidao de acoes civeis e criminais e reconhecida",
                civel.automatica() && civel.tipo().equals("CER.CND_CIVEL_CRIMINAL"));
        ok("F1-03 . a de falencias e recuperacoes tambem",
                falencia.automatica() && falencia.tipo().equals("CER.CND_FALENCIA"));
        ok("F1-03 . e nenhuma das duas aparece como candidata da outra",
                civel.candidatos().stream().noneMatch(c -> c.tipo().equals("CER.CND_FALENCIA"))
                        && falencia.candidatos().stream()
                                .noneMatch(c -> c.tipo().equals("CER.CND_CIVEL_CRIMINAL")));
    }

    /**
     * O caso que corrigiu a carga de regras. O comprovante bancario do Santander
     * REPRODUZ O DARF INTEIRO dentro dele: "composicao do documento de
     * arrecadacao", "periodo de apuracao", "valor total do documento". Com um
     * discriminante tirado da composicao, os dois marcavam 1,00 e o comprovante
     * ia para triagem como se fosse a DCTFWeb.
     */
    static void documentoQueContemOutroNaoViraOOutro() throws Exception {
        Classificacao comprovante = classificar(
                "internet banking empresarial engesoftware tecnologia s/a",
                "pagamento com codigo de barras > 2a via de comprovante",
                "comprovante de pagamento de darf",
                "documento de arrecadacao de receitas federais",
                "composicao do documento de arrecadacao periodo de apuracao",
                "data de pagamento: 31/07/2026 valor total: r$ 50.209,27");

        ok("F1-03 . o comprovante que embute o DARF e comprovante, nao DCTFWeb",
                comprovante.automatica() && comprovante.tipo().equals("CMP.DARF"));
        ok("F1-03 . e a DCTFWeb nem entra como candidata",
                comprovante.candidatos().stream()
                        .noneMatch(c -> c.tipo().equals("INS.DCTFWEB")));

        Classificacao dctf = classificar(
                "dctfweb recibo de entrega",
                "documento de arrecadacao de receitas federais",
                "periodo de apuracao junho/2026",
                "composicao do documento de arrecadacao");
        ok("F1-03 . a DCTFWeb continua sendo reconhecida pelo recibo de transmissao",
                dctf.automatica() && dctf.tipo().equals("INS.DCTFWEB"));
    }

    /**
     * O sistema existe para identificar o arquivo pelo conteudo. Um bonus de
     * nome ou de pasta desempata; nunca elege.
     */
    static void oNomeDoArquivoNaoClassifica() throws Exception {
        byte[] pdf = documento("relatorio interno de acompanhamento",
                "planilha de controle sem nenhuma ancora conhecida");
        Bonus comoSeOArquivoSeChamasseAssim =
                new Bonus(Map.of("CER.CND_RFB", 0.20, "CER.CNDT", 0.20));

        Classificacao r = MOTOR.classificar(
                new ExtratorPdfBox().extrair(pdf), comoSeOArquivoSeChamasseAssim);

        ok("F1-03 . bonus de nome nao classifica documento sem ancora",
                r.decisao() == Decisao.NAO_RECONHECIDO);
        ok("F1-03 . bonus acima do teto e recusado no cadastro",
                recusa(() -> new Bonus(Map.of("CER.CNDT", 0.50))));
    }

    static void empateVaiParaTriagem() throws Exception {
        // Duas regras com as mesmas ancoras: e o empate puro.
        Classificador motor = new Classificador(List.of(
                RegraDeReconhecimento.de("A.UM", List.of(Ancora.de("relatorio mensal", 1))),
                RegraDeReconhecimento.de("A.DOIS", List.of(Ancora.de("relatorio mensal", 1)))));

        Classificacao r = motor.classificar(
                new ExtratorPdfBox().extrair(documento("relatorio mensal de servicos")));

        ok("F1-03 . dois tipos com o mesmo score vao para triagem",
                r.decisao() == Decisao.TRIAGEM);
        ok("F1-03 . e o motivo diz que empate e duvida",
                r.motivo().contains("margem mínima"));
    }

    static void documentoVazioNaoEReconhecido() throws Exception {
        // Achado A12: o comprovante do Itau so com rotulos.
        Classificacao r = classificar("sispag salarios",
                "nome da empresa: agencia: conta corrente:", "nome: valor:");

        ok("A12 . o documento vazio nao e classificado",
                r.decisao() == Decisao.NAO_RECONHECIDO);
    }

    /** Cap. 16: a decisao precisa ser reproduzivel a partir do que a produziu. */
    static void aDecisaoDizPorQue() throws Exception {
        Classificacao r = classificar("certificado de regularidade do fgts - crf",
                "inscricao: 00.681.946/0001-60 caixa economica federal",
                "art. 7 da lei 8.036, de 11 de maio de 1990");

        Candidato melhor = r.melhor().orElseThrow();
        ok("Cap. 16 . a decisao registra as ancoras que a produziram",
                melhor.evidencias().size() >= 3);
        ok("Cap. 16 . e a versao da regra usada", melhor.versaoDaRegra() == 1);
        ok("Cap. 16 . o motivo traz o score e o limiar",
                r.motivo().contains("0,95") || r.motivo().contains("0.95"));
    }

    static void cadastroIncoerenteERecusado() {
        ok("F1-03 . regra sem ancora e recusada — classificaria pelo nome",
                recusa(() -> RegraDeReconhecimento.de("X.Y", List.of())));
        ok("F1-03 . ancora com peso nao positivo e recusada",
                recusa(() -> Ancora.de("qualquer", 0)));
        ok("F1-03 . limiar de triagem acima do automatico e recusado",
                recusa(() -> new RegraDeReconhecimento("X.Y",
                        List.of(Ancora.de("a", 1)), List.of(), List.of(), 0, 0.7, 0.95, 1)));
    }

    /**
     * A carga de regras existe em dois lugares — {@link CargaDeRegras}, para a
     * medicao ser reproduzivel sem banco, e o seed V103, que e o cadastro de
     * verdade. Editar um e esquecer o outro faria a medicao medir uma coisa e a
     * producao classificar por outra.
     */
    static void cargaJavaEseedNaoDivergem() throws Exception {
        java.nio.file.Path seed = java.nio.file.Path.of(
                System.getProperty("sgdf.raiz", "."),
                "db/seed/V103__regras_de_reconhecimento.sql");
        if (!java.nio.file.Files.exists(seed)) {
            falhas.add("Carga . o seed V103 nao foi encontrado em " + seed.toAbsolutePath());
            return;
        }
        String sql = java.nio.file.Files.readString(seed);

        List<String> ausentesNoSeed = new ArrayList<>();
        for (RegraDeReconhecimento r : CargaDeRegras.corporativas()) {
            if (!sql.contains("'" + r.tipo() + "'")) {
                ausentesNoSeed.add(r.tipo());
                continue;
            }
            for (Ancora d : r.discriminantes()) {
                // No SQL a barra invertida do regex aparece dobrada.
                String noSql = d.expressao().pattern().replace("\\", "\\\\");
                if (!sql.contains(noSql)) {
                    ausentesNoSeed.add(r.tipo() + " discriminante " + noSql);
                }
            }
        }
        ok("Carga . todo tipo e discriminante do codigo esta no seed V103 — "
                + ausentesNoSeed, ausentesNoSeed.isEmpty());

        // Os comprovantes bancarios NAO vao para o seed de propósito: cap. 8.5,
        // "sem âncora fixa — identificado pelo pareamento". Eles reconhecem a
        // familia; qual obrigacao o comprovante paga e o pareamento que decide.
        List<String> comprovantesNoSeed = new ArrayList<>();
        for (RegraDeReconhecimento r : CargaDeRegras.comprovantesBancarios()) {
            if (sql.contains("'" + r.tipo() + "'")) {
                comprovantesNoSeed.add(r.tipo());
            }
        }
        ok("Carga . os comprovantes bancarios ficam fora do seed — quem decide o "
                + "tipo e o pareamento " + comprovantesNoSeed, comprovantesNoSeed.isEmpty());
    }

    // -------------------------------------------------------------------------

    static Classificacao classificar(String... linhas) throws IOException {
        return MOTOR.classificar(new ExtratorPdfBox().extrair(documento(linhas)));
    }

    static String[] concatenar(String[] base, String extra) {
        List<String> tudo = new ArrayList<>(List.of(base));
        tudo.add(extra);
        return tudo.toArray(new String[0]);
    }

    static byte[] documento(String... linhas) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                float y = 750;
                for (String linha : linhas) {
                    f.beginText();
                    f.newLineAtOffset(40, y);
                    f.showText(linha);
                    f.endText();
                    y -= 14;
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
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

    /**
     * Roda um teste isolando a sua falha.
     *
     * <p>Sem isto, uma excecao dentro de um teste derruba a suite inteira e
     * esconde o resultado de todos os outros — que foi exatamente o que
     * aconteceu ao verificar a trava de divergencia entre a carga e o seed.
     */
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

    private TestesDeClassificacao() {}
}
