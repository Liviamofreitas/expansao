package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.coleta.PoliticaDeArquivos;
import br.com.engesoftware.sgdf.coleta.ResultadoVarredura;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Painel de organizacao e conflitos de sincronizacao — historia F1-10.
 *
 * <p>Criterio de aceite: <i>"copia em conflito aparece sinalizada e nunca
 * vinculada"</i>. As duas metades sao verificadas de formas diferentes: a
 * sinalizacao por comportamento; o "nunca vinculada" por ESTRUTURA — o achado
 * nao esta em {@code documento}, e so documento pode ser vinculado.
 */
public final class TestesDeOrganizacao {

    static final String MARCA = "teste-organizacao";

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
                executar("aCopiaRedundanteApontaParaOOriginal",
                        () -> aCopiaRedundanteApontaParaOOriginal(sgdf));
                executar("aCopiaComConteudoProprioPedeAtencao",
                        () -> aCopiaComConteudoProprioPedeAtencao(sgdf));
                executar("aCopiaNuncaViraDocumento", () -> aCopiaNuncaViraDocumento(sgdf));
                executar("varrerDeNovoNaoRepeteOAchado",
                        () -> varrerDeNovoNaoRepeteOAchado(sgdf));
                executar("oPainelERecortadoPorContrato",
                        () -> oPainelERecortadoPorContrato(sgdf));
                executar("resolverTiraDoPainelSemApagar",
                        () -> resolverTiraDoPainelSemApagar(sgdf));
                executar("naoSeAfirmaDuplicataSemHash", () -> naoSeAfirmaDuplicataSemHash(sgdf));
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

    /** Hash conhecido: a copia e redundante, e o alerta diz de qual documento. */
    static void aCopiaRedundanteApontaParaOOriginal(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        String hash = hash(f.seq);
        UUID original = umDocumento(sgdf, "GUIA_FGTS.pdf", hash);

        ResultadoVarredura r = new ResultadoVarredura();
        r.conflitos.add(new ResultadoVarredura.Conflito(
                "/c" + f.seq + "/GUIA_FGTS (conflicted copy 2026-06-30).pdf", "etag1", hash,
                1024));

        RepositorioDeOrganizacao.Registro registro =
                new RepositorioDeOrganizacao(sgdf).registrar(f.contrato, "2026-06", r);

        ok("F1-10 . a copia de conflito e registrada", registro.conflitos() == 1);
        ok("Cap. 8.1 . com hash conhecido, nao pede atencao — e redundancia",
                registro.comConteudoProprio() == 0);
        ok("Cap. 8.1 . o achado aponta para o documento ORIGINAL",
                original.toString().equals(escalar(sgdf, "SELECT documento_original_id::text"
                        + " FROM achado_de_organizacao WHERE contrato_servico_id = '"
                        + f.contrato + "'")));
        ok("F1-10 . e a severidade e INFORMATIVO",
                "INFORMATIVO".equals(escalar(sgdf, "SELECT severidade FROM"
                        + " achado_de_organizacao WHERE contrato_servico_id = '"
                        + f.contrato + "'")));
    }

    /**
     * O CASO QUE JUSTIFICA BAIXAR O ARQUIVO. Se a sincronizacao substituiu o
     * original por uma copia vazia ou antiga, a "copia em conflito" e a unica
     * versao que sobrou — e descarta-la pelo nome a perderia em silencio.
     */
    static void aCopiaComConteudoProprioPedeAtencao(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        ResultadoVarredura r = new ResultadoVarredura();
        r.conflitos.add(new ResultadoVarredura.Conflito(
                "/c" + f.seq + "/RELATORIO (conflicted copy 2026-06-30).pdf", "etag1",
                hash(900 + f.seq), 2048));

        RepositorioDeOrganizacao.Registro registro =
                new RepositorioDeOrganizacao(sgdf).registrar(f.contrato, "2026-06", r);

        ok("F1-10 . conteudo inedito conta separado — e o numero que alguem olha hoje",
                registro.comConteudoProprio() == 1);
        ok("Cap. 8.1 . e a severidade sobe para ATENCAO",
                "ATENCAO".equals(escalar(sgdf, "SELECT severidade FROM achado_de_organizacao"
                        + " WHERE contrato_servico_id = '" + f.contrato + "'")));
        // Trecho sem acento de proposito: este arquivo e ASCII, e comparar
        // acentuacao aqui testaria a codificacao do fonte, nao a mensagem.
        ok("F1-10 . o detalhe diz o que fazer antes de apagar",
                escalar(sgdf, "SELECT detalhe FROM achado_de_organizacao"
                        + " WHERE contrato_servico_id = '" + f.contrato + "'")
                        .contains("conferir antes de apagar"));
        ok("Cap. 8.1 . e nao ha original a apontar",
                null == escalar(sgdf, "SELECT documento_original_id::text FROM"
                        + " achado_de_organizacao WHERE contrato_servico_id = '"
                        + f.contrato + "'"));
    }

    /**
     * "NUNCA VINCULADA" COMO PROPRIEDADE ESTRUTURAL.
     *
     * <p>vinculo_exigencia_documento referencia documento. Se o achado nao e um
     * documento, nao existe consulta que possa vincula-lo por engano — nao ha
     * filtro para alguem esquecer.
     */
    static void aCopiaNuncaViraDocumento(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        String hash = hash(500 + f.seq);
        ResultadoVarredura r = new ResultadoVarredura();
        r.conflitos.add(new ResultadoVarredura.Conflito(
                "/c" + f.seq + "/NF (conflicted copy).pdf", "etag1", hash, 512));
        new RepositorioDeOrganizacao(sgdf).registrar(f.contrato, "2026-06", r);

        ok("F1-10 . a copia NAO entra em documento",
                0 == contar(sgdf, "documento WHERE hash_sha256 = '" + hash + "'"));
        ok("F1-10 . e portanto nao pode ser vinculada — nao ha o que referenciar",
                0 == contar(sgdf, "vinculo_exigencia_documento v JOIN documento d"
                        + " ON d.id = v.documento_id WHERE d.hash_sha256 = '" + hash + "'"));
        ok("F1-10 . mas aparece sinalizada",
                1 == contar(sgdf, "achado_de_organizacao WHERE hash_sha256 = '" + hash + "'"));
    }

    /** A varredura passa de 30 em 30 minutos. */
    static void varrerDeNovoNaoRepeteOAchado(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        ResultadoVarredura r = new ResultadoVarredura();
        r.conflitos.add(new ResultadoVarredura.Conflito(
                "/c" + f.seq + "/X (conflicted copy).pdf", "etag1", hash(700 + f.seq), 100));
        RepositorioDeOrganizacao repo = new RepositorioDeOrganizacao(sgdf);
        repo.registrar(f.contrato, "2026-06", r);
        RepositorioDeOrganizacao.Registro segunda = repo.registrar(f.contrato, "2026-06", r);

        ok("F1-10 . a segunda varredura nao cria um achado novo", segunda.conflitos() == 0);
        ok("F1-10 . e o painel continua com um so",
                1 == contar(sgdf, "achado_de_organizacao WHERE contrato_servico_id = '"
                        + f.contrato + "'"));
    }

    static void oPainelERecortadoPorContrato(Sgdf sgdf) {
        Fixture meu = fixture(sgdf);
        Fixture alheio = fixture(sgdf);
        RepositorioDeOrganizacao repo = new RepositorioDeOrganizacao(sgdf);
        for (Fixture f : List.of(meu, alheio)) {
            ResultadoVarredura r = new ResultadoVarredura();
            r.conflitos.add(new ResultadoVarredura.Conflito(
                    "/c" + f.seq + "/Y (conflicted copy).pdf", "e", hash(300 + f.seq), 10));
            repo.registrar(f.contrato, "2026-06", r);
        }

        List<RepositorioDeOrganizacao.Achado> so = repo.abertos(Set.of(meu.contrato), 50);
        ok("F1-10 . o painel do meu contrato traz o meu achado", so.size() == 1);
        ok("F1-10 . e nao o do contrato alheio",
                so.get(0).caminho().contains("/c" + meu.seq + "/"));
        ok("Cap. 15.1 . recorte vazio nao vira visao global",
                repo.abertos(Set.of(), 50).isEmpty());
        ok("F1-10 . e o item traz o contrato e a competencia",
                so.get(0).contrato().startsWith("CT-ORG") && "2026-06".equals(
                        so.get(0).competencia()));
    }

    static void resolverTiraDoPainelSemApagar(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        ResultadoVarredura r = new ResultadoVarredura();
        r.conflitos.add(new ResultadoVarredura.Conflito(
                "/c" + f.seq + "/Z (conflicted copy).pdf", "e", hash(400 + f.seq), 10));
        RepositorioDeOrganizacao repo = new RepositorioDeOrganizacao(sgdf);
        repo.registrar(f.contrato, "2026-06", r);
        UUID achado = repo.abertos(Set.of(f.contrato), 50).get(0).id();

        repo.resolver(achado, "quem.arrumou");
        ok("F1-10 . resolvido sai do painel", repo.abertos(Set.of(f.contrato), 50).isEmpty());
        ok("F1-10 . mas a linha fica, com quem resolveu",
                "quem.arrumou".equals(escalar(sgdf, "SELECT resolvido_por FROM"
                        + " achado_de_organizacao WHERE id = '" + achado + "'")));
        ok("Cap. 16 . e a resolucao fica na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'ACHADO_RESOLVER'"
                        + " AND objeto_id = '" + achado + "'"));
    }

    /**
     * O que NAO e baixado nao tem hash, e sem hash nao se afirma duplicata: a
     * unica coisa que se sabe e que o nome parecia de temporario.
     */
    static void naoSeAfirmaDuplicataSemHash(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        ResultadoVarredura r = new ResultadoVarredura();
        r.ignorados.add(new ResultadoVarredura.Ignorado("/c" + f.seq + "/~$planilha.xlsx",
                PoliticaDeArquivos.Motivo.ARQUIVO_TEMPORARIO, "~$planilha.xlsx"));
        RepositorioDeOrganizacao.Registro registro =
                new RepositorioDeOrganizacao(sgdf).registrar(f.contrato, "2026-06", r);

        ok("Cap. 8.1 . o temporario tambem e sinalizado — nada some em silencio",
                registro.outros() == 1);
        ok("F1-10 . sem hash e sem original: nao se afirmou nada sobre o conteudo",
                1 == contar(sgdf, "achado_de_organizacao WHERE contrato_servico_id = '"
                        + f.contrato + "' AND hash_sha256 IS NULL"
                        + " AND documento_original_id IS NULL"));

        boolean recusou = false;
        try {
            executar(sgdf, "INSERT INTO achado_de_organizacao (origem, caminho, nome_arquivo,"
                    + " motivo, severidade, documento_original_id) VALUES ('OWNCLOUD',"
                    + " '/c" + f.seq + "/sem-hash.pdf', 'sem-hash.pdf', 'COPIA_DE_CONFLITO',"
                    + " 'INFORMATIVO', (SELECT id FROM documento LIMIT 1))");
        } catch (RuntimeException e) {
            recusou = true;
        }
        ok("Cap. 8.1 . o banco recusa afirmar duplicata sem hash", recusou);
    }

    // -------------------------------------------------------------------------

    record Fixture(int seq, UUID contrato) {}

    static Fixture fixture(Sgdf sgdf) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Org " + n + "', '" + String.format("3%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Org " + n + "', '" + String.format("4%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-ORG-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/c" + n + "', '{\"ancora\":\"ATESTE\","
                + "\"tipo_dia\":\"CORRIDO\",\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '"
                + MARCA + "' FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        return new Fixture(n, contrato);
    }

    static UUID umDocumento(Sgdf sgdf, String nome, String hash) {
        return uuid(sgdf, "INSERT INTO documento (origem, caminho, nome_arquivo, hash_sha256,"
                + " tamanho, mime_real, formato, status_triagem, criado_por) VALUES"
                + " ('OWNCLOUD', '/orig', '" + nome + "', '" + hash + "', 1024,"
                + " 'application/pdf', 'pdf', 'CONFIRMADO', '" + MARCA + "') RETURNING id");
    }

    static String hash(int n) {
        return String.format("%064x", java.math.BigInteger.valueOf(1_000_003L * (n + 1)));
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
            throw new IllegalStateException("recusado: " + e.getMessage(), e);
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
            "DELETE FROM achado_de_organizacao WHERE contrato_servico_id IN"
                    + " (SELECT id FROM contrato_servico WHERE criado_por = '" + MARCA + "')"
                    + " OR caminho LIKE '/c%'",
            "DELETE FROM documento WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
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

    private TestesDeOrganizacao() {}
}
