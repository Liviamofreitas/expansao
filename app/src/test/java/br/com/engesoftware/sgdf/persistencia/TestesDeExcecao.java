package br.com.engesoftware.sgdf.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fluxo de excecao com SoD — historia F0-09.
 *
 * <p>Criterio de aceite: <i>"solicitante nao consegue aprovar a propria excecao;
 * aprovacao exige APROVADOR_DAF"</i>. E a unica saida legitima de uma exigencia
 * bloqueante sem documento: sem ela, ou alguem edita o banco, ou o faturamento
 * trava.
 */
public final class TestesDeExcecao {

    static final String MARCA = "teste-excecao";
    static final String MOTIVO = "documento inexistente por decisao judicial nos autos 123/26";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

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
                executar("oSolicitanteNaoAprovaAPropria",
                        () -> oSolicitanteNaoAprovaAPropria(sgdf));
                executar("aprovarDispensaEFechaAPendencia",
                        () -> aprovarDispensaEFechaAPendencia(sgdf));
                executar("negarDeixaAExigenciaOndeEstava",
                        () -> negarDeixaAExigenciaOndeEstava(sgdf));
                executar("aEntregaQueChegaEnquantoAExcecaoEspera",
                        () -> aEntregaQueChegaEnquantoAExcecaoEspera(sgdf));
                executar("umaExcecaoEmAbertoPorExigencia",
                        () -> umaExcecaoEmAbertoPorExigencia(sgdf));
                executar("decidirDuasVezesERecusado", () -> decidirDuasVezesERecusado(sgdf));
                executar("naoSeDispensaOQueJaFoiEntregue",
                        () -> naoSeDispensaOQueJaFoiEntregue(sgdf));
                executar("aRecusaFicaNaTrilha", () -> aRecusaFicaNaTrilha(sgdf));
                executar("aFilaDoDafMostraOContexto", () -> aFilaDoDafMostraOContexto(sgdf));
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

    /** O criterio de aceite, literal. */
    static void oSolicitanteNaoAprovaAPropria(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeExcecao repo = new RepositorioDeExcecao(sgdf);
        UUID excecao = repo.solicitar(f.exigencia, MOTIVO, null, "joao.silva", "CURADOR_MATRIZ");

        boolean recusou = false;
        try {
            // Ele ATE tem o papel — o que ele nao tem e o direito sobre ESTA excecao.
            repo.aprovar(excecao, "joao.silva", "APROVADOR_DAF", true);
        } catch (RepositorioDeExcecao.SegregacaoDeFuncoes e) {
            recusou = true;
        }
        ok("F0-09 . o solicitante nao aprova a propria excecao, mesmo sendo DAF", recusou);
        ok("F0-09 . e a exigencia continua PENDENTE",
                "PENDENTE".equals(statusDa(sgdf, f.exigencia)));

        boolean semPapel = false;
        try {
            repo.aprovar(excecao, "maria.andrade", "PUBLICADOR_FIN", false);
        } catch (RepositorioDeExcecao.SegregacaoDeFuncoes e) {
            semPapel = true;
        }
        ok("F0-09 . e quem nao e APROVADOR_DAF tampouco decide", semPapel);

        RepositorioDeExcecao.Decidida d = repo.aprovar(excecao, "maria.andrade",
                "APROVADOR_DAF", true);
        ok("F0-09 . outro APROVADOR_DAF aprova", "APROVADA".equals(d.situacao()));
    }

    static void aprovarDispensaEFechaAPendencia(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeExcecao repo = new RepositorioDeExcecao(sgdf);
        UUID excecao = repo.solicitar(f.exigencia, MOTIVO, "processo 123/26", "joao.silva",
                "CURADOR_MATRIZ");
        RepositorioDeExcecao.Decidida d = repo.aprovar(excecao, "maria.andrade",
                "APROVADOR_DAF", true);

        ok("Cap. 6.1 . PENDENTE vai para DISPENSADO com a excecao aprovada",
                "DISPENSADO".equals(d.statusExigencia())
                        && "DISPENSADO".equals(statusDa(sgdf, f.exigencia)));
        ok("Cap. 5.2 . a pendencia fecha com resolucao EXCECAO",
                1 == contar(sgdf, "pendencia WHERE exigencia_id = '" + f.exigencia
                        + "' AND resolucao = 'EXCECAO' AND resolvida_em IS NOT NULL"));
        ok("F0-09 . e a evidencia fica gravada",
                "processo 123/26".equals(escalar(sgdf, "SELECT evidencia FROM excecao "
                        + "WHERE id = '" + excecao + "'")));
    }

    /**
     * Negar sem motivo devolveria o problema a quem solicitou sem nada que o
     * ajude: ou ele pede de novo igual, ou desiste de uma excecao legitima.
     */
    static void negarDeixaAExigenciaOndeEstava(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeExcecao repo = new RepositorioDeExcecao(sgdf);
        UUID excecao = repo.solicitar(f.exigencia, MOTIVO, null, "joao.silva",
                "CURADOR_MATRIZ");

        boolean semMotivo = false;
        try {
            repo.negar(excecao, "nao", "maria.andrade", "APROVADOR_DAF", true);
        } catch (RepositorioDeExcecao.ExcecaoInvalida e) {
            semMotivo = true;
        }
        ok("F0-09 . negar sem motivo substantivo e recusado", semMotivo);

        RepositorioDeExcecao.Decidida d = repo.negar(excecao,
                "a decisao judicial citada nao alcanca esta competencia", "maria.andrade",
                "APROVADOR_DAF", true);
        ok("F0-09 . negada e um estado de primeira classe", "NEGADA".equals(d.situacao()));
        ok("F0-09 . a exigencia continua PENDENTE",
                "PENDENTE".equals(statusDa(sgdf, f.exigencia)));
        ok("Cap. 5.2 . e a pendencia continua aberta",
                1 == contar(sgdf, "pendencia WHERE exigencia_id = '" + f.exigencia
                        + "' AND resolvida_em IS NULL"));
        ok("F0-09 . o motivo da negativa fica gravado, para quem pedir de novo",
                escalar(sgdf, "SELECT motivo_decisao FROM excecao WHERE id = '" + excecao
                        + "'").contains("competencia"));

        // Negada nao e o fim: pedir de novo, com o que faltava, e legitimo.
        UUID outra = repo.solicitar(f.exigencia, "nova solicitacao com a certidao de objeto e pe",
                null, "joao.silva", "CURADOR_MATRIZ");
        ok("F0-09 . depois de negada, cabe pedir de novo", outra != null);
    }

    /**
     * O ACHADO DESTA HISTORIA. A excecao espera decisao humana, e nesse meio-tempo
     * o documento pode chegar. Aprovar sobre um estado que ja nao e o atual
     * empurraria RECEBIDO para DISPENSADO — apagando do book um documento que
     * existe.
     */
    static void aEntregaQueChegaEnquantoAExcecaoEspera(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeExcecao repo = new RepositorioDeExcecao(sgdf);
        UUID excecao = repo.solicitar(f.exigencia, MOTIVO, null, "joao.silva",
                "CURADOR_MATRIZ");

        // A entrega chega enquanto o DAF ainda nao decidiu.
        executar(sgdf, "UPDATE exigencia SET status = 'RECEBIDO' WHERE id = '"
                + f.exigencia + "'");

        boolean recusou = false;
        String mensagem = "";
        try {
            repo.aprovar(excecao, "maria.andrade", "APROVADOR_DAF", true);
        } catch (RepositorioDeExcecao.ExcecaoInvalida e) {
            recusou = true;
            mensagem = e.getMessage();
        }
        ok("F0-09 . aprovar sobre exigencia ja atendida e recusado", recusou);
        ok("F0-09 . e a mensagem diz que a entrega chegou",
                mensagem.contains("a entrega chegou"));
        ok("F0-09 . a exigencia segue RECEBIDO — o documento nao foi apagado",
                "RECEBIDO".equals(statusDa(sgdf, f.exigencia)));
        ok("F0-09 . e a excecao continua em aberto, para ser negada ou cancelada",
                "SOLICITADA".equals(escalar(sgdf, "SELECT situacao FROM excecao WHERE id = '"
                        + excecao + "'")));
    }

    static void umaExcecaoEmAbertoPorExigencia(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeExcecao repo = new RepositorioDeExcecao(sgdf);
        repo.solicitar(f.exigencia, MOTIVO, null, "joao.silva", "CURADOR_MATRIZ");

        boolean recusou = false;
        try {
            repo.solicitar(f.exigencia, "outra solicitacao para a mesma exigencia aberta",
                    null, "outro.pedinte", "GESTOR_CONTRATO");
        } catch (RepositorioDeExcecao.ExcecaoInvalida e) {
            recusou = true;
        }
        ok("F0-09 . duas excecoes em aberto para a mesma exigencia sao recusadas", recusou);
    }

    static void decidirDuasVezesERecusado(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeExcecao repo = new RepositorioDeExcecao(sgdf);
        UUID excecao = repo.solicitar(f.exigencia, MOTIVO, null, "joao.silva",
                "CURADOR_MATRIZ");
        repo.aprovar(excecao, "maria.andrade", "APROVADOR_DAF", true);

        boolean recusou = false;
        try {
            repo.negar(excecao, "mudei de ideia depois de ja ter aprovado isto aqui",
                    "carlos.daf", "APROVADOR_DAF", true);
        } catch (RepositorioDeExcecao.ExcecaoJaDecidida e) {
            recusou = true;
        }
        ok("F0-09 . decidir de novo e recusado", recusou);
    }

    static void naoSeDispensaOQueJaFoiEntregue(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        executar(sgdf, "UPDATE exigencia SET status = 'PUBLICADO' WHERE id = '"
                + f.exigencia + "'");
        boolean recusou = false;
        try {
            new RepositorioDeExcecao(sgdf).solicitar(f.exigencia, MOTIVO, null, "joao.silva",
                    "CURADOR_MATRIZ");
        } catch (RepositorioDeExcecao.ExcecaoInvalida e) {
            recusou = true;
        }
        ok("Cap. 6.1 . nao se solicita excecao de exigencia ja publicada", recusou);
    }

    static void aRecusaFicaNaTrilha(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeExcecao repo = new RepositorioDeExcecao(sgdf);
        UUID excecao = repo.solicitar(f.exigencia, MOTIVO, null, "joao.silva",
                "CURADOR_MATRIZ");
        try {
            repo.aprovar(excecao, "joao.silva", "APROVADOR_DAF", true);
        } catch (RepositorioDeExcecao.SegregacaoDeFuncoes e) {
            // esperado
        }
        ok("Cap. 16 . a tentativa de aprovar a propria excecao fica na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'EXCECAO_DECIDIR'"
                        + " AND resultado = 'NEGADO' AND objeto_id = '" + excecao + "'"));
        ok("Cap. 16 . e a solicitacao tambem",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'EXCECAO_SOLICITAR'"
                        + " AND objeto_id = '" + excecao + "'"));
    }

    static void aFilaDoDafMostraOContexto(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID excecao = new RepositorioDeExcecao(sgdf).solicitar(f.exigencia, MOTIVO, null,
                "joao.silva", "CURADOR_MATRIZ");
        RepositorioDeExcecao.EmAberto item = new RepositorioDeExcecao(sgdf).emAberto(200)
                .stream().filter(e -> e.id().equals(excecao)).findFirst().orElseThrow();

        ok("F0-09 . a fila diz quem pediu", "joao.silva".equals(item.solicitante()));
        ok("F0-09 . e sobre o que — tipo, contrato e competencia",
                item.tipo().startsWith("TST.EXC") && item.contrato().startsWith("CT-EXC")
                        && "2026-04".equals(item.competencia()));
        ok("F0-09 . e em que estado a exigencia esta AGORA",
                "PENDENTE".equals(item.statusExigencia()));
    }

    // -------------------------------------------------------------------------

    record Fixture(int seq, UUID exigencia) {}

    static Fixture fixture(Sgdf sgdf) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Excecao " + n + "', '" + String.format("7%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-exc-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Excecao " + n + "', '" + String.format("8%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-EXC-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/teste', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        UUID ciclo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2026-04',"
                + " 'ABERTO', '" + versao + "', '" + MARCA + "') RETURNING id");
        UUID tipo = uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo,"
                + " evento, defasagem, criticidade, sigilo, criado_por) VALUES"
                + " ('TST.EXC" + n + "', 'Tipo', 'Teste', 'CONTRATO', 'MENSAL', 'M',"
                + " 'BLOQUEANTE', 'INTERNO', '" + MARCA + "') RETURNING id");
        UUID exigencia = uuid(sgdf, "INSERT INTO exigencia (ciclo_id, tipo_id, evento, status,"
                + " prazo_calculado, criticidade, responsavel, origem, criado_por) VALUES ('"
                + ciclo + "', '" + tipo + "', 'MENSAL', 'PENDENTE', DATE '2026-05-05',"
                + " 'BLOQUEANTE', 'AP', 'MATRIZ', '" + MARCA + "') RETURNING id");
        executar(sgdf, "INSERT INTO pendencia (exigencia_id, prazo) VALUES ('" + exigencia
                + "', DATE '2026-05-05')");
        return new Fixture(n, exigencia);
    }

    static String statusDa(Sgdf sgdf, UUID exigencia) {
        return escalar(sgdf, "SELECT status FROM exigencia WHERE id = '" + exigencia + "'");
    }

    static UUID uuid(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, UUID.class);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static void executar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static String escalar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao ler " + sql, e);
        }
    }

    static long contar(Sgdf sgdf, String de) {
        return Long.parseLong(escalar(sgdf, "SELECT count(*) FROM " + de));
    }

    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM excecao WHERE exigencia_id IN (SELECT id FROM exigencia"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM pendencia WHERE exigencia_id IN (SELECT id FROM exigencia"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM exigencia WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM tipo_documental WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM empresa WHERE criado_por = '" + MARCA + "'",
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

    private TestesDeExcecao() {}
}
