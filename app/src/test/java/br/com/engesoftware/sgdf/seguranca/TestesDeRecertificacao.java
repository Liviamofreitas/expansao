package br.com.engesoftware.sgdf.seguranca;

import br.com.engesoftware.sgdf.persistencia.ConsultaDeRecertificacao;
import br.com.engesoftware.sgdf.persistencia.RegistroDeAcesso;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import br.com.engesoftware.sgdf.persistencia.TrilhaDeAuditoria;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Recertificacao trimestral de acessos — historia F3-06, requisito SEC-10.
 *
 * <p>Criterio de aceite: <i>"relatorio gerado e enviado a DAF"</i>. SEC-10 pede
 * <i>"recertificacao trimestral de acessos por contrato"</i>.
 */
public final class TestesDeRecertificacao {

    static final String MARCA = "teste-recert";
    static final LocalDate DESDE = LocalDate.of(2027, 1, 1);
    static final LocalDate ATE = LocalDate.of(2027, 3, 31);
    static final LocalDate DENTRO = LocalDate.of(2027, 2, 10);

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("aAtencaoNaoViraRuido", TestesDeRecertificacao::aAtencaoNaoViraRuido);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("oAuditorApareceNaPropriaRecertificacao",
                            () -> oAuditorApareceNaPropriaRecertificacao(sgdf));
                    executar("oPrivilegioNaoExercidoEApontado",
                            () -> oPrivilegioNaoExercidoEApontado(sgdf));
                    executar("aDerivaDeConcessaoAparece",
                            () -> aDerivaDeConcessaoAparece(sgdf));
                    executar("aOrdemDoArrayNaoInventaDeriva",
                            () -> aOrdemDoArrayNaoInventaDeriva(sgdf));
                    executar("oEscopoDeContratoEntraNoRelatorio",
                            () -> oEscopoDeContratoEntraNoRelatorio(sgdf));
                    executar("oPeriodoRecorta", () -> oPeriodoRecorta(sgdf));
                    executar("oCsvComecaPelaRessalva", () -> oCsvComecaPelaRessalva(sgdf));
                    executar("observarNaoDerrubaARequisicao",
                            () -> observarNaoDerrubaARequisicao(sgdf));
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

    /**
     * "Somente leitura" nao e apontamento sozinho.
     *
     * <p>O papel AUDITORIA le por definicao (cap. 15.1: nao pode "qualquer
     * escrita"). Marca-lo faria a lista de atencao repetir todo trimestre a
     * unica linha que esta certa — e uma lista que sempre acusa a mesma coisa e
     * uma lista que ninguem le.
     */
    static void aAtencaoNaoViraRuido() {
        var auditor = acesso("auditora.silva", List.of("AUDITORIA"), 0, 1, 0);
        ok("SEC-10 . o AUDITORIA que so leu NAO e apontado — ler e o que ele faz",
                auditor.atencao().isEmpty()
                        && "SOMENTE_LEITURA".equals(auditor.situacao()));

        var dafParado = acesso("chefe.daf", List.of("APROVADOR_DAF"), 0, 1, 0);
        ok("SEC-10 . mas o APROVADOR_DAF que nunca aprovou nada E apontado",
                dafParado.atencao().stream().anyMatch(a -> a.contains("não exercido"))
                        && "REVISAR".equals(dafParado.situacao()));

        var dafAtivo = acesso("chefe.daf", List.of("APROVADOR_DAF"), 12, 1, 0);
        ok("SEC-10 . e o que aprovou nao e apontado por isso",
                dafAtivo.atencao().isEmpty() && "ATIVO".equals(dafAtivo.situacao()));

        var acumula = acesso("dono.de.tudo",
                List.of("APROVADOR_DAF", "CURADOR_MATRIZ"), 30, 1, 0);
        ok("Cap. 15.1 . acumular CURADOR_MATRIZ e APROVADOR_DAF e concentracao apontada",
                acumula.atencao().stream().anyMatch(a -> a.contains("concentração")));

        var servico = acesso("svc.leitura", List.of("SVC_LEITURA"), 0, 1, 0);
        ok("Cap. 15.1 . conta de servico que aparece como tendo ENTRADO e apontada — "
                        + "login interativo e negado",
                servico.atencao().stream().anyMatch(a -> a.contains("conta de serviço")));

        var comum = acesso("fulano", List.of("PUBLICADOR_FIN"), 5, 1, 3);
        ok("SEC-10 . e o ator comum e ativo, sem apontamento",
                comum.atencao().isEmpty() && "ATIVO".equals(comum.situacao()));
    }

    // --- com banco --------------------------------------------------------------

    /**
     * O que a trilha sozinha nao conseguiria.
     *
     * <p>A trilha registra quem ESCREVE. O auditor nao escreve — e um relatorio
     * de acessos que omite o auditor omite justamente quem tem leitura global.
     */
    static void oAuditorApareceNaPropriaRecertificacao(Sgdf sgdf) {
        observar(sgdf, "auditora.silva", DENTRO, List.of("AUDITORIA"), List.of());
        acao(sgdf, "outra.pessoa", "CURADOR_MATRIZ", "CADASTRO_CONTRATO");

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        var auditor = achar(r, "auditora.silva");
        ok("SEC-10 . o auditor aparece, mesmo sem ter escrito nada",
                auditor != null && auditor.acoesNoPeriodo() == 0);
        ok("SEC-10 . e com o papel que o token concedeu",
                auditor.papeis().equals(List.of("AUDITORIA")));
        ok("SEC-10 . quem so escreveu e nao entrou NAO aparece — a lista e de acesso "
                        + "observado, e a ressalva diz isso",
                achar(r, "outra.pessoa") == null);
        ok("SEC-10 . e a ressalva declara a cobertura",
                ConsultaDeRecertificacao.RESSALVA.contains("nunca entrou")
                        && ConsultaDeRecertificacao.RESSALVA.contains("RA-16"));
    }

    static void oPrivilegioNaoExercidoEApontado(Sgdf sgdf) {
        observar(sgdf, "daf.parado", DENTRO, List.of("APROVADOR_DAF"), List.of());
        observar(sgdf, "daf.ativo", DENTRO, List.of("APROVADOR_DAF"), List.of());
        acao(sgdf, "daf.ativo", "APROVADOR_DAF", "EXCECAO_APROVADA");

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        ok("SEC-10 . o DAF que entrou e nunca aprovou e apontado",
                "REVISAR".equals(achar(r, "daf.parado").situacao()));
        ok("SEC-10 . e o que aprovou nao e",
                "ATIVO".equals(achar(r, "daf.ativo").situacao())
                        && achar(r, "daf.ativo").acoesNoPeriodo() == 1);
        ok("SEC-10 . a ordenacao poe quem menos agiu primeiro — a lista comeca pelo "
                        + "que precisa de revisao",
                r.get(0).acoesNoPeriodo() <= r.get(r.size() - 1).acoesNoPeriodo());
    }

    /** Ganhar um papel no meio do trimestre e o que a recertificacao procura. */
    static void aDerivaDeConcessaoAparece(Sgdf sgdf) {
        observar(sgdf, "cresceu", DENTRO, List.of("PUBLICADOR_FIN"), List.of());
        observar(sgdf, "cresceu", DENTRO.plusDays(20),
                List.of("PUBLICADOR_FIN", "APROVADOR_DAF"), List.of());
        acao(sgdf, "cresceu", "APROVADOR_DAF+PUBLICADOR_FIN", "EXCECAO_APROVADA");

        var linha = achar(relatorio(sgdf), "cresceu");
        ok("SEC-10 . as duas concessoes distintas sao contadas",
                linha.concessoesDistintas() == 2);
        ok("SEC-10 . e a mudanca e apontada para conferencia",
                linha.atencao().stream().anyMatch(a -> a.contains("mudou 1 vez")));
        ok("SEC-10 . com os papeis de todo o periodo, nao so os do ultimo dia",
                linha.papeis().containsAll(List.of("APROVADOR_DAF", "PUBLICADOR_FIN")));
    }

    /**
     * {A,B} e {B,A} sao a mesma concessao — e o teste precisa olhar o ARRAY
     * GRAVADO, nao duas chamadas.
     *
     * <p><b>A primeira versao deste teste nao provava nada, e a quebra
     * deliberada mostrou.</b> Ela observava o mesmo ator duas vezes com os
     * papeis em ordem trocada e conferia que dava uma concessao so. Passava com
     * a ordenacao E sem ela — porque {@code Set.copyOf} tem ordem de iteracao
     * ESTAVEL DENTRO DE UMA EXECUCAO da JVM. As duas chamadas concordavam
     * sempre, com ou sem defeito.
     *
     * <p>A ordem so muda ENTRE execucoes (a JVM sorteia um SALT por processo).
     * O defeito, portanto, e invisivel num teste que roda numa JVM so, e
     * apareceria em producao como deriva de concessao fantasma depois de todo
     * reinicio — apontando mudanca de acesso onde ninguem mudou nada.
     *
     * <p>Verificado como tem de ser: lendo o array gravado e exigindo que ele
     * esteja ordenado. Isso o Set nao pode falsificar.
     */
    static void aOrdemDoArrayNaoInventaDeriva(Sgdf sgdf) {
        observar(sgdf, "estavel", DENTRO,
                List.of("PUBLICADOR_FIN", "GESTOR_CONTRATO", "APROVADOR_DAF"), List.of());

        String gravado = escalar(sgdf, "SELECT papeis::text FROM acesso_observado"
                + " WHERE ator = 'estavel'");
        ok("SEC-10 . o array gravado esta ORDENADO — sem isso, a ordem varia entre "
                        + "execucoes da JVM e todo reinicio inventaria deriva",
                "{APROVADOR_DAF,GESTOR_CONTRATO,PUBLICADOR_FIN}".equals(gravado));

        observar(sgdf, "estavel", DENTRO.plusDays(5),
                List.of("GESTOR_CONTRATO", "APROVADOR_DAF", "PUBLICADOR_FIN"), List.of());
        var linha = achar(relatorio(sgdf), "estavel");
        ok("SEC-10 . e a mesma concessao em dias diferentes conta como UMA",
                linha.concessoesDistintas() == 1);
        ok("SEC-10 . sem apontamento de deriva",
                linha.atencao().stream().noneMatch(a -> a.contains("mudou")));

        // O mesmo vale para os contratos: o recorte {c1,c2} e {c2,c1} e o mesmo.
        UUID menor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID maior = UUID.fromString("ffffffff-0000-0000-0000-000000000002");
        observar(sgdf, "contratos.ordenados", DENTRO, List.of("PUBLICADOR_AP"),
                List.of(maior, menor));
        ok("SEC-10 . e os contratos tambem saem ordenados",
                escalar(sgdf, "SELECT contratos::text FROM acesso_observado"
                        + " WHERE ator = 'contratos.ordenados'").indexOf("00000000") == 1);
    }

    /** SEC-10 pede "por contrato": o recorte do token entra no relatorio. */
    static void oEscopoDeContratoEntraNoRelatorio(Sgdf sgdf) {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        observar(sgdf, "recortado", DENTRO, List.of("PUBLICADOR_AP"), List.of(c1, c2));
        observar(sgdf, "global", DENTRO, List.of("APROVADOR_DAF"), List.of());

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        ok("SEC-10 . o recorte de contrato do token e relatado",
                achar(r, "recortado").contratosNoEscopo() == 2);
        ok("SEC-10 . e visao global aparece como zero contratos, nao como ausencia",
                achar(r, "global").contratosNoEscopo() == 0);
    }

    static void oPeriodoRecorta(Sgdf sgdf) {
        observar(sgdf, "dentro.do.trimestre", DENTRO, List.of("PUBLICADOR_FIN"), List.of());
        observar(sgdf, "fora.do.trimestre", ATE.plusDays(1), List.of("PUBLICADOR_FIN"),
                List.of());

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        ok("SEC-10 . o trimestre recorta", achar(r, "dentro.do.trimestre") != null
                && achar(r, "fora.do.trimestre") == null);

        boolean recusou = false;
        try {
            new ConsultaDeRecertificacao(sgdf).relatorio(ATE, DESDE);
        } catch (ConsultaDeRecertificacao.PeriodoInvalido e) {
            recusou = true;
        }
        ok("SEC-10 . periodo invertido e recusado", recusou);
    }

    /**
     * A ressalva e a PRIMEIRA linha do arquivo.
     *
     * <p>No fim, ou num campo por linha, ela sumiria quando alguem ordenasse a
     * planilha — e e ela que muda o significado da tabela inteira.
     */
    static void oCsvComecaPelaRessalva(Sgdf sgdf) {
        observar(sgdf, "alguem", DENTRO, List.of("PUBLICADOR_FIN"), List.of());
        String csv = new ConsultaDeRecertificacao(sgdf).csv(DESDE, ATE);
        String[] linhas = csv.split("\r\n");

        ok("SEC-10 . a primeira linha do CSV e a ressalva de cobertura",
                linhas[0].contains("COBERTURA") && linhas[0].contains("nunca entrou"));
        ok("SEC-10 . a segunda diz o periodo e quantos atores",
                linhas[1].contains("2027-01-01") && linhas[1].contains("ator(es)"));
        ok("SEC-10 . e o cabecalho vem depois",
                linhas[2].startsWith("ator,papeis"));
        ok("F3-05 . a ressalva passa pelo escape do CSV como qualquer celula",
                !linhas[0].startsWith("=") && !linhas[0].startsWith("+"));
    }

    /**
     * Observar acesso nao pode derrubar o acesso.
     *
     * <p>Um erro ao gravar a observacao transformaria uma consulta ao painel em
     * erro 500 — a recertificacao derrubaria o produto que ela existe para
     * revisar. O custo, dito: uma falha persistente produz relatorio incompleto
     * sem sintoma (RA-15).
     */
    static void observarNaoDerrubaARequisicao(Sgdf sgdf) {
        RegistroDeAcesso registro = new RegistroDeAcesso(sgdf);
        Ator semPapel = new Ator("ninguem", "Ninguem", Set.of(), Set.of());
        ok("SEC-10 . ator sem papel nao vira observacao — nao ha concessao a certificar",
                !registro.observar(semPapel, DENTRO));
        ok("SEC-10 . e ator nulo tampouco", !registro.observar(null, DENTRO));

        Ator valido = new Ator("repetido", "Fulano", Set.of(Papel.PUBLICADOR_FIN), Set.of());
        ok("SEC-10 . a primeira observacao do dia grava",
                registro.observar(valido, DENTRO));
        ok("SEC-10 . e a segunda igual nao duplica — uma linha por concessao por dia",
                !registro.observar(valido, DENTRO));
        ok("SEC-10 . mas nao levanta excecao por isso",
                1 == contar(sgdf, "acesso_observado WHERE ator = 'repetido'"));
    }

    // -------------------------------------------------------------------------

    static ConsultaDeRecertificacao.Acesso acesso(String ator, List<String> papeis, int acoes,
                                                  int concessoes, int contratos) {
        return new ConsultaDeRecertificacao.Acesso(ator, papeis, contratos, DENTRO, DENTRO,
                concessoes, acoes, List.of(), acoes == 0 ? null : DENTRO);
    }

    static List<ConsultaDeRecertificacao.Acesso> relatorio(Sgdf sgdf) {
        return new ConsultaDeRecertificacao(sgdf).relatorio(DESDE, ATE);
    }

    static ConsultaDeRecertificacao.Acesso achar(List<ConsultaDeRecertificacao.Acesso> r,
                                                 String ator) {
        return r.stream().filter(a -> a.ator().equals(ator)).findFirst().orElse(null);
    }

    static void observar(Sgdf sgdf, String ator, LocalDate dia, List<String> papeis,
                         List<UUID> contratos) {
        new RegistroDeAcesso(sgdf).observar(new Ator(ator, ator,
                papeis.stream().map(Papel::valueOf)
                        .collect(java.util.stream.Collectors.toSet()),
                Set.copyOf(contratos)), dia);
    }

    static void acao(Sgdf sgdf, String ator, String papel, String acao) {
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, acao, "teste", MARCA + "-" + (++sequencia), Map.of()));
            return null;
        });
        // A trilha grava ocorrido_em = now(); o relatorio recorta por data, e o
        // fixture precisa cair dentro do trimestre de 2027.
        executar(sgdf, "UPDATE log_auditoria SET ator = ator WHERE false");
        executarDireto(sgdf, "ALTER TABLE log_auditoria DISABLE RULE log_auditoria_sem_update");
        executar(sgdf, "UPDATE log_auditoria SET ocorrido_em = TIMESTAMPTZ '" + DENTRO
                + " 12:00:00-03' WHERE objeto_tipo = 'teste' AND objeto_id LIKE '" + MARCA
                + "%'");
        executarDireto(sgdf, "ALTER TABLE log_auditoria ENABLE RULE log_auditoria_sem_update");
    }

    static void executarDireto(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static void executar(Sgdf sgdf, String sql) {
        executarDireto(sgdf, sql);
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
            st.execute("DELETE FROM acesso_observado");
            st.execute("ALTER TABLE log_auditoria DISABLE RULE log_auditoria_sem_delete");
            st.execute("DELETE FROM log_auditoria WHERE objeto_tipo = 'teste'");
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

    private TestesDeRecertificacao() {}
}
