package br.com.engesoftware.sgdf.folha;

import br.com.engesoftware.sgdf.documento.FolhaDeCompetencia;
import br.com.engesoftware.sgdf.documento.ItemDaFolha;
import br.com.engesoftware.sgdf.documento.Rubrica;
import br.com.engesoftware.sgdf.persistencia.RepositorioDaMatriz;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeRubricas;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Derivacao de eventos a partir da folha — cap. 7.2, historia F2-02.
 *
 * <p>Criterio de aceite: <i>"rescisao na folha de teste instancia as 6
 * exigencias do conjunto, so para aquela matricula"</i>.
 */
public final class TestesDeEventos {

    static final String MARCA = "teste-eventos";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("aAssinaturaNaoAdmiteCalendario",
                TestesDeEventos::aAssinaturaNaoAdmiteCalendario);
        executar("aRubricaDenunciaOEvento", TestesDeEventos::aRubricaDenunciaOEvento);
        executar("codigoVenceDescricao", TestesDeEventos::codigoVenceDescricao);
        executar("rubricaDesconhecidaNaoTrava", TestesDeEventos::rubricaDesconhecidaNaoTrava);
        executar("aAusenciaDeVtEUmGatilho", TestesDeEventos::aAusenciaDeVtEUmGatilho);
        executar("admissaoSoPelaMovimentacao", TestesDeEventos::admissaoSoPelaMovimentacao);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte de banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("aRescisaoInstanciaSeisSoParaAquelaMatricula",
                            () -> aRescisaoInstanciaSeisSoParaAquelaMatricula(sgdf));
                    executar("derivarDeNovoNaoDuplica", () -> derivarDeNovoNaoDuplica(sgdf));
                    executar("matriculaForaDoContratoViraAlerta",
                            () -> matriculaForaDoContratoViraAlerta(sgdf));
                    executar("oDeParaVemDoCadastro", () -> oDeParaVemDoCadastro(sgdf));
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
     * D-04 COMO PROPRIEDADE DA ASSINATURA.
     *
     * <p>O cap. 7.2 abre com "executada quando a folha chega. NUNCA por
     * calendario". Um sinalizador booleano seria testavel e nao provaria nada:
     * alguem o inverte. Aqui a protecao e que o metodo NAO RECEBE DATA — nao ha
     * por onde um calendario entrar, e acrescenta-lo quebra a compilacao de quem
     * chama. Este teste fixa a lista de parametros; se ela mudar, alguem tera de
     * decidir de novo.
     */
    static void aAssinaturaNaoAdmiteCalendario() {
        Method detectar = null;
        for (Method m : DerivadorDeEventos.class.getMethods()) {
            if (m.getName().equals("detectar")) {
                detectar = m;
            }
        }
        ok("D-04 . o derivador expoe exatamente um detectar", detectar != null);

        List<String> tipos = new ArrayList<>();
        for (Parameter p : detectar.getParameters()) {
            tipos.add(p.getType().getSimpleName());
        }
        ok("D-04 . e ele recebe folha, de-para e movimentacao — nada mais — " + tipos,
                tipos.equals(List.of("FolhaDeCompetencia", "DeParaDeRubricas", "Map")));
        ok("D-04 . nenhum parametro e data: nao ha por onde derivar por calendario",
                tipos.stream().noneMatch(t -> t.contains("Date") || t.contains("Clock")
                        || t.contains("YearMonth") || t.contains("Instant")));
    }

    static void aRubricaDenunciaOEvento() {
        FolhaDeCompetencia folha = new FolhaDeCompetencia("06/2026", List.of(
                item("0001", provento("Adiantamento 13º Salário", "1500.00")),
                item("0002", provento("Férias", "2000.00")),
                item("0003", provento("Multa FGTS 40%", "800.00")),
                item("0004", provento("Salário Base", "3000.00"))));

        List<DerivadorDeEventos.Evento> eventos =
                DerivadorDeEventos.detectar(folha, dePara(), Map.of());

        ok("Cap. 7.2 . a rubrica de adiantamento de 13o instancia o evento",
                DerivadorDeEventos.matriculasCom(eventos,
                        DerivadorDeEventos.Tipo.DECIMO_TERCEIRO).equals(List.of("0001")));
        ok("Cap. 7.2 . a de ferias, o de ferias",
                DerivadorDeEventos.matriculasCom(eventos,
                        DerivadorDeEventos.Tipo.FERIAS).equals(List.of("0002")));
        ok("Cap. 7.2 . a multa de FGTS denuncia a rescisao",
                DerivadorDeEventos.matriculasCom(eventos,
                        DerivadorDeEventos.Tipo.RESCISAO).equals(List.of("0003")));
        ok("Cap. 7.2 . e quem so tem salario nao gera evento nenhum desses",
                eventos.stream().filter(e -> e.matricula().equals("0004"))
                        .allMatch(e -> e.tipo() == DerivadorDeEventos.Tipo.NAO_ADESAO_VT));
        ok("F2-02 . o motivo diz QUAL rubrica disparou — sem isso ninguem confere",
                eventos.stream().filter(e -> e.tipo() == DerivadorDeEventos.Tipo.RESCISAO)
                        .findFirst().orElseThrow().motivo().contains("Multa FGTS 40%"));
    }

    /** A descricao varia de grafia entre competencias; o codigo nao. */
    static void codigoVenceDescricao() {
        DeParaDeRubricas dePara = new DeParaDeRubricas(
                Map.of("08305", PapelDaRubrica.VALE_ALIMENTACAO),
                Map.of("VALE ALIMENTACAO", PapelDaRubrica.OUTRA));

        ok("Cap. 7.2 . com codigo, o codigo decide",
                dePara.papelDe(new Rubrica("08305", "V. ALIMENTACAO", null, BigDecimal.ONE))
                        == PapelDaRubrica.VALE_ALIMENTACAO);
        ok("A05 . sem codigo, a descricao normalizada serve",
                dePara.papelDe(Rubrica.semCodigo("Vale Alimentação", null, BigDecimal.ONE))
                        == PapelDaRubrica.OUTRA);
        ok("Cap. 7.2 . acento e caixa nao mudam o casamento",
                DeParaDeRubricas.normalizar("Vale Alimentação")
                        .equals(DeParaDeRubricas.normalizar("VALE ALIMENTACAO")));
    }

    /**
     * A folha tem dezenas de linhas e o de-para so precisa das que alguma regra
     * usa. Tratar o desconhecido como erro faria uma rubrica nova travar a
     * competencia inteira — contra o principio 1 do cap. 1.
     */
    static void rubricaDesconhecidaNaoTrava() {
        ok("Cap. 1 . rubrica fora do cadastro vira OUTRA, nao erro",
                DeParaDeRubricas.vazio().papelDe(
                        Rubrica.semCodigo("RUBRICA NOVA QUE NINGUEM CADASTROU", null,
                                BigDecimal.ONE)) == PapelDaRubrica.OUTRA);

        FolhaDeCompetencia folha = new FolhaDeCompetencia("06/2026",
                List.of(item("0001", provento("Rubrica desconhecida", "10.00"))));
        ok("Cap. 1 . e a derivacao roda mesmo assim",
                !DerivadorDeEventos.detectar(folha, DeParaDeRubricas.vazio(), Map.of())
                        .isEmpty());
    }

    /** O unico gatilho por AUSENCIA do cap. 7.2. */
    static void aAusenciaDeVtEUmGatilho() {
        FolhaDeCompetencia comVt = new FolhaDeCompetencia("06/2026",
                List.of(item("0001", desconto("Vale Transporte", "180.00"))));
        FolhaDeCompetencia semVt = new FolhaDeCompetencia("06/2026",
                List.of(item("0002", provento("Salário Base", "3000.00"))));

        ok("Cap. 7.2 . quem tem VT nao gera nao-adesao",
                DerivadorDeEventos.matriculasCom(
                        DerivadorDeEventos.detectar(comVt, dePara(), Map.of()),
                        DerivadorDeEventos.Tipo.NAO_ADESAO_VT).isEmpty());
        ok("Cap. 7.2 . a AUSENCIA da rubrica de VT e que dispara",
                DerivadorDeEventos.matriculasCom(
                        DerivadorDeEventos.detectar(semVt, dePara(), Map.of()),
                        DerivadorDeEventos.Tipo.NAO_ADESAO_VT).equals(List.of("0002")));
    }

    /**
     * Supor a admissao pela primeira aparicao na folha erraria em toda migracao
     * de sistema, que faz todo mundo "aparecer" de uma vez.
     */
    static void admissaoSoPelaMovimentacao() {
        FolhaDeCompetencia folha = new FolhaDeCompetencia("06/2026",
                List.of(item("0001", provento("Salário Base", "3000.00"))));

        ok("Cap. 7.2 . sem movimentacao, nao ha admissao a afirmar",
                DerivadorDeEventos.matriculasCom(
                        DerivadorDeEventos.detectar(folha, dePara(), Map.of()),
                        DerivadorDeEventos.Tipo.ADMISSAO).isEmpty());

        Map<String, Movimentacao> mov = Map.of("0001",
                new Movimentacao("0001", LocalDate.of(2026, 6, 15), null));
        List<DerivadorDeEventos.Evento> eventos =
                DerivadorDeEventos.detectar(folha, dePara(), mov);
        ok("Cap. 7.2 . com a data na competencia, o evento existe",
                DerivadorDeEventos.matriculasCom(eventos,
                        DerivadorDeEventos.Tipo.ADMISSAO).equals(List.of("0001")));
        ok("Cap. 7.3 . e leva a data, que e a ancora EVENTO",
                eventos.stream().filter(e -> e.tipo() == DerivadorDeEventos.Tipo.ADMISSAO)
                        .findFirst().orElseThrow().data().equals(LocalDate.of(2026, 6, 15)));

        Map<String, Movimentacao> antiga = Map.of("0001",
                new Movimentacao("0001", LocalDate.of(2024, 3, 1), null));
        ok("Cap. 7.2 . admissao de outra competencia nao gera evento",
                DerivadorDeEventos.matriculasCom(
                        DerivadorDeEventos.detectar(folha, dePara(), antiga),
                        DerivadorDeEventos.Tipo.ADMISSAO).isEmpty());
    }

    // --- contra o banco ------------------------------------------------------

    /** O criterio de aceite, literal: seis exigencias, uma matricula. */
    static void aRescisaoInstanciaSeisSoParaAquelaMatricula(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDaMatriz.Materializacao m = new RepositorioDaMatriz(sgdf)
                .materializarEvento(f.ciclo, "RESCISAO",
                        mapa("MAT" + f.seq + "A", LocalDate.of(2026, 4, 20)), "folha");

        ok("F2-02 . a rescisao instancia as SEIS exigencias do conjunto", m.criadas() == 6);
        ok("F2-02 . todas para a matricula que teve o evento",
                6 == contar(sgdf, "exigencia e JOIN profissional p ON p.id = e.profissional_id"
                        + " WHERE e.ciclo_id = '" + f.ciclo + "' AND p.matricula = 'MAT"
                        + f.seq + "A'"));
        ok("F2-02 . e NENHUMA para a outra matricula alocada",
                0 == contar(sgdf, "exigencia e JOIN profissional p ON p.id = e.profissional_id"
                        + " WHERE e.ciclo_id = '" + f.ciclo + "' AND p.matricula = 'MAT"
                        + f.seq + "B'"));
        ok("Cap. 5.2 . as derivadas se distinguem das da matriz pela origem",
                6 == contar(sgdf, "exigencia WHERE ciclo_id = '" + f.ciclo
                        + "' AND origem = 'DERIVADA'"));
        // 20/04 e segunda. Contando 5 dias uteis: 21/04 e Tiradentes e esta na
        // carga do calendario, entao a contagem pula para 22, 23, 24, 27 e 28 —
        // e o prazo cai em 28/04, nao em 27. A primeira versao deste teste
        // esperava 27 por eu ter contado sem o feriado; o valor certo e a prova
        // de que o calendario da UF esta sendo consultado de verdade.
        ok("Cap. 7.3 . o prazo sai da ancora EVENTO, com o feriado nacional no meio",
                "2026-04-28".equals(escalar(sgdf, "SELECT max(prazo_calculado)::text"
                        + " FROM exigencia WHERE ciclo_id = '" + f.ciclo
                        + "' AND origem = 'DERIVADA'")));
        ok("Cap. 16 . a derivacao fica na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'EVENTO_MATERIALIZAR'"
                        + " AND objeto_id = '" + f.ciclo + "'"));
    }

    static void derivarDeNovoNaoDuplica(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDaMatriz repo = new RepositorioDaMatriz(sgdf);
        Map<String, LocalDate> mat = mapa("MAT" + f.seq + "A", LocalDate.of(2026, 4, 20));
        repo.materializarEvento(f.ciclo, "RESCISAO", mat, "folha");
        RepositorioDaMatriz.Materializacao segunda =
                repo.materializarEvento(f.ciclo, "RESCISAO", mat, "folha");

        ok("F2-02 . reprocessar a folha nao duplica as derivadas",
                segunda.criadas() == 0 && 6 == contar(sgdf, "exigencia WHERE ciclo_id = '"
                        + f.ciclo + "'"));
    }

    /**
     * A folha e o cadastro divergem — e criar a exigencia mesmo assim inventaria
     * uma cobranca sem dono.
     */
    static void matriculaForaDoContratoViraAlerta(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDaMatriz.Materializacao m = new RepositorioDaMatriz(sgdf)
                .materializarEvento(f.ciclo, "RESCISAO",
                        mapa("MAT-QUE-NAO-EXISTE", LocalDate.of(2026, 4, 20)), "folha");

        ok("F2-02 . matricula fora do contrato nao vira exigencia", m.criadas() == 0);
        ok("F2-02 . mas vira alerta, nao silencio",
                m.alertas().size() == 1 && m.alertas().get(0).contains("nao esta alocada"
                        .replace("nao", "não").replace("esta", "está")));
    }

    static void oDeParaVemDoCadastro(Sgdf sgdf) {
        DeParaDeRubricas carregado = new RepositorioDeRubricas(sgdf)
                .vigente("CONTRACHEQUE", LocalDate.of(2026, 6, 1));

        ok("Cap. 7.2 . o de-para do contracheque vem do cadastro, nao do codigo-fonte",
                carregado.papelDe(Rubrica.semCodigo("Vale Transporte", null, BigDecimal.ONE))
                        == PapelDaRubrica.VALE_TRANSPORTE);
        ok("Cap. 7.2 . inclusive os gatilhos de evento",
                carregado.papelDe(Rubrica.semCodigo("Multa FGTS 40%", null, BigDecimal.ONE))
                        == PapelDaRubrica.RESCISAO);

        DeParaDeRubricas fopag = new RepositorioDeRubricas(sgdf)
                .vigente("FOPAG-ENGESOFTWARE", LocalDate.of(2026, 6, 1));
        ok("F2-01 . e os codigos da FOPAG que estavam em constante Java",
                fopag.papelDe(new Rubrica("14300", "FGTS DO MES", null, BigDecimal.ONE))
                        == PapelDaRubrica.FGTS_MES);
    }

    // --- apoio ---------------------------------------------------------------

    static Map<String, LocalDate> mapa(String matricula, LocalDate data) {
        Map<String, LocalDate> m = new LinkedHashMap<>();
        m.put(matricula, data);
        return m;
    }

    static DeParaDeRubricas dePara() {
        return new DeParaDeRubricas(Map.of(), Map.of(
                "ADIANTAMENTO 13 SALARIO", PapelDaRubrica.ADIANTAMENTO_13,
                "FERIAS", PapelDaRubrica.FERIAS,
                "MULTA FGTS 40", PapelDaRubrica.RESCISAO,
                "VALE TRANSPORTE", PapelDaRubrica.VALE_TRANSPORTE));
    }

    static Rubrica provento(String descricao, String valor) {
        return Rubrica.semCodigo(descricao, null, new BigDecimal(valor));
    }

    static Rubrica desconto(String descricao, String valor) {
        return Rubrica.semCodigo(descricao, null, new BigDecimal(valor));
    }

    static ItemDaFolha item(String matricula, Rubrica rubrica) {
        boolean eDesconto = rubrica.contem("Vale Transporte");
        return new ItemDaFolha(matricula, "Trabalhador " + matricula, null, "06/2026",
                eDesconto ? List.of() : List.of(rubrica),
                eDesconto ? List.of(rubrica) : List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    record Fixture(int seq, UUID ciclo) {}

    /** Um ciclo com as SEIS exigências de rescisão do cap. 7.2 na matriz. */
    static Fixture fixture(Sgdf sgdf) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Ev " + n + "', '" + String.format("2%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-ev-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Ev " + n + "', '" + String.format("9%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-EV-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/ev', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        UUID ciclo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2026-04',"
                + " 'ABERTO', '" + versao + "', '" + MARCA + "') RETURNING id");

        // As seis do conjunto de rescisao (cap. 7.2).
        for (String codigo : List.of("RES.AVISO_PREVIO", "RES.TRCT", "RES.COMPROVANTE_PG",
                "FGT.GUIA_RESCISORIA", "FGT.COMPROVANTE_PG_GRRF", "RES.ASO_DEMISSIONAL")) {
            UUID tipo = uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo,"
                    + " evento, defasagem, criticidade, sigilo, criado_por) VALUES ('"
                    + codigo + "_" + n + "', 'Rescisao', 'Rescisão', 'PROFISSIONAL',"
                    + " 'RESCISAO', 'EVENTO', 'BLOQUEANTE', 'PESSOAL', '" + MARCA + "')"
                    + " RETURNING id");
            executar(sgdf, "INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id,"
                    + " obrigatoriedade, prazo, responsavel_titular, vigencia_ini,"
                    + " versao_matriz_id, criado_por)"
                    + " SELECT '" + tipo + "', 'MODALIDADE', m.id, 'OBRIGATORIO',"
                    + " '{\"ancora\":\"EVENTO\",\"tipo_dia\":\"UTIL\",\"offset\":5}'::jsonb,"
                    + " 'AP', DATE '2025-01-01', '" + versao + "', '" + MARCA + "'"
                    + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING'");
        }

        for (String sufixo : List.of("A", "B")) {
            UUID prof = uuid(sgdf, "INSERT INTO profissional (matricula, nome, cpf_cifrado,"
                    + " cpf_hash, admissao, criado_por) VALUES ('MAT" + n + sufixo + "',"
                    + " 'Trabalhador', '\\x00', '\\x"
                    + String.format("%02x%02x", n % 256, (int) sufixo.charAt(0))
                    + "', DATE '2025-01-01', '" + MARCA + "') RETURNING id");
            executar(sgdf, "INSERT INTO alocacao (profissional_id, contrato_servico_id,"
                    + " inicio, criado_por) VALUES ('" + prof + "', '" + contrato + "',"
                    + " DATE '2025-01-01', '" + MARCA + "')");
        }
        return new Fixture(n, ciclo);
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
                    + " WHERE criado_por IN ('" + MARCA + "', 'folha'))",
            "DELETE FROM exigencia WHERE criado_por IN ('" + MARCA + "', 'folha')",
            "DELETE FROM alocacao WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM profissional WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM regra_exigibilidade WHERE criado_por = '" + MARCA + "'",
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

    private TestesDeEventos() {}
}
