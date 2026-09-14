package br.com.engesoftware.sgdf.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Abertura automatica de ciclos — historia F0-07, cap. 7.1.
 *
 * <p>Criterio de aceite: <i>"no 1o dia util, 15 ciclos abertos; exigencia
 * corporativa unica compartilhada"</i>.
 *
 * <p>Nada criava a linha de ciclo: a materializacao sabia preencher um ciclo que
 * ja existisse, e ele so existia porque um fixture o inseria. Em producao o
 * sistema abriria zero ciclos, sem erro nenhum.
 */
public final class TestesDeAbertura {

    static final String MARCA = "teste-abertura";
    static final YearMonth COMPETENCIA = YearMonth.of(2030, 5);

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
                executar("abreEMaterializa", () -> abreEMaterializa(sgdf));
                executar("abrirDuasVezesNaoDuplica", () -> abrirDuasVezesNaoDuplica(sgdf));
                executar("contratoForaDeVigenciaNaoAbre",
                        () -> contratoForaDeVigenciaNaoAbre(sgdf));
                executar("aEntradaEaSaidaDoMesAbrem", () -> aEntradaEaSaidaDoMesAbrem(sgdf));
                executar("semMatrizRecusaEmVezDeAbrirVazio",
                        () -> semMatrizRecusaEmVezDeAbrirVazio(sgdf));
                executar("aVersaoEcongeladaNoCiclo", () -> aVersaoEcongeladaNoCiclo(sgdf));
                executar("oCicloVazioNaoFicaDePe", () -> oCicloVazioNaoFicaDePe(sgdf));
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

    static void abreEMaterializa(Sgdf sgdf) {
        UUID contrato = contrato(sgdf, "2029-01-01", null);
        AberturaDeCiclos.Abertura a = new AberturaDeCiclos(sgdf).abrir(COMPETENCIA, MARCA);

        // NÃO EXIGE completa(): os 12 contratos da carga real falham por
        // defeito de DADOS — regra ambígua e empresa emitente ausente (achados
        // § 50). Exigir completa() aqui faria o teste do motor falhar por causa
        // da carga, e mandaria procurar defeito onde não há.
        ok("F0-07 . o ciclo do contrato vigente e aberto",
                a.abertos().contains(cicloDe(sgdf, contrato)));
        ok("F0-07 . com a competencia corrente",
                1 == contar(sgdf, "ciclo WHERE contrato_servico_id = '" + contrato
                        + "' AND competencia = '2030-05'"));
        ok("F0-07 . e nasce ABERTO",
                "ABERTO".equals(escalar(sgdf, "SELECT status FROM ciclo WHERE"
                        + " contrato_servico_id = '" + contrato + "'")));
        ok("Cap. 16 . a abertura fica na trilha",
                contar(sgdf, "log_auditoria WHERE acao = 'CICLO_ABERTO' AND ator = '"
                        + MARCA + "'") >= 1);
    }

    /** O job roda todo dia; abrir de novo no mesmo mes nao pode duplicar. */
    static void abrirDuasVezesNaoDuplica(Sgdf sgdf) {
        UUID contrato = contrato(sgdf, "2029-01-01", null);
        AberturaDeCiclos abertura = new AberturaDeCiclos(sgdf);
        abertura.abrir(COMPETENCIA, MARCA);
        int antes = (int) contar(sgdf, "ciclo WHERE criado_por = '" + MARCA + "'");

        // NAO AFIRMA SOBRE completa(): os 12 contratos da carga real falham em
        // TODA execucao, por defeito de dados (achados § 50). Uma assercao
        // global aqui mediria a carga, nao a idempotencia — e mandaria procurar
        // defeito no motor.
        AberturaDeCiclos.Abertura segunda = abertura.abrir(COMPETENCIA, MARCA);
        ok("F0-07 . a segunda execucao nao reabre o ciclo deste contrato",
                !segunda.abertos().contains(cicloDe(sgdf, contrato)));
        ok("F0-07 . e nao duplica ciclo",
                antes == (int) contar(sgdf, "ciclo WHERE criado_por = '" + MARCA + "'"));
        ok("F0-07 . o ciclo continua la, unico",
                1 == contar(sgdf, "ciclo WHERE contrato_servico_id = '" + contrato + "'"));
        ok("F0-07 . e os contratos vigentes continuam sendo considerados",
                segunda.contratosVigentes() > 0);
    }

    /**
     * Abrir ciclo para contrato encerrado materializaria exigencias que ninguem
     * deve — e a area seria cobrada por documento de servico que nao presta mais.
     */
    static void contratoForaDeVigenciaNaoAbre(Sgdf sgdf) {
        UUID encerrado = contrato(sgdf, "2029-01-01", "2029-12-31");
        UUID futuro = contrato(sgdf, "2031-01-01", null);
        new AberturaDeCiclos(sgdf).abrir(COMPETENCIA, MARCA);

        ok("F0-07 . contrato encerrado antes da competencia nao abre",
                0 == contar(sgdf, "ciclo WHERE contrato_servico_id = '" + encerrado + "'"));
        ok("F0-07 . e contrato que so comeca depois tampouco",
                0 == contar(sgdf, "ciclo WHERE contrato_servico_id = '" + futuro + "'"));
    }

    /**
     * Vigente em QUALQUER dia da competencia, e nao no mes inteiro.
     *
     * <p>Um contrato que comeca no dia 20 ou termina no dia 10 prestou servico e
     * tem o que faturar — e e justamente na entrada e na saida que ha admissao e
     * rescisao a documentar.
     */
    static void aEntradaEaSaidaDoMesAbrem(Sgdf sgdf) {
        UUID entrou = contrato(sgdf, "2030-05-20", null);
        UUID saiu = contrato(sgdf, "2029-01-01", "2030-05-10");
        new AberturaDeCiclos(sgdf).abrir(COMPETENCIA, MARCA);

        ok("F0-07 . contrato que comeca no meio da competencia abre",
                1 == contar(sgdf, "ciclo WHERE contrato_servico_id = '" + entrou + "'"));
        ok("F0-07 . e o que termina no meio tambem — ha rescisao a documentar",
                1 == contar(sgdf, "ciclo WHERE contrato_servico_id = '" + saiu + "'"));
    }

    /**
     * Um ciclo sem exigencia aparece no painel como um ciclo COMPLETO.
     *
     * <p>O sistema estaria dizendo "nao falta nada" sobre uma competencia que
     * ele nao sabe conferir — o pior resultado possivel num sistema cuja funcao
     * e notar o que falta.
     */
    static void semMatrizRecusaEmVezDeAbrirVazio(Sgdf sgdf) {
        contrato(sgdf, "2029-01-01", null);
        // TRANSACAO DESFEITA, E NAO CIRURGIA EM CHAVE ESTRANGEIRA.
        //
        // A primeira versao derrubava a FK de ciclo para poder apagar as
        // versoes, e devolvia tudo no finally — um teste que altera o ESQUEMA
        // deixa o banco quebrado se morrer no meio, e o proximo teste falha por
        // um motivo que nao e o dele. Aqui nada persiste: o rollback devolve.
        boolean recusou = false;
        try {
            sgdf.conexao().setAutoCommit(false);
            // A ordem segue as FKs: ciclo e regra apontam para versao_matriz.
            // Tudo desfeito no rollback — nada disto persiste.
            executar(sgdf, "DELETE FROM pendencia");
            executar(sgdf, "DELETE FROM exigencia");
            executar(sgdf, "DELETE FROM ciclo");
            executar(sgdf, "DELETE FROM regra_exigibilidade");
            executar(sgdf, "DELETE FROM versao_matriz");
            try {
                new AberturaDeCiclos(sgdf).abrir(COMPETENCIA, MARCA);
            } catch (AberturaDeCiclos.SemMatrizPublicada e) {
                recusou = true;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                sgdf.conexao().rollback();
                sgdf.conexao().setAutoCommit(true);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        ok("F0-07 . sem matriz publicada a abertura RECUSA, em vez de criar ciclos "
                + "vazios que parecem completos", recusou);
    }

    /** Cap. 7.1, passo 4: reprocessamento usa a regra da epoca, nunca a atual. */
    static void aVersaoEcongeladaNoCiclo(Sgdf sgdf) {
        UUID contrato = contrato(sgdf, "2029-01-01", null);
        String corrente = escalar(sgdf, "SELECT id::text FROM versao_matriz"
                + " ORDER BY publicada_em DESC LIMIT 1");
        new AberturaDeCiclos(sgdf).abrir(COMPETENCIA, MARCA);

        ok("Cap. 7.1 . a versao corrente fica congelada no ciclo",
                corrente.equals(escalar(sgdf, "SELECT versao_matriz_id::text FROM ciclo"
                        + " WHERE contrato_servico_id = '" + contrato + "'")));
    }

    /**
     * O achado que ligar a abertura contra a carga real revelou.
     *
     * <p>Os 12 contratos abriam e materializavam ZERO — as 303 regras da carga
     * sao todas de alvo CONTRATO, e onde ha regra ela vem duplicada (17 pares
     * ambiguos) ou o contrato nao tem empresa emitente (todos os 12). O motor
     * estava certo em recusar; o que faltava era recusar o RESULTADO VAZIO.
     *
     * <p>Um ciclo sem exigencia aparece no painel como um ciclo COMPLETO:
     * nenhuma pendencia, nenhum bloqueio, nada faltando. O sistema estaria
     * dizendo "nao falta nada" sobre uma competencia que ele nao conseguiu
     * conferir — o pior resultado possivel num sistema cuja funcao e notar o
     * que falta.
     */
    static void oCicloVazioNaoFicaDePe(Sgdf sgdf) {
        UUID semRegra = contratoSemRegra(sgdf);
        AberturaDeCiclos.Abertura a = new AberturaDeCiclos(sgdf).abrir(COMPETENCIA, MARCA);

        ok("F0-07 . o contrato sem regra NAO entra nos abertos",
                cicloDe(sgdf, semRegra) == null);
        ok("F0-07 . e o ciclo vazio foi DESFEITO — um ciclo sem exigencia aparece "
                        + "no painel como um ciclo completo",
                0 == contar(sgdf, "ciclo WHERE contrato_servico_id = '" + semRegra + "'"));
        ok("F0-07 . a falha e nomeada, e diz por que",
                a.falhas().stream().anyMatch(f -> f.contains(semRegra.toString())
                        && f.contains("sem exigência nenhuma")));
        ok("F0-07 . e o lote se declara incompleto", !a.completa());
    }

    // -------------------------------------------------------------------------

    /** Contrato sem regra propria — a carga real nao tem regra de modalidade. */
    static UUID contratoSemRegra(Sgdf sgdf) {
        UUID contrato = contrato(sgdf, "2029-01-01", null);
        executar(sgdf, "DELETE FROM regra_exigibilidade WHERE alvo_contrato_id = '"
                + contrato + "'");
        return contrato;
    }

    static UUID cicloDe(Sgdf sgdf, UUID contrato) {
        String id = escalar(sgdf, "SELECT id::text FROM ciclo WHERE contrato_servico_id = '"
                + contrato + "'");
        return id == null ? null : UUID.fromString(id);
    }

    static UUID contrato(Sgdf sgdf, String vigenciaIni, String vigenciaFim) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Ab " + n + "', '" + String.format("6%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Ab " + n + "', '" + String.format("7%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, vigencia_fim, pasta_origem,"
                + " data_contratual_faturamento, calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-AB-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '" + vigenciaIni + "', "
                + (vigenciaFim == null ? "NULL" : "DATE '" + vigenciaFim + "'")
                + ", '/f', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\",\"offset\":3}'::jsonb,"
                + " 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        // UMA REGRA PRÓPRIA, PORQUE A CARGA NÃO TEM REGRA DE MODALIDADE.
        //
        // As 303 regras da carga real são TODAS de alvo CONTRATO: um contrato
        // novo materializa zero até alguém escrever regras para ele. Sem esta
        // linha o fixture abriria um ciclo vazio — que a própria abertura desfaz,
        // e o teste mediria o contrário do que diz medir.
        executar(sgdf, "INSERT INTO regra_exigibilidade (versao_matriz_id, tipo_id, alvo,"
                + " alvo_contrato_id, obrigatoriedade, criticidade, prazo, responsavel_titular,"
                + " vigencia_ini, criado_por)"
                + " SELECT v.id, t.id, 'CONTRATO', '" + contrato + "', 'OBRIGATORIO',"
                + " 'BLOQUEANTE', '{\"ancora\":\"INICIO_COMPETENCIA\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":21}'::jsonb, 'OPERACAO', DATE '2020-01-01', '" + MARCA + "'"
                + " FROM (SELECT id FROM versao_matriz ORDER BY publicada_em DESC LIMIT 1) v,"
                + " tipo_documental t WHERE t.codigo = 'CER.CND_RFB'");
        return contrato;
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
            throw new IllegalStateException(e.getMessage(), e);
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
        // A ORDEM SEGUE AS CHAVES ESTRANGEIRAS, E A EXIGÊNCIA CORPORATIVA É
        // COMPARTILHADA: ela aponta para a EMPRESA, não só para o ciclo. Apagar
        // por ciclo deixaria as corporativas presas à empresa do fixture.
        String[] comandos = {
            "DELETE FROM pendencia WHERE exigencia_id IN (SELECT e.id FROM exigencia e"
                    + " LEFT JOIN ciclo c ON c.id = e.ciclo_id"
                    + " WHERE c.criado_por = '" + MARCA + "' OR e.empresa_id IN"
                    + " (SELECT id FROM empresa WHERE criado_por = '" + MARCA + "'))",
            "DELETE FROM exigencia WHERE ciclo_id IN (SELECT id FROM ciclo"
                    + " WHERE criado_por = '" + MARCA + "')"
                    + " OR empresa_id IN (SELECT id FROM empresa WHERE criado_por = '"
                    + MARCA + "')",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM regra_exigibilidade WHERE criado_por = '" + MARCA + "'",
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

    private TestesDeAbertura() {}
}
