package br.com.engesoftware.sgdf.limite;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeBloqueio;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rate limiting e bloqueio por tentativa repetida — SEC-06.
 *
 * <p>Criterio de verificacao do cap. 15.2: <i>"teste de forca bruta"</i>.
 *
 * <p><b>O SGDF nao autentica.</b> Quem valida senha e aplica MFA e o IdP
 * (cap. 14.4), entao "lockout" aqui nao e sobre senha: e sobre um ator JA
 * AUTENTICADO que insiste em pedir o que nao pode.
 */
public final class TestesDeLimite {

    static final String MARCA = "teste-limite";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("aJanelaNaoTemVirada", TestesDeLimite::aJanelaNaoTemVirada);
        executar("oBarradoNaoEmpurraALiberacao", TestesDeLimite::oBarradoNaoEmpurraALiberacao);
        executar("aEsperaNuncaMandaTentarAgora", TestesDeLimite::aEsperaNuncaMandaTentarAgora);
        executar("escritaEMaisApertadoQueLeitura",
                TestesDeLimite::escritaEMaisApertadoQueLeitura);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("dezNegativasBloqueiam", () -> dezNegativasBloqueiam(sgdf));
                    executar("insistirNaoProlongaOBloqueio",
                            () -> insistirNaoProlongaOBloqueio(sgdf));
                    executar("oBloqueioTemFim", () -> oBloqueioTemFim(sgdf));
                    executar("liberarEAtoAuditavel", () -> liberarEAtoAuditavel(sgdf));
                    executar("umAtorNaoBloqueiaOutro", () -> umAtorNaoBloqueiaOutro(sgdf));
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

    // --- a janela, sem banco -----------------------------------------------------

    /**
     * O defeito classico do balde fixo: o dobro do limite na virada.
     *
     * <p>Um contador que zera de minuto em minuto aceita 100 no segundo 59 e
     * mais 100 no segundo 61 — pico real de 200 em dois segundos, com teto
     * declarado de 100. A janela deslizante mede sempre os ultimos N segundos, e
     * por isso nao tem virada.
     */
    static void aJanelaNaoTemVirada() {
        JanelaDeslizante j = new JanelaDeslizante(Duration.ofMinutes(1), 5);
        Instant t = Instant.parse("2026-09-14T10:00:59Z");
        for (int i = 0; i < 5; i++) {
            j.cabe(t);
        }
        ok("SEC-06 . o limite e atingido", !j.cabe(t));

        // Dois segundos depois — em balde fixo, seria outro minuto e tudo
        // recomecaria.
        Instant depois = t.plusSeconds(2);
        ok("SEC-06 . dois segundos depois NAO libera: a janela nao virou",
                !j.cabe(depois));
        ok("SEC-06 . e a contagem continua cheia", j.quantidade(depois) == 5);

        // So depois de a janela inteira passar.
        Instant umMinutoDepois = t.plusSeconds(61);
        ok("SEC-06 . passado o minuto inteiro, libera", j.cabe(umMinutoDepois));
    }

    /**
     * Quem insiste durante o bloqueio nao se prende para sempre.
     *
     * <p>Contar a tentativa barrada empurraria a liberacao a cada retry — e um
     * cliente com retry automatico se auto-prenderia indefinidamente. O bloqueio
     * pune o excesso, nao a insistencia.
     */
    static void oBarradoNaoEmpurraALiberacao() {
        JanelaDeslizante j = new JanelaDeslizante(Duration.ofSeconds(10), 2);
        Instant t = Instant.parse("2026-09-14T10:00:00Z");
        j.cabe(t);
        j.cabe(t);

        // Cinco tentativas barradas ao longo do bloqueio.
        for (int i = 1; i <= 5; i++) {
            j.cabe(t.plusSeconds(i));
        }
        ok("SEC-06 . as tentativas barradas nao entram na contagem",
                j.quantidade(t.plusSeconds(5)) == 2);
        ok("SEC-06 . e a liberacao acontece 10 s apos o PRIMEIRO evento, nao apos "
                        + "a ultima tentativa",
                j.cabe(t.plusSeconds(11)));
    }

    /** Um Retry-After de zero manda tentar agora, e a tentativa e barrada de novo. */
    static void aEsperaNuncaMandaTentarAgora() {
        JanelaDeslizante j = new JanelaDeslizante(Duration.ofSeconds(10), 1);
        Instant t = Instant.parse("2026-09-14T10:00:00Z");
        j.cabe(t);

        ok("SEC-06 . com a janela cheia, a espera e positiva",
                j.esperaAte(t).getSeconds() > 0);
        ok("SEC-06 . e no limite exato da expiracao tambem — nunca zero",
                j.esperaAte(t.plusSeconds(9)).getSeconds() >= 1);
        ok("SEC-06 . com a janela vazia, nao ha espera",
                new JanelaDeslizante(Duration.ofSeconds(10), 1)
                        .esperaAte(t).isZero());

        boolean recusouLimiteZero = false;
        try {
            new JanelaDeslizante(Duration.ofSeconds(1), 0);
        } catch (IllegalArgumentException e) {
            recusouLimiteZero = true;
        }
        ok("SEC-06 . limite zero e recusado — barraria tambem quem tem direito",
                recusouLimiteZero);
    }

    /** Nao existe fluxo humano que decida trinta triagens em um minuto. */
    static void escritaEMaisApertadoQueLeitura() {
        ok("SEC-06 . o teto de escrita e menor que o de leitura",
                PoliticaDeUso.ESCRITAS_POR_MINUTO < PoliticaDeUso.PEDIDOS_POR_MINUTO);
        ok("SEC-06 . POST conta como escrita", PoliticaDeUso.escrita("POST"));
        ok("SEC-06 . GET nao", !PoliticaDeUso.escrita("GET"));
        ok("SEC-06 . metodo desconhecido conta como escrita — o lado seguro",
                PoliticaDeUso.escrita("PROPFIND") && PoliticaDeUso.escrita(null));
        ok("SEC-06 . a limitacao do contador em memoria esta declarada",
                PoliticaDeUso.LIMITACAO_CONHECIDA.contains("por instância"));
    }

    // --- o bloqueio, com banco ---------------------------------------------------

    static void dezNegativasBloqueiam(Sgdf sgdf) {
        String ator = ator();
        RepositorioDeBloqueio repo = new RepositorioDeBloqueio(sgdf);

        for (int i = 1; i < PoliticaDeUso.NEGATIVAS_ANTES_DO_BLOQUEIO; i++) {
            var b = repo.contarNegativaE(ator, "PUBLICADOR_FIN", "GET /api/ciclos/" + i);
            if (b.isPresent()) {
                ok("SEC-06 . bloqueou cedo demais, na tentativa " + i, false);
                return;
            }
        }
        ok("SEC-06 . nove negativas nao bloqueiam — um 403 legitimo e comum, e "
                        + "punir o uso desatento faria a operacao pedir para desligar",
                repo.ativo(ator).isEmpty());

        var bloqueio = repo.contarNegativaE(ator, "PUBLICADOR_FIN", "GET /api/ciclos/10");
        ok("SEC-06 . a decima bloqueia", bloqueio.isPresent());
        ok("SEC-06 . e o motivo diz quantas foram e em que janela",
                bloqueio.get().motivo().contains("10 negativas")
                        && bloqueio.get().motivo().contains("15 minutos"));
        ok("SEC-06 . o bloqueio fica ativo", repo.ativo(ator).isPresent());
        ok("Cap. 16 . e entra na trilha como NEGADO",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'ATOR_BLOQUEADO'"
                        + " AND ator = '" + ator + "' AND resultado = 'NEGADO'"));
    }

    /**
     * Cada tentativa durante o bloqueio NAO empurra a expiracao.
     *
     * <p>Sem o ON CONFLICT DO NOTHING, um cliente com retry automatico se
     * prenderia indefinidamente — e o sintoma seria um bloqueio de quinze
     * minutos que dura horas, sem ninguem entender por que.
     */
    static void insistirNaoProlongaOBloqueio(Sgdf sgdf) {
        String ator = ator();
        RepositorioDeBloqueio repo = new RepositorioDeBloqueio(sgdf);
        for (int i = 0; i < PoliticaDeUso.NEGATIVAS_ANTES_DO_BLOQUEIO; i++) {
            repo.contarNegativaE(ator, "PUBLICADOR_FIN", "GET /api/ciclos/" + i);
        }
        OffsetDateTime expiraPrimeiro = repo.ativo(ator).orElseThrow().expiraEm();

        for (int i = 0; i < 5; i++) {
            repo.contarNegativaE(ator, "PUBLICADOR_FIN", "GET /api/ciclos/insiste" + i);
        }
        ok("SEC-06 . insistir nao empurra a expiracao",
                expiraPrimeiro.equals(repo.ativo(ator).orElseThrow().expiraEm()));
        ok("SEC-06 . e nao cria um segundo bloqueio ativo",
                1 == contar(sgdf, "bloqueio_de_ator WHERE ator = '" + ator
                        + "' AND liberado_em IS NULL"));
    }

    /**
     * Um bloqueio permanente vira chamado de suporte, e chamado repetido vira um
     * bypass que alguem cria e ninguem remove.
     */
    static void oBloqueioTemFim(Sgdf sgdf) {
        String ator = ator();
        RepositorioDeBloqueio repo = new RepositorioDeBloqueio(sgdf);
        for (int i = 0; i < PoliticaDeUso.NEGATIVAS_ANTES_DO_BLOQUEIO; i++) {
            repo.contarNegativaE(ator, "PUBLICADOR_FIN", "GET /api/x" + i);
        }
        ok("SEC-06 . bloqueado", repo.ativo(ator).isPresent());
        ok("SEC-06 . com expiracao no futuro",
                repo.ativo(ator).orElseThrow().expiraEm().isAfter(OffsetDateTime.now()));

        // ENVELHECE OS DOIS CAMPOS, E A RESTRIÇÃO ME OBRIGOU A ISSO.
        //
        // A primeira versao movia so o expira_em para o passado, e o banco
        // recusou: expira_em > bloqueado_em. A restricao estava certa e o
        // fixture errado — um bloqueio que expira antes de comecar nao e um
        // bloqueio vencido, e um teste que o cria mede um estado impossivel.
        executar(sgdf, "UPDATE bloqueio_de_ator SET bloqueado_em = now() - INTERVAL '1 hour',"
                + " expira_em = now() - INTERVAL '1 minute' WHERE ator = '" + ator + "'");
        ok("SEC-06 . expirado deixa de ser ativo SEM depender de faxina — um "
                        + "bloqueio que precisa de job para acabar dura para sempre quando "
                        + "o job morre",
                repo.ativo(ator).isEmpty());

        boolean recusouSemFim = false;
        try {
            executar(sgdf, "INSERT INTO bloqueio_de_ator (ator, expira_em, negativas, motivo)"
                    + " VALUES ('eterno', now() - INTERVAL '1 day', 10, 'sem fim')");
        } catch (IllegalStateException e) {
            recusouSemFim = e.getMessage().contains("bloqueio_expira_depois");
        }
        ok("SEC-06 . e o banco recusa um bloqueio que nao expire depois de comecar",
                recusouSemFim);
    }

    /** O bloqueio pode ser engano: papel mal mapeado produz negativas legitimas. */
    static void liberarEAtoAuditavel(Sgdf sgdf) {
        String ator = ator();
        RepositorioDeBloqueio repo = new RepositorioDeBloqueio(sgdf);
        for (int i = 0; i < PoliticaDeUso.NEGATIVAS_ANTES_DO_BLOQUEIO; i++) {
            repo.contarNegativaE(ator, "PUBLICADOR_FIN", "GET /api/y" + i);
        }

        ok("SEC-06 . a liberacao funciona",
                repo.liberar(ator, "admin.tec", "ADMIN_SISTEMA"));
        ok("SEC-06 . e o ator volta a passar", repo.ativo(ator).isEmpty());
        ok("Cap. 16 . com quem liberou na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'BLOQUEIO_LIBERAR'"
                        + " AND ator = 'admin.tec' AND objeto_id = '" + ator + "'"));
        ok("SEC-06 . liberar o que nao esta bloqueado devolve false, nao erro",
                !repo.liberar(ator, "admin.tec", "ADMIN_SISTEMA"));
    }

    /** Bloquear por IP tiraria o escritorio inteiro do ar por causa de uma pessoa. */
    static void umAtorNaoBloqueiaOutro(Sgdf sgdf) {
        String sondador = ator();
        String inocente = ator();
        RepositorioDeBloqueio repo = new RepositorioDeBloqueio(sgdf);
        for (int i = 0; i < PoliticaDeUso.NEGATIVAS_ANTES_DO_BLOQUEIO; i++) {
            repo.contarNegativaE(sondador, "PUBLICADOR_FIN", "GET /api/z" + i);
        }

        ok("SEC-06 . o sondador e bloqueado", repo.ativo(sondador).isPresent());
        ok("SEC-06 . e quem nao fez nada continua passando",
                repo.ativo(inocente).isEmpty());
        ok("SEC-06 . o painel de operacao lista so os ativos",
                repo.ativos().stream().anyMatch(b -> b.ator().equals(sondador))
                        && repo.ativos().stream().noneMatch(b -> b.ator().equals(inocente)));
    }

    // -------------------------------------------------------------------------

    static String ator() {
        return MARCA + "-" + (++sequencia);
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
        try (Statement st = conexao.createStatement()) {
            st.execute("DELETE FROM bloqueio_de_ator WHERE ator LIKE '" + MARCA + "%'"
                    + " OR ator = 'eterno'");
            st.execute("ALTER TABLE log_auditoria DISABLE RULE log_auditoria_sem_delete");
            st.execute("DELETE FROM log_auditoria WHERE ator LIKE '" + MARCA + "%'"
                    + " OR objeto_id LIKE '" + MARCA + "%'");
            st.execute("ALTER TABLE log_auditoria ENABLE RULE log_auditoria_sem_delete");
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

    private TestesDeLimite() {}
}
