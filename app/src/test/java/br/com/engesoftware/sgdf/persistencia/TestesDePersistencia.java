package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.conciliacao.ResultadoDaConciliacao;
import br.com.engesoftware.sgdf.extracao.CampoExtraido;
import br.com.engesoftware.sgdf.extracao.RegiaoNoDocumento;
import br.com.engesoftware.sgdf.extracao.Retangulo;
import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import br.com.engesoftware.sgdf.validacao.Veredito;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Testes da persistencia contra o PostgreSQL REAL.
 *
 * <p>Nao contra banco em memoria: o que interessa testar sao as restricoes que
 * o esquema impoe — reprovacao sem motivo recusada, unicidade por hash,
 * historico que nao e substituido — e um H2 da vida nao as teria. Testar contra
 * um banco que nao tem as garantias seria testar outra coisa.
 *
 * <p>As linhas de teste sao marcadas em {@code criado_por} e removidas no fim.
 * Nao se usa transacao-sandbox porque os repositorios comitam de proposito: o
 * que se quer verificar e o comportamento deles, nao o de uma versao deles
 * neutralizada para o teste.
 */
public final class TestesDePersistencia {

    static final String MARCA = "teste-persistencia";

    /** Cada fixture de ciclo precisa de cliente e contrato proprios: o CNPJ e o
     *  numero do contrato sao unicos no banco. */
    static int sequenciaDoFixture = 0;

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (pulado: -Dsgdf.jdbc nao informado)");
            System.out.println("0/0 testes passaram.");
            return;
        }

        try (Connection conexao = DriverManager.getConnection(url)) {
            conexao.setAutoCommit(true);
            limpar(conexao);
            try {
                Sgdf sgdf = new Sgdf(conexao);
                executar("documentoEIdempotentePorHash",
                        () -> documentoEIdempotentePorHash(sgdf));
                executar("camposGuardamAPosicao", () -> camposGuardamAPosicao(sgdf));
                executar("reprocessarSubstituiOCampo", () -> reprocessarSubstituiOCampo(sgdf));
                executar("veredictoEGravadoComMotivo", () -> veredictoEGravadoComMotivo(sgdf));
                executar("reprovacaoSemMotivoNaoEGravavel",
                        () -> reprovacaoSemMotivoNaoEGravavel(sgdf));
                executar("historicoNaoESubstituido", () -> historicoNaoESubstituido(sgdf));
                executar("conciliacaoConformeTambemEGravada",
                        () -> conciliacaoConformeTambemEGravada(sgdf));
                executar("caractereDeControleNaoDerrubaAGravacao",
                        () -> caractereDeControleNaoDerrubaAGravacao(sgdf));
                executar("transacaoDesfazTudoOuNada", () -> transacaoDesfazTudoOuNada(sgdf));
                executar("bookEVersionado", () -> bookEVersionado(sgdf));
            } finally {
                limpar(conexao);
            }
        }

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    /**
     * A varredura e recursiva a partir da raiz do mes e passa varias vezes por
     * dia. Gravar de novo criaria linhas duplicadas com o mesmo hash, e V7
     * passaria a ver dois registros onde ha um arquivo.
     */
    static void documentoEIdempotentePorHash(Sgdf sgdf) {
        RepositorioDeDocumento repo = new RepositorioDeDocumento(sgdf);
        Documento d = documento("a");

        DocumentoGravado primeiro = repo.gravar(d);
        DocumentoGravado segundo = repo.gravar(d);

        ok("Persistência . o documento é gravado", primeiro.inedito());
        ok("Persistência . a segunda passagem da varredura não duplica",
                !segundo.inedito() && segundo.id().equals(primeiro.id()));
    }

    /** F1-02: o campo extraido exibe a regiao de origem no documento. */
    static void camposGuardamAPosicao(Sgdf sgdf) {
        RepositorioDeDocumento repo = new RepositorioDeDocumento(sgdf);
        UUID id = repo.gravar(documento("b")).id();

        repo.gravarCampos(id, List.of(campo("cnpj", "00.681.946/0001-60"),
                campo("validade", "25/11/2026")));

        Map<String, String> lidos = repo.camposDe(id);
        ok("F1-02 . os campos voltam do banco",
                "00.681.946/0001-60".equals(lidos.get("cnpj"))
                        && "25/11/2026".equals(lidos.get("validade")));

        String posicao = escalar(sgdf, "SELECT posicao::text FROM campo_extraido "
                + "WHERE documento_id = '" + id + "' AND campo = 'cnpj'");
        ok("F1-02 . e a posição de onde vieram",
                posicao.contains("pagina") && posicao.contains("retangulos"));
    }

    static void reprocessarSubstituiOCampo(Sgdf sgdf) {
        RepositorioDeDocumento repo = new RepositorioDeDocumento(sgdf);
        UUID id = repo.gravar(documento("c")).id();

        repo.gravarCampos(id, List.of(campo("valor", "119.301,51")));
        repo.gravarCampos(id, List.of(campo("valor", "119.301,52")));

        ok("Persistência . reprocessar com regra nova dá o valor novo, não dois",
                "119.301,52".equals(repo.camposDe(id).get("valor"))
                        && repo.camposDe(id).size() == 1);
    }

    static void veredictoEGravadoComMotivo(Sgdf sgdf) {
        UUID id = new RepositorioDeDocumento(sgdf).gravar(documento("d")).id();
        RepositorioDeValidacao repo = new RepositorioDeValidacao(sgdf);

        repo.gravar(id, new ResultadoDeValidacao("V3", Veredito.APROVADO, null,
                Map.of("compativeis", List.of("00681946000160"))), null);
        repo.gravar(id, new ResultadoDeValidacao("V8", Veredito.REPROVADO,
                "documento incompleto: 2 de 2 campo(s) essencial(is) não pôde(puderam) "
                        + "ser lido(s)",
                Map.of("ausentes", List.of("valor", "favorecido"))), null);

        Map<String, Veredito> vigentes = repo.vigentesDe(id);
        ok("Cap. 16 . os vereditos ficam gravados por código",
                vigentes.get("V3") == Veredito.APROVADO
                        && vigentes.get("V8") == Veredito.REPROVADO);
        ok("Cap. 16 . e o documento é reconhecido como reprovado", !repo.semReprovacao(id));

        String motivo = escalar(sgdf, "SELECT motivo FROM validacao_documento "
                + "WHERE documento_id = '" + id + "' AND codigo = 'V8'");
        ok("Cap. 12 . o motivo acionável é o que fica gravado", motivo.contains("2 de 2"));
    }

    /**
     * A restricao do banco e a rede de seguranca para o caso de um caminho de
     * codigo esquecer a checagem — a mesma razao da excecao_sod.
     */
    static void reprovacaoSemMotivoNaoEGravavel(Sgdf sgdf) {
        UUID id = new RepositorioDeDocumento(sgdf).gravar(documento("e")).id();
        boolean recusou = false;
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute("INSERT INTO validacao_documento (documento_id, codigo, resultado) "
                    + "VALUES ('" + id + "', 'V8', 'REPROVADO')");
        } catch (SQLException e) {
            recusou = true;
        }
        ok("Cap. 16 . o banco recusa reprovação sem motivo, mesmo por SQL direto", recusou);
    }

    static void historicoNaoESubstituido(Sgdf sgdf) {
        UUID id = new RepositorioDeDocumento(sgdf).gravar(documento("f")).id();
        RepositorioDeValidacao repo = new RepositorioDeValidacao(sgdf);

        repo.gravar(id, new ResultadoDeValidacao("V5", Veredito.REPROVADO,
                "a certidão vence antes da NF prevista", Map.of()), null);
        repo.gravar(id, new ResultadoDeValidacao("V5", Veredito.APROVADO, null, Map.of()), null);

        ok("Cap. 16 . reprocessar acrescenta, não substitui", repo.execucoesDe(id, "V5") == 2);
        ok("Cap. 16 . e o vigente é o mais recente",
                repo.vigentesDe(id).get("V5") == Veredito.APROVADO);
    }

    /** Cap. 9: o resultado e sempre gravado, inclusive quando conforme. */
    static void conciliacaoConformeTambemEGravada(Sgdf sgdf) {
        UUID cicloId = umCiclo(sgdf);
        UUID regraId = umaRegra(sgdf);
        RepositorioDeConciliacao repo = new RepositorioDeConciliacao(sgdf);

        repo.gravar(cicloId, regraId, new ResultadoDaConciliacao("R01",
                ResultadoDaConciliacao.Situacao.CONFORME,
                ResultadoDaConciliacao.Modo.BLOQUEIO,
                new BigDecimal("119301.51"), new BigDecimal("119301.51"),
                new BigDecimal("0.00"), null,
                Map.of("pareados", List.of("FGT.GUIA com comprovante em 2026-07-20"))));

        String itens = escalar(sgdf, "SELECT itens::text FROM conciliacao "
                + "WHERE ciclo_id = '" + cicloId + "'");
        ok("Cap. 9 . o conforme é gravado com os valores comparados",
                itens.contains("pareados") && itens.contains("2026-07-20"));

        String delta = escalar(sgdf, "SELECT delta::text FROM conciliacao "
                + "WHERE ciclo_id = '" + cicloId + "'");
        ok("Cap. 9 . e com o delta", "0.00".equals(delta));
        ok("Publicação . conforme não gera bloqueio", repo.bloqueiosDoCiclo(cicloId).isEmpty());

        repo.gravar(cicloId, regraId, new ResultadoDaConciliacao("R01",
                ResultadoDaConciliacao.Situacao.DIVERGENTE,
                ResultadoDaConciliacao.Modo.BLOQUEIO, null, null, null,
                "sem comprovante de pagamento pareado", Map.of()));
        List<String> bloqueios = repo.bloqueiosDoCiclo(cicloId);
        ok("Publicação . divergente em BLOQUEIO aparece como bloqueio",
                bloqueios.size() == 1 && bloqueios.get(0).contains("sem comprovante"));
    }

    /**
     * Achado A15: a extracao encontrou NUL no texto de um PDF com fonte
     * quebrada. PostgreSQL recusa NUL em text e em jsonb.
     */
    static void caractereDeControleNaoDerrubaAGravacao(Sgdf sgdf) {
        UUID id = new RepositorioDeDocumento(sgdf).gravar(documento("g")).id();
        String comControle = "texto com " + (char) 1 + " controle e \"aspas\"";
        new RepositorioDeValidacao(sgdf).gravar(id,
                new ResultadoDeValidacao("V2", Veredito.APROVADO, null,
                        Map.of("degradacao", List.of(comControle))), null);
        String detalhe = escalar(sgdf, "SELECT detalhe::text FROM validacao_documento "
                + "WHERE documento_id = '" + id + "'");
        ok("A15 . caractere de controle é escapado, não derruba a gravação",
                detalhe != null && detalhe.contains("aspas"));
    }

    /** Um documento meio gravado e pior que nenhum: quebra a auditoria. */
    static void transacaoDesfazTudoOuNada(Sgdf sgdf) {
        long antes = contar(sgdf, "documento");
        boolean falhou = false;
        try {
            sgdf.emTransacao(conexao -> {
                new RepositorioDeDocumento(new Sgdf(conexao)).gravar(documento("h"));
                throw new IllegalStateException("falha no meio do trabalho");
            });
        } catch (RuntimeException e) {
            falhou = true;
        }
        ok("Persistência . a falha propaga", falhou);
        ok("Persistência . e nada fica gravado pela metade", contar(sgdf, "documento") == antes);
    }

    /**
     * Republicar grava v(N+1); a linha da versao anterior fica. A tabela e
     * append-only por desenho: UNIQUE (ciclo_id, versao) e nenhuma atualizacao.
     */
    static void bookEVersionado(Sgdf sgdf) {
        UUID cicloId = umCiclo(sgdf);
        RepositorioDeBook repo = new RepositorioDeBook(sgdf);

        ok("F1-08 . o primeiro book do ciclo é a versão 1", repo.proximaVersao(cicloId) == 1);

        var pecas = List.of(new br.com.engesoftware.sgdf.book.Peca(1, "CER.CND_RFB",
                br.com.engesoftware.sgdf.book.Sigilo.PUBLICO_CLIENTE, "01_CER.CND_RFB.pdf",
                "a".repeat(64), "owncloud://x", java.time.OffsetDateTime.now(), false));
        var v1 = new br.com.engesoftware.sgdf.book.Book("CT-PERSIST", "2026-07", 1,
                java.time.OffsetDateTime.now(), MARCA, "1.0", "b".repeat(64), pecas,
                "books/CT-PERSIST/2026-07/v1/");

        repo.registrar(cicloId, v1, "{}", br.com.engesoftware.sgdf.book
                .ArmazenamentoImutavel.Retencao.semPrazo());
        ok("F1-08 . depois de publicar, a próxima é a 2", repo.proximaVersao(cicloId) == 2);

        boolean recusou = false;
        try {
            repo.registrar(cicloId, v1, "{}", br.com.engesoftware.sgdf.book
                    .ArmazenamentoImutavel.Retencao.semPrazo());
        } catch (Sgdf.FalhaDePersistencia e) {
            recusou = true;
        }
        ok("F1-08 . e a mesma versão não é gravada duas vezes", recusou);

        String modo = escalar(sgdf, "SELECT retencao_modo FROM book WHERE ciclo_id = '"
                + cicloId + "'");
        ok("A08 . o modo de retenção fica registrado", "LEGAL_HOLD".equals(modo));
    }

    // -------------------------------------------------------------------------

    static Documento documento(String semente) {
        String hash = String.format("%064x", new java.math.BigInteger(1, semente.getBytes()));
        return new Documento("OWNCLOUD", "/2026/07/" + semente + ".pdf", semente + ".pdf",
                hash, 1024, "application/pdf", "pdf", "PENDENTE", false,
                null, null, null, null, "etag-" + semente, MARCA);
    }

    static CampoExtraido campo(String nome, String valor) {
        return new CampoExtraido(nome, valor, 1.0,
                new RegiaoNoDocumento(1, List.of(new Retangulo(10, 20, 100, 12))));
    }

    static UUID umCiclo(Sgdf sgdf) {
        String n = String.format("%02d", ++sequenciaDoFixture);
        return uuid(sgdf, "WITH c AS ("
                + "  INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por)"
                + "  VALUES ('Teste Persistencia " + n + "', '000000000001" + n + "',"
                + "          'PRIVADA', false, '" + MARCA + "')"
                + "  RETURNING id),"
                // A modalidade tem código de enum fechado no banco: reusa a
                // que existe em vez de inventar uma que a restrição recusaria.
                + " m AS (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING' LIMIT 1),"
                + " v AS ("
                + "  INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + "  VALUES ('0.0-persist-" + n + "', '" + MARCA
                + "', 'fixture de teste') RETURNING id),"
                + " cs AS ("
                + "  INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id,"
                + "                                vigencia_ini, pasta_origem,"
                + "                                data_contratual_faturamento, calendario_uf,"
                + "                                ativo, criado_por)"
                + "  SELECT c.id, 'CT-PERSIST-" + n + "', 'Teste', m.id, DATE '2026-01-01',"
                + "         '/teste',"
                + "         '{\"ancora\": \"ATESTE\", \"offset\": 3,"
                + "           \"tipo_dia\": \"CORRIDO\"}'::jsonb, 'DF', false, '"
                + MARCA + "'"
                + "  FROM c, m RETURNING id)"
                + " INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + "                    versao_matriz_id, criado_por)"
                + " SELECT cs.id, '2026-07', 'ABERTO', v.id, '" + MARCA + "' FROM cs, v"
                + " RETURNING id");
    }

    static UUID umaRegra(Sgdf sgdf) {
        return uuid(sgdf, "INSERT INTO regra_conciliacao (codigo, nome, logica, tolerancia, "
                + "modo, fase, criado_por) VALUES ('R01-PERSIST', 'Teste', 'x', "
                + "'{\"absoluta\": 0.01}'::jsonb, 'BLOQUEIO', '1A', '" + MARCA + "') "
                + "RETURNING id");
    }

    static UUID uuid(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, UUID.class);
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao montar o fixture: "
                    + e.getMessage(), e);
        }
    }

    static String escalar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler " + sql, e);
        }
    }

    static long contar(Sgdf sgdf, String tabela) {
        return Long.parseLong(escalar(sgdf, "SELECT count(*) FROM " + tabela));
    }

    /** Remove tudo o que os testes criaram, na ordem das dependencias. */
    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM book WHERE ciclo_id IN "
                    + "(SELECT id FROM ciclo WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM conciliacao WHERE ciclo_id IN "
                    + "(SELECT id FROM ciclo WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM documento WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM regra_conciliacao WHERE criado_por = '" + MARCA + "'",
        };
        try (Statement st = conexao.createStatement()) {
            for (String c : comandos) {
                st.execute(c);
            }
        }
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

    private TestesDePersistencia() {}
}
