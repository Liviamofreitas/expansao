package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.orquestracao.Job;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestracao dos jobs — pendencia RA-07, a metade que e mecanismo.
 *
 * <p>Ligar os gatilhos continua sendo decisao de governanca, gated na F3-01. O
 * que se verifica aqui e que, quando forem ligados, dois nao rodam juntos, um
 * que morre nao some, e um agendador morto e NOTADO.
 *
 * <p><b>Vive no pacote do Agendador, e nao no de Job.</b> O lock e as suas
 * primitivas sao package-private de proposito: expor "tome este lock" numa API
 * publica convida a usa-lo fora do executar(), e e justamente o executar() que
 * garante o par tomar/liberar e o registro do desfecho. O teste entra pelo
 * pacote em vez de a producao abrir a porta.
 */
public final class TestesDeAgendador {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        executar("oCapitulo72NaoSeAgenda", TestesDeAgendador::oCapitulo72NaoSeAgenda);
        executar("cadaJobTemChaveDeLockPropria",
                TestesDeAgendador::cadaJobTemChaveDeLockPropria);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection a = DriverManager.getConnection(url);
                 Connection b = DriverManager.getConnection(url)) {
                a.setAutoCommit(true);
                b.setAutoCommit(true);
                limpar(a);
                try {
                    executar("rodarEZeroItensEUmFatoRegistrado",
                            () -> rodarEZeroItensEUmFatoRegistrado(new Sgdf(a)));
                    executar("oJobQueFalhaNaoSomeDaTrilha",
                            () -> oJobQueFalhaNaoSomeDaTrilha(new Sgdf(a)));
                    executar("duasInstanciasNaoRodamJuntas",
                            () -> duasInstanciasNaoRodamJuntas(new Sgdf(a), new Sgdf(b)));
                    executar("oLockMorreComASessao",
                            () -> oLockMorreComASessao(new Sgdf(a), url));
                    executar("oAgendadorMortoESilencioso",
                            () -> oAgendadorMortoESilencioso(new Sgdf(a)));
                    executar("oJobTravadoEOutroSintoma",
                            () -> oJobTravadoEOutroSintoma(new Sgdf(a)));
                    executar("oHistoricoSeparaOsDesfechos",
                            () -> oHistoricoSeparaOsDesfechos(new Sgdf(a)));
                } finally {
                    limpar(a);
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

    // --- sem banco -------------------------------------------------------------

    /**
     * "Executada quando a folha chega. NUNCA POR CALENDARIO." (cap. 7.2)
     *
     * <p>Derivar eventos por calendario materializaria exigencias de ferias, 13o
     * e rescisao para uma competencia cuja folha nao chegou — cobrando as areas
     * por eventos que talvez nao tenham acontecido.
     */
    static void oCapitulo72NaoSeAgenda() {
        ok("Cap. 7.2 . a derivacao de eventos nao e agendavel",
                !Job.DERIVACAO_DE_EVENTOS.agendavel()
                        && Job.Disparo.EVENTO == Job.DERIVACAO_DE_EVENTOS.disparo());
        ok("Cap. 7.2 . e nao tem cadencia esperada — nao ha 'deveria ter rodado' "
                        + "para quem so roda quando a folha chega",
                Job.DERIVACAO_DE_EVENTOS.cadenciaEsperada() == null);

        ok("RA-07 . todo job de calendario declara a cadencia esperada",
                java.util.Arrays.stream(Job.values()).filter(Job::agendavel)
                        .allMatch(j -> j.cadenciaEsperada() != null));
        ok("RA-07 . e todo job por evento NAO declara",
                java.util.Arrays.stream(Job.values()).filter(j -> !j.agendavel())
                        .allMatch(j -> j.cadenciaEsperada() == null));
        ok("RA-07 . texto desconhecido nao vira job",
                Job.de("VARREDURA_TOTAL") == null && Job.de(null) == null);
    }

    /**
     * Duas chaves iguais fariam dois jobs disputarem o mesmo lock.
     *
     * <p>Um deles nunca rodaria, e o sintoma seria CONCORRENTE eterno num job
     * que ninguem mais executa — um silencio que o alerta de silencio NAO pegaria,
     * porque haveria linhas.
     */
    static void cadaJobTemChaveDeLockPropria() {
        long distintas = java.util.Arrays.stream(Job.values())
                .mapToLong(Agendador::chaveDo).distinct().count();
        ok("RA-07 . cada job tem chave de lock propria",
                distintas == Job.values().length);
    }

    // --- com banco --------------------------------------------------------------

    /**
     * O ponto da V018 inteira.
     *
     * <p>Sem a linha, "rodou e nao achou nada" e "nao rodou" sao o mesmo estado
     * observavel — e o segundo deixa o painel verde por nada ter acontecido.
     */
    static void rodarEZeroItensEUmFatoRegistrado(Sgdf sgdf) {
        Agendador agendador = new Agendador(sgdf, "instancia-a");
        Agendador.Execucao e = agendador.executar(Job.VARREDURA_COMPLETA, () -> 0);

        ok("RA-07 . rodar e achar zero e SUCESSO, nao ausencia",
                e.sucesso() && e.itens() == 0);
        ok("RA-07 . e vira linha com a contagem, distinguivel de linha nenhuma",
                1 == contar(sgdf, "execucao_de_job WHERE job = 'VARREDURA_COMPLETA'"
                        + " AND resultado = 'SUCESSO' AND itens = 0"));
        ok("RA-07 . com quem executou",
                "instancia-a".equals(escalar(sgdf, "SELECT instancia FROM execucao_de_job"
                        + " WHERE job = 'VARREDURA_COMPLETA'")));

        boolean recusou = false;
        try {
            agendador.executar(Job.DERIVACAO_DE_EVENTOS, () -> 1);
        } catch (Agendador.JobNaoAgendavel ex) {
            recusou = true;
        }
        ok("Cap. 7.2 . e agendar a derivacao de eventos e recusado", recusou);
        ok("Cap. 7.2 . sem nem abrir linha para ela",
                0 == contar(sgdf, "execucao_de_job WHERE job = 'DERIVACAO_DE_EVENTOS'"));
    }

    /**
     * Engolir a falha faria o job parecer nunca ter acontecido.
     *
     * <p>E deixar a linha em aberto faria o alerta de travado apontar para uma
     * execucao que ja morreu.
     */
    static void oJobQueFalhaNaoSomeDaTrilha(Sgdf sgdf) {
        Agendador agendador = new Agendador(sgdf, "instancia-a");
        boolean repassou = false;
        try {
            agendador.executar(Job.CONCILIACAO, () -> {
                throw new IllegalStateException("o WebDAV nao respondeu");
            });
        } catch (IllegalStateException e) {
            repassou = true;
        }
        ok("RA-07 . a falha e repassada a quem chamou", repassou);
        ok("RA-07 . e fica registrada como FALHA, com o motivo",
                1 == contar(sgdf, "execucao_de_job WHERE job = 'CONCILIACAO'"
                        + " AND resultado = 'FALHA' AND detalhe LIKE '%WebDAV%'"));
        ok("RA-07 . a linha foi FECHADA — nao fica em aberto para sempre",
                0 == contar(sgdf, "execucao_de_job WHERE job = 'CONCILIACAO'"
                        + " AND terminada_em IS NULL"));

        // E o lock foi liberado: o proximo roda.
        Agendador.Execucao seguinte = agendador.executar(Job.CONCILIACAO, () -> 3);
        ok("RA-07 . e o lock foi liberado mesmo com a falha — o proximo roda",
                seguinte.sucesso() && seguinte.itens() == 3);
    }

    /** Duas conexoes de verdade: o lock e o que impede a varredura dupla. */
    static void duasInstanciasNaoRodamJuntas(Sgdf primeira, Sgdf segunda) {
        Agendador a = new Agendador(primeira, "instancia-a");
        Agendador b = new Agendador(segunda, "instancia-b");

        ok("RA-07 . a primeira instancia toma o lock", a.tentarLock(Job.VARREDURA_INCREMENTAL));
        Agendador.Execucao daSegunda = b.executar(Job.VARREDURA_INCREMENTAL, () -> {
            throw new AssertionError("a segunda instancia NAO deveria ter executado");
        });
        ok("RA-07 . e a segunda desiste sem executar o trabalho",
                "CONCORRENTE".equals(daSegunda.resultado()));
        ok("RA-07 . registrando que houve disputa — que e diferente de silencio",
                1 == contar(primeira, "execucao_de_job WHERE job = 'VARREDURA_INCREMENTAL'"
                        + " AND resultado = 'CONCORRENTE' AND instancia = 'instancia-b'"));

        a.liberarLock(Job.VARREDURA_INCREMENTAL);
        ok("RA-07 . liberado, a segunda passa a executar",
                b.executar(Job.VARREDURA_INCREMENTAL, () -> 7).itens() == 7);
    }

    /**
     * O motivo de ser lock consultivo e nao tabela com lease.
     *
     * <p>Uma tabela exigiria escolher um tempo de expiracao, e os dois lados da
     * escolha sao ruins: curto demais e o job lento perde o lock enquanto roda;
     * longo demais e uma instancia morta bloqueia o job pelo resto do lease.
     * Aqui a conexao cai e o PostgreSQL libera na hora.
     */
    static void oLockMorreComASessao(Sgdf sobrevivente, String url) throws SQLException {
        Connection efemera = DriverManager.getConnection(url);
        efemera.setAutoCommit(true);
        Agendador morre = new Agendador(new Sgdf(efemera), "instancia-que-morre");
        ok("RA-07 . a instancia efemera toma o lock", morre.tentarLock(Job.ABERTURA_DE_CICLOS));

        Agendador vivo = new Agendador(sobrevivente, "instancia-a");
        ok("RA-07 . e enquanto ela vive, a outra nao entra",
                !vivo.tentarLock(Job.ABERTURA_DE_CICLOS));

        efemera.close();
        // Sem lease, sem relogio, sem calibragem: a sessao caiu, o lock caiu.
        ok("RA-07 . morta a sessao, o lock e liberado pelo PostgreSQL sozinho",
                vivo.tentarLock(Job.ABERTURA_DE_CICLOS));
        vivo.liberarLock(Job.ABERTURA_DE_CICLOS);
    }

    /**
     * O unico jeito de um agendador morto ser notado.
     *
     * <p>Todos os outros sintomas da morte dele se parecem com saude: nenhum
     * documento novo, nenhuma pendencia nova, nenhum erro.
     */
    static void oAgendadorMortoESilencioso(Sgdf sgdf) {
        // MESA LIMPA, E ISSO NAO E ZELO.
        //
        // A primeira versao afirmava "sem execucao nenhuma" sobre uma tabela
        // que os testes anteriores ja tinham populado, e falhava por ordem de
        // execucao — nao por defeito. Um teste que depende de quem rodou antes
        // afirma sobre um estado que ele nao controla, e quando quebra manda
        // procurar no lugar errado.
        executar(sgdf, "DELETE FROM execucao_de_job");
        Agendador agendador = new Agendador(sgdf, "instancia-a");
        OffsetDateTime agora = OffsetDateTime.now();

        List<Agendador.Silencio> semNada = agendador.silenciosos(agora);
        ok("RA-07 . sem execucao nenhuma, TODO job de calendario e apontado",
                semNada.size() == (int) java.util.Arrays.stream(Job.values())
                        .filter(Job::agendavel).count());
        ok("RA-07 . e o motivo diz 'nunca executado' — o caso do cron que "
                        + "ninguem ligou, que uma consulta pela ultima execucao nao acharia",
                semNada.stream().allMatch(Agendador.Silencio::nuncaExecutou));
        ok("Cap. 7.2 . a derivacao de eventos NAO e apontada — nao ha cadencia "
                        + "que se espere dela",
                semNada.stream().noneMatch(s -> s.job() == Job.DERIVACAO_DE_EVENTOS));

        agendador.executar(Job.REGUA_DE_NOTIFICACAO, () -> 0);
        ok("RA-07 . executado agora, o job sai da lista de silenciosos",
                agendador.silenciosos(agora).stream()
                        .noneMatch(s -> s.job() == Job.REGUA_DE_NOTIFICACAO));

        // E volta quando a cadencia estoura.
        List<Agendador.Silencio> depois = agendador.silenciosos(
                agora.plus(Duration.ofHours(30)));
        Agendador.Silencio regua = depois.stream()
                .filter(s -> s.job() == Job.REGUA_DE_NOTIFICACAO).findFirst().orElse(null);
        ok("RA-07 . e volta quando passa da cadencia esperada",
                regua != null && !regua.nuncaExecutou());
        ok("RA-07 . dizendo ha quanto tempo foi o ultimo sucesso",
                regua.motivo().contains("último sucesso há"));
    }

    /** Travado e silencio sao sintomas diferentes, e a diferenca e acionavel. */
    static void oJobTravadoEOutroSintoma(Sgdf sgdf) {
        executar(sgdf, "INSERT INTO execucao_de_job (job, iniciada_em, instancia) VALUES"
                + " ('VARREDURA_COMPLETA', now() - INTERVAL '3 days', 'instancia-zumbi')");
        Agendador agendador = new Agendador(sgdf, "instancia-a");

        List<Agendador.Travado> travados = agendador.travados(OffsetDateTime.now());
        ok("RA-07 . a execucao aberta ha 3 dias e apontada como travada",
                travados.size() == 1 && "VARREDURA_COMPLETA".equals(travados.get(0).job()));
        ok("RA-07 . com a instancia que a abriu — com duas, e o que diz qual parou",
                "instancia-zumbi".equals(travados.get(0).instancia()));

        // Uma execucao recem-aberta NAO e travada: ela pode estar rodando.
        executar(sgdf, "INSERT INTO execucao_de_job (job, instancia) VALUES"
                + " ('CONCILIACAO', 'instancia-a')");
        ok("RA-07 . e a recem-aberta nao e — ela pode estar rodando agora",
                agendador.travados(OffsetDateTime.now()).size() == 1);
    }

    static void oHistoricoSeparaOsDesfechos(Sgdf sgdf) {
        executar(sgdf, "DELETE FROM execucao_de_job WHERE job = 'VARREDURA_COMPLETA'");
        Agendador agendador = new Agendador(sgdf, "instancia-a");
        agendador.executar(Job.VARREDURA_COMPLETA, () -> 12);
        agendador.executar(Job.VARREDURA_COMPLETA, () -> 0);
        try {
            agendador.executar(Job.VARREDURA_COMPLETA, () -> {
                throw new IllegalStateException("erro do teste");
            });
        } catch (IllegalStateException e) {
            // esperado
        }

        List<Agendador.Execucao> h = agendador.historico(Job.VARREDURA_COMPLETA, 10);
        ok("RA-07 . o historico traz os tres desfechos, do mais recente",
                h.size() == 3 && "FALHA".equals(h.get(0).resultado()));
        ok("RA-07 . com a contagem de cada sucesso, zero inclusive",
                h.get(1).itens() == 0 && h.get(2).itens() == 12);
        ok("RA-07 . e a execucao ainda em aberto nao entra no historico",
                h.stream().allMatch(e -> e.resultado() != null));
    }

    // -------------------------------------------------------------------------

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
        try (Statement st = conexao.createStatement()) {
            st.execute("DELETE FROM execucao_de_job");
            // Os locks sobrevivem a sessao enquanto ela vive; entre testes,
            // soltar tudo evita que um teste anterior segure o proximo.
            st.execute("SELECT pg_advisory_unlock_all()");
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

    private TestesDeAgendador() {}
}
