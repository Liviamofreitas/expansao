package br.com.engesoftware.sgdf.matriz;

import br.com.engesoftware.sgdf.persistencia.RepositorioDaMatriz;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeRascunho;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rascunho e publicacao da matriz — historia F0-05.
 *
 * <p>Criterio de aceite: <i>"alterar regra nao afeta ciclo aberto; historico
 * lista versoes com autor e motivo"</i>. A primeira metade ja e provada em
 * {@code TestesDeMatriz}, do lado de quem LE a matriz; aqui prova-se do lado de
 * quem a ESCREVE — publicar nao toca na versao base.
 */
public final class TestesDeRascunho {

    static final String MARCA = "teste-rascunho";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("reescreverIgualNaoPedeAprovacao",
                TestesDeRascunho::reescreverIgualNaoPedeAprovacao);
        executar("removerBloqueanteTambemPedeDaf",
                TestesDeRascunho::removerBloqueanteTambemPedeDaf);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte de banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("publicarCriaVersaoESemMexerNaBase",
                            () -> publicarCriaVersaoESemMexerNaBase(sgdf));
                    executar("bloqueanteExigeDaf", () -> bloqueanteExigeDaf(sgdf));
                    executar("escreverOHerdadoNaoEMudanca",
                            () -> escreverOHerdadoNaoEMudanca(sgdf));
                    executar("mudarSoOPrazoNaoExigeDaf", () -> mudarSoOPrazoNaoExigeDaf(sgdf));
                    executar("publicarDuasVezesERecusado",
                            () -> publicarDuasVezesERecusado(sgdf));
                    executar("numeroRepetidoERecusado", () -> numeroRepetidoERecusado(sgdf));
                    executar("oHistoricoTemAutorEMotivo", () -> oHistoricoTemAutorEMotivo(sgdf));
                    executar("aNegativaFicaNaTrilha", () -> aNegativaFicaNaTrilha(sgdf));
                } finally {
                    limpar(conexao);
                }
            }
        }

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    // --- a regra do gate, sem banco ------------------------------------------

    static void reescreverIgualNaoPedeAprovacao() {
        List<MudancaDeCriticidade.Item> iguais = List.of(
                new MudancaDeCriticidade.Item("ALTERAR", "CER.CND", "BLOQUEANTE", "BLOQUEANTE"));
        ok("Cap. 12 . reescrever a mesma criticidade nao pede DAF",
                MudancaDeCriticidade.exigemAprovacaoDaf(iguais).isEmpty());

        List<MudancaDeCriticidade.Item> naoBloqueante = List.of(
                new MudancaDeCriticidade.Item("ALTERAR", "OPE.DOC", "NAO_BLOQUEANTE",
                        "NAO_BLOQUEANTE"));
        ok("Cap. 12 . mexer em regra que nunca foi bloqueante tampouco",
                MudancaDeCriticidade.exigemAprovacaoDaf(naoBloqueante).isEmpty());

        List<MudancaDeCriticidade.Item> virou = List.of(
                new MudancaDeCriticidade.Item("ALTERAR", "OPE.DOC", "NAO_BLOQUEANTE",
                        "BLOQUEANTE"));
        ok("Cap. 12 . tornar bloqueante pede DAF",
                MudancaDeCriticidade.exigemAprovacaoDaf(virou).size() == 1);
        ok("Cap. 12 . e a mensagem diz o que muda",
                MudancaDeCriticidade.exigemAprovacaoDaf(virou).get(0)
                        .contains("NAO_BLOQUEANTE → BLOQUEANTE"));
    }

    /**
     * A leitura conservadora, declarada: acrescentar exigencia bloqueante aperta
     * o portao; remover uma afrouxa. A que deixa o faturamento passar sem
     * documento e a segunda.
     */
    static void removerBloqueanteTambemPedeDaf() {
        List<MudancaDeCriticidade.Item> remove = List.of(
                new MudancaDeCriticidade.Item("REMOVER", "CER.CND", "BLOQUEANTE", null));
        ok("Cap. 12 . REMOVER regra bloqueante pede DAF — afrouxar e a mudanca perigosa",
                MudancaDeCriticidade.exigemAprovacaoDaf(remove).size() == 1);

        List<MudancaDeCriticidade.Item> deixaDeSer = List.of(
                new MudancaDeCriticidade.Item("ALTERAR", "CER.CND", "BLOQUEANTE",
                        "NAO_BLOQUEANTE"));
        ok("Cap. 12 . deixar de ser bloqueante tambem",
                MudancaDeCriticidade.exigemAprovacaoDaf(deixaDeSer).size() == 1);

        List<MudancaDeCriticidade.Item> incluiSemBloquear = List.of(
                new MudancaDeCriticidade.Item("INCLUIR", "OPE.DOC", null, "NAO_BLOQUEANTE"));
        ok("Cap. 12 . incluir regra nao bloqueante nao pede DAF",
                MudancaDeCriticidade.exigemAprovacaoDaf(incluiSemBloquear).isEmpty());
    }

    // --- contra o banco ------------------------------------------------------

    static void publicarCriaVersaoESemMexerNaBase(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        long regrasDaBase = contar(sgdf, "regra_exigibilidade WHERE versao_matriz_id = '"
                + f.base + "'");

        UUID rascunho = abrirRascunho(sgdf, f, "9.1-" + f.seq);
        // Altera o prazo de UMA das tres regras; as outras duas sao copiadas.
        acrescentarAlteracao(sgdf, rascunho, f.regraContrato, f.tipoContrato, 20, null);

        RepositorioDeRascunho repo = new RepositorioDeRascunho(sgdf);
        RepositorioDeRascunho.Analise analise = repo.analisar(rascunho);
        ok("F0-05 . a analise mostra o ciclo aberto que fica na versao antiga",
                analise.ciclosAbertosNaBase().size() == 1);
        ok("F0-05 . e nao exige DAF, porque so o prazo muda",
                analise.exigemDaf().isEmpty());

        RepositorioDeRascunho.Publicada p = repo.publicar(rascunho, "curador",
                "CURADOR_MATRIZ", false);

        ok("F0-05 . a versao nova tem a matriz COMPLETA, nao so o delta",
                p.regras() == regrasDaBase);
        ok("F0-05 . a versao base fica intacta",
                regrasDaBase == contar(sgdf, "regra_exigibilidade WHERE versao_matriz_id = '"
                        + f.base + "'"));
        ok("F0-05 . a regra alterada aparece com o prazo novo na versao nova",
                "20".equals(escalar(sgdf, "SELECT r.prazo->>'offset' FROM regra_exigibilidade r"
                        + " JOIN tipo_documental t ON t.id = r.tipo_id"
                        + " WHERE r.versao_matriz_id = '" + p.versaoId() + "'"
                        + " AND t.id = '" + f.tipoContrato + "'")));
        ok("F0-05 . e com o prazo antigo na base",
                "5".equals(escalar(sgdf, "SELECT r.prazo->>'offset' FROM regra_exigibilidade r"
                        + " WHERE r.versao_matriz_id = '" + f.base + "'"
                        + " AND r.tipo_id = '" + f.tipoContrato + "'")));
        ok("Cap. 12 . a publicacao nomeia quem NAO foi afetado",
                p.naoAfetados().size() == 1 && p.naoAfetados().get(0).contains("2026-04"));

        // E a prova do outro lado: rematerializar o ciclo antigo continua dando
        // o prazo da base.
        executar(sgdf, "DELETE FROM pendencia WHERE exigencia_id IN (SELECT id FROM exigencia"
                + " WHERE criado_por = 'materializa')");
        executar(sgdf, "DELETE FROM exigencia WHERE criado_por = 'materializa'");
        new RepositorioDaMatriz(sgdf).materializar(f.ciclo, "materializa");
        ok("F0-05 . o ciclo aberto rematerializa com o prazo da versao que congelou",
                "2026-04-08".equals(escalar(sgdf, "SELECT max(prazo_calculado)::text"
                        + " FROM exigencia WHERE criado_por = 'materializa'")));
    }

    /**
     * Note qual regra este teste altera. A primeira versao dele mexia na regra
     * cujo TIPO ja e bloqueante e escrevia "BLOQUEANTE" explicitamente — e a
     * analise, corretamente, nao pediu DAF: a criticidade efetiva era bloqueante
     * antes e continuava depois. Escrever explicitamente o que ja valia por
     * heranca nao e mudanca. Para exercitar o portao e preciso mudar de fato,
     * partindo de uma regra NAO bloqueante.
     */
    static void bloqueanteExigeDaf(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID rascunho = abrirRascunho(sgdf, f, "9.2-" + f.seq);
        acrescentarAlteracao(sgdf, rascunho, f.regraNaoBloqueante, f.tipoNaoBloqueante, 5,
                "BLOQUEANTE");

        RepositorioDeRascunho repo = new RepositorioDeRascunho(sgdf);
        ok("Cap. 12 . a analise avisa antes do clique",
                repo.analisar(rascunho).exigemDaf().size() == 1);

        boolean recusou = false;
        try {
            repo.publicar(rascunho, "curador", "CURADOR_MATRIZ", false);
        } catch (RepositorioDeRascunho.ExigeAprovacaoDaf e) {
            recusou = true;
        }
        ok("Cap. 12 . o CURADOR_MATRIZ nao publica criticidade bloqueante", recusou);
        ok("F0-05 . e nenhuma versao foi criada",
                0 == contar(sgdf, "versao_matriz WHERE numero = '9.2-" + f.seq + "'"));

        RepositorioDeRascunho.Publicada p = repo.publicar(rascunho, "daf", "APROVADOR_DAF", true);
        ok("Cap. 15.1 . o APROVADOR_DAF publica", p.versaoId() != null);
    }

    /**
     * A heranca de criticidade (V001: nula na regra = herda do tipo) faz com que
     * escrever explicitamente o valor herdado NAO seja mudanca. E o caso que
     * derrubou a primeira versao do teste acima.
     */
    static void escreverOHerdadoNaoEMudanca(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID rascunho = abrirRascunho(sgdf, f, "9.8-" + f.seq);
        // A regra tem criticidade NULA e o tipo e BLOQUEANTE: efetiva = BLOQUEANTE.
        acrescentarAlteracao(sgdf, rascunho, f.regraContrato, f.tipoContrato, 5, "BLOQUEANTE");
        RepositorioDeRascunho.Analise analise = new RepositorioDeRascunho(sgdf)
                .analisar(rascunho);
        ok("Cap. 12 . tornar explicito o BLOQUEANTE herdado do tipo nao pede DAF",
                analise.exigemDaf().isEmpty());
        ok("F0-05 . e o item continua no rascunho — ele muda o prazo", analise.itens() == 1);
    }

    static void mudarSoOPrazoNaoExigeDaf(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID rascunho = abrirRascunho(sgdf, f, "9.3-" + f.seq);
        // A regra ja e NAO_BLOQUEANTE por heranca do tipo; muda so o prazo.
        acrescentarAlteracao(sgdf, rascunho, f.regraNaoBloqueante, f.tipoNaoBloqueante, 9, null);
        RepositorioDeRascunho.Publicada p = new RepositorioDeRascunho(sgdf)
                .publicar(rascunho, "curador", "CURADOR_MATRIZ", false);
        ok("Cap. 12 . mudanca que nao toca bloqueante publica sem DAF", p.versaoId() != null);
    }

    static void publicarDuasVezesERecusado(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID rascunho = abrirRascunho(sgdf, f, "9.4-" + f.seq);
        acrescentarAlteracao(sgdf, rascunho, f.regraContrato, f.tipoContrato, 7, null);
        RepositorioDeRascunho repo = new RepositorioDeRascunho(sgdf);
        repo.publicar(rascunho, "curador", "CURADOR_MATRIZ", false);

        boolean recusou = false;
        try {
            repo.publicar(rascunho, "curador", "CURADOR_MATRIZ", false);
        } catch (RepositorioDeRascunho.RascunhoJaFechado e) {
            recusou = true;
        }
        ok("F0-05 . publicar o mesmo rascunho duas vezes e recusado", recusou);
        ok("F0-05 . e so existe uma versao para ele",
                1 == contar(sgdf, "rascunho_matriz WHERE id = '" + rascunho
                        + "' AND versao_publicada_id IS NOT NULL"));
    }

    static void numeroRepetidoERecusado(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID primeiro = abrirRascunho(sgdf, f, "9.5-" + f.seq);
        acrescentarAlteracao(sgdf, primeiro, f.regraContrato, f.tipoContrato, 7, null);
        RepositorioDeRascunho repo = new RepositorioDeRascunho(sgdf);
        repo.publicar(primeiro, "curador", "CURADOR_MATRIZ", false);

        UUID segundo = abrirRascunho(sgdf, f, "9.5-" + f.seq);
        acrescentarAlteracao(sgdf, segundo, f.regraContrato, f.tipoContrato, 8, null);
        boolean recusou = false;
        try {
            repo.publicar(segundo, "curador", "CURADOR_MATRIZ", false);
        } catch (RepositorioDeRascunho.NumeroJaUsado e) {
            recusou = true;
        }
        ok("F0-05 . numero de versao repetido e recusado", recusou);
    }

    static void oHistoricoTemAutorEMotivo(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID rascunho = abrirRascunho(sgdf, f, "9.6-" + f.seq);
        acrescentarAlteracao(sgdf, rascunho, f.regraContrato, f.tipoContrato, 12, null);
        new RepositorioDeRascunho(sgdf).publicar(rascunho, "curador", "CURADOR_MATRIZ", false);

        List<RepositorioDeRascunho.VersaoNoHistorico> historico =
                new RepositorioDeRascunho(sgdf).historico(50);
        RepositorioDeRascunho.VersaoNoHistorico nova = historico.stream()
                .filter(v -> ("9.6-" + f.seq).equals(v.numero())).findFirst().orElseThrow();

        ok("F0-05 . o historico lista a versao com autor", "curador".equals(nova.publicadaPor()));
        ok("F0-05 . e com motivo", nova.motivo().contains("teste"));
        ok("F0-05 . e diz quantas regras a versao tem", nova.regras() == 3);
        ok("F0-05 . e quem propos o rascunho, que pode nao ser quem publicou",
                MARCA.equals(nova.propostaPor()));
        ok("F0-05 . a versao base aparece com o ciclo que a congelou",
                historico.stream().anyMatch(v -> v.id().equals(f.base) && v.ciclos() == 1));
    }

    static void aNegativaFicaNaTrilha(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID rascunho = abrirRascunho(sgdf, f, "9.7-" + f.seq);
        acrescentarAlteracao(sgdf, rascunho, f.regraNaoBloqueante, f.tipoNaoBloqueante, 5,
                "BLOQUEANTE");
        try {
            new RepositorioDeRascunho(sgdf).publicar(rascunho, "curador", "CURADOR_MATRIZ",
                    false);
        } catch (RepositorioDeRascunho.ExigeAprovacaoDaf e) {
            // esperado
        }
        ok("Cap. 16 . a tentativa negada fica na trilha — e o que mostra alguem insistindo",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'MATRIZ_PUBLICAR'"
                        + " AND resultado = 'NEGADO' AND objeto_id = '" + rascunho + "'"));
    }

    // --- fixture -------------------------------------------------------------

    record Fixture(int seq, UUID base, UUID ciclo, UUID contrato, UUID tipoContrato,
                   UUID tipoNaoBloqueante, UUID regraContrato, UUID regraNaoBloqueante) {}

    static Fixture fixture(Sgdf sgdf) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Rascunho " + n + "', '" + String.format("4%013d", n)
                + "', '" + MARCA + "') RETURNING id");
        UUID base = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('9.0-base-" + n + "', '" + MARCA + "', 'versao base do fixture')"
                + " RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Rascunho " + n + "', '" + String.format("3%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-RASC-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/teste', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + " \"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        UUID ciclo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2026-04',"
                + " 'ABERTO', '" + base + "', '" + MARCA + "') RETURNING id");

        UUID tipoCorp = umTipo(sgdf, "TST.RAS_CORP" + n, "CORPORATIVO", "BLOQUEANTE");
        UUID tipoContrato = umTipo(sgdf, "TST.RAS_CT" + n, "CONTRATO", "BLOQUEANTE");
        UUID tipoLeve = umTipo(sgdf, "TST.RAS_LEVE" + n, "CONTRATO", "NAO_BLOQUEANTE");

        UUID regraCorp = umaRegra(sgdf, tipoCorp, base, 5);
        UUID regraContrato = umaRegra(sgdf, tipoContrato, base, 5);
        UUID regraLeve = umaRegra(sgdf, tipoLeve, base, 5);
        if (regraCorp == null) {
            throw new IllegalStateException("fixture sem regra corporativa");
        }

        executar(sgdf, "INSERT INTO calendario_feriados (uf, data, descricao, criado_por)"
                + " VALUES (NULL, DATE '2026-04-03', 'Sexta-Feira Santa', '" + MARCA + "')"
                + " ON CONFLICT DO NOTHING");

        return new Fixture(n, base, ciclo, contrato, tipoContrato, tipoLeve, regraContrato,
                regraLeve);
    }

    static UUID umTipo(Sgdf sgdf, String codigo, String escopo, String criticidade) {
        return uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento,"
                + " defasagem, criticidade, sigilo, criado_por) VALUES ('" + codigo + "',"
                + " 'Tipo', 'Teste', '" + escopo + "', 'MENSAL', 'M', '" + criticidade + "',"
                + " 'INTERNO', '" + MARCA + "') RETURNING id");
    }

    /** Criticidade NULA de proposito: a regra herda a do tipo (V001). */
    static UUID umaRegra(Sgdf sgdf, UUID tipo, UUID versao, int offset) {
        return uuid(sgdf, "INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id,"
                + " obrigatoriedade, prazo, responsavel_titular, vigencia_ini,"
                + " versao_matriz_id, criado_por)"
                + " SELECT '" + tipo + "', 'MODALIDADE', m.id, 'OBRIGATORIO',"
                + " '{\"ancora\":\"INICIO_COMPETENCIA\",\"tipo_dia\":\"UTIL\",\"offset\":"
                + offset + "}'::jsonb, 'FINANCEIRO', DATE '2025-01-01', '" + versao + "', '"
                + MARCA + "' FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
    }

    static UUID abrirRascunho(Sgdf sgdf, Fixture f, String numero) {
        return uuid(sgdf, "INSERT INTO rascunho_matriz (numero_proposto, motivo, base_versao_id,"
                + " criado_por) VALUES ('" + numero + "', 'proposta de teste do rascunho da"
                + " matriz', '" + f.base + "', '" + MARCA + "') RETURNING id");
    }

    static void acrescentarAlteracao(Sgdf sgdf, UUID rascunho, UUID regraOrigem, UUID tipo,
                                     int offset, String criticidade) {
        executar(sgdf, "INSERT INTO rascunho_regra (rascunho_id, acao, regra_origem_id, tipo_id,"
                + " alvo, alvo_modalidade_id, obrigatoriedade, criticidade, prazo,"
                + " responsavel_titular, vigencia_ini, criado_por)"
                + " SELECT '" + rascunho + "', 'ALTERAR', '" + regraOrigem + "', '" + tipo + "',"
                + " 'MODALIDADE', m.id, 'OBRIGATORIO', "
                + (criticidade == null ? "NULL" : "'" + criticidade + "'") + ","
                + " '{\"ancora\":\"INICIO_COMPETENCIA\",\"tipo_dia\":\"UTIL\",\"offset\":"
                + offset + "}'::jsonb, 'FINANCEIRO', DATE '2025-01-01', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING'");
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
            "DELETE FROM pendencia WHERE exigencia_id IN (SELECT id FROM exigencia"
                    + " WHERE criado_por IN ('" + MARCA + "', 'materializa'))",
            "DELETE FROM exigencia WHERE criado_por IN ('" + MARCA + "', 'materializa')",
            "DELETE FROM rascunho_regra WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM regra_exigibilidade WHERE criado_por IN ('" + MARCA + "', 'curador',"
                    + " 'daf')",
            "UPDATE rascunho_matriz SET versao_publicada_id = NULL, situacao = 'ABERTO',"
                    + " publicado_em = NULL, publicado_por = NULL WHERE criado_por = '"
                    + MARCA + "'",
            "DELETE FROM rascunho_matriz WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por IN ('" + MARCA + "', 'curador',"
                    + " 'daf')",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM tipo_documental WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM empresa WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM calendario_feriados WHERE criado_por = '" + MARCA + "'",
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

    private TestesDeRascunho() {}
}
