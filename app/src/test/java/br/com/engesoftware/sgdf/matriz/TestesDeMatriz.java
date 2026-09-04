package br.com.engesoftware.sgdf.matriz;

import br.com.engesoftware.sgdf.persistencia.RepositorioDaMatriz;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Conformidade do motor de exigencias — historias F0-04, F0-06 e F0-07.
 *
 * <p><b>Estes testes nao inventam casos: eles LEEM as suites normativas</b> de
 * {@code especificacao/prazo/casos.json} (32 casos) e
 * {@code especificacao/materializacao/casos.json} (18 casos), as mesmas que a
 * implementacao de referencia em Python responde. O cabecalho das suites diz o
 * que isso significa: <i>"divergencia e defeito da implementacao, nao da suite —
 * corrigir a suite exige decisao da area demandante"</i>.
 *
 * <p>Escrever casos proprios aqui seria o contrario: eu escolheria o que testar
 * a partir do que implementei, e as duas coisas concordariam por construcao.
 */
public final class TestesDeMatriz {

    static final ObjectMapper JSON = new ObjectMapper();

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        executar("suiteDePrazo", TestesDeMatriz::suiteDePrazo);
        executar("suiteDeMaterializacao", TestesDeMatriz::suiteDeMaterializacao);
        executar("prazoCorporativoEOMenor", TestesDeMatriz::prazoCorporativoEOMenor);
        executar("vigenciaEComparadaContraOMes", TestesDeMatriz::vigenciaEComparadaContraOMes);
        executar("alocacaoDeUmDiaConta", TestesDeMatriz::alocacaoDeUmDiaConta);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte de banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("materializarGravaEAbrePendencia",
                            () -> materializarGravaEAbrePendencia(sgdf));
                    executar("reexecutarNaoDuplica", () -> reexecutarNaoDuplica(sgdf));
                    executar("alterarRegraNaoAfetaCicloAberto",
                            () -> alterarRegraNaoAfetaCicloAberto(sgdf));
                    executar("aCorporativaEUmaSoEComOMenorPrazo",
                            () -> aCorporativaEUmaSoEComOMenorPrazo(sgdf));
                    executar("aAberturaFicaNaTrilha", () -> aAberturaFicaNaTrilha(sgdf));
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

    // --- suite do prazo (F0-06) ----------------------------------------------

    static void suiteDePrazo() throws Exception {
        JsonNode suite = JSON.readTree(raiz().resolve("especificacao/prazo/casos.json")
                .toFile());
        JsonNode calendarios = suite.get("feriados");
        int total = 0;

        for (JsonNode caso : suite.get("casos")) {
            total++;
            String id = caso.get("id").asText();
            Calendario calendario = calendario(calendarios.get(caso.get("calendario").asText()));
            Map<String, String> contexto = mapa(caso.get("contexto"));

            if (caso.has("erro")) {
                String esperado = caso.get("erro").asText();
                String obtido = null;
                try {
                    // A construção é parte da resolução: um cadastro cujo offset
                    // não é inteiro nunca chega a virar prazo.
                    Prazo.resolver(prazoDe(caso.get("prazo")), contexto, calendario);
                } catch (PrazoInvalido e) {
                    obtido = e.codigo();
                }
                ok(id + " . erro " + esperado, esperado.equals(obtido));
                continue;
            }

            try {
                Prazo.Resolucao r = Prazo.resolver(prazoDe(caso.get("prazo")), contexto,
                        calendario);
                String esperado = caso.get("esperado").asText();
                List<String> avisosEsperados = lista(caso.get("avisos"));
                boolean bate = esperado.equals(r.data().toString())
                        && new LinkedHashSet<>(avisosEsperados)
                                .equals(new LinkedHashSet<>(r.avisos()));
                ok(id + " . " + esperado + (avisosEsperados.isEmpty() ? ""
                        : " " + avisosEsperados) + " — obtido " + r.data() + " " + r.avisos(),
                        bate);
            } catch (PrazoInvalido e) {
                ok(id + " . erro inesperado " + e.codigo(), false);
            }
        }
        ok("F0-06 . a suite normativa do prazo tem 32 casos e todos rodaram", total == 32);
    }

    // --- suite da materializacao (F0-04, F0-07) -------------------------------

    static void suiteDeMaterializacao() throws Exception {
        JsonNode suite = JSON.readTree(
                raiz().resolve("especificacao/materializacao/casos.json").toFile());
        Map<String, TipoDoCadastro> tipos = tipos(suite.get("tipos_padrao"));
        JsonNode prazoPadrao = suite.get("prazo_padrao");
        List<String> feriadosPadrao = lista(suite.get("feriados_padrao"));
        int total = 0;

        for (JsonNode caso : suite.get("casos")) {
            total++;
            String id = caso.get("id").asText();

            Resolvedor.Contrato contrato = new Resolvedor.Contrato(
                    caso.get("contrato").get("numero").asText(),
                    caso.get("contrato").get("modalidade").asText());
            List<Regra> regras = regras(caso.get("regras"), prazoPadrao);
            List<Alocacao> alocacoes = alocacoes(caso.get("alocacoes"));
            List<String> feriados = caso.has("feriados") ? lista(caso.get("feriados"))
                    : feriadosPadrao;
            Calendario calendario = new Calendario(feriados.stream()
                    .map(LocalDate::parse).collect(java.util.stream.Collectors.toSet()));

            if (caso.has("erro")) {
                String esperado = caso.get("erro").asText();
                String obtido = null;
                try {
                    Resolvedor.abrirCiclo(contrato, caso.get("competencia").asText(), tipos,
                            regras, alocacoes, mapa(caso.get("contexto")), calendario);
                } catch (AberturaInvalida e) {
                    obtido = e.codigo();
                }
                ok(id + " . erro " + esperado, esperado.equals(obtido));
                continue;
            }

            Resolvedor.Abertura abertura;
            try {
                abertura = Resolvedor.abrirCiclo(contrato, caso.get("competencia").asText(),
                        tipos, regras, alocacoes, mapa(caso.get("contexto")), calendario);
            } catch (AberturaInvalida e) {
                ok(id + " . erro inesperado " + e.codigo(), false);
                continue;
            }

            String divergencia = comparar(caso, abertura);
            ok(id + " . " + (divergencia == null
                    ? abertura.exigencias().size() + " exigência(s)" : divergencia),
                    divergencia == null);
        }
        ok("F0-07 . a suite normativa da materializacao tem 18 casos e todos rodaram",
                total == 18);
    }

    /** Compara so os campos que o caso declara — o resto e livre, como em verificar.py. */
    static String comparar(JsonNode caso, Resolvedor.Abertura abertura) {
        JsonNode esperadas = caso.get("esperado");
        List<ExigenciaResolvida> obtidas = abertura.exigencias();
        if (esperadas.size() != obtidas.size()) {
            StringBuilder sb = new StringBuilder("esperava " + esperadas.size()
                    + ", obteve " + obtidas.size() + ": ");
            obtidas.forEach(e -> sb.append(e.tipo()).append('/')
                    .append(e.profissional() == null ? "-" : e.profissional()).append(' '));
            return sb.toString();
        }
        for (int i = 0; i < esperadas.size(); i++) {
            JsonNode esperada = esperadas.get(i);
            ExigenciaResolvida obtida = obtidas.get(i);
            var campos = esperada.fields();
            while (campos.hasNext()) {
                var campo = campos.next();
                String nome = campo.getKey();
                JsonNode valor = campo.getValue();
                if ("avisos".equals(nome)) {
                    if (!new LinkedHashSet<>(lista(valor))
                            .equals(new LinkedHashSet<>(obtida.avisos()))) {
                        return obtida.tipo() + ": avisos " + obtida.avisos() + " != " + valor;
                    }
                    continue;
                }
                String obtidoTexto = switch (nome) {
                    case "tipo" -> obtida.tipo();
                    case "escopo" -> obtida.escopo();
                    case "evento" -> obtida.evento();
                    case "criticidade" -> obtida.criticidade();
                    case "responsavel" -> obtida.responsavel();
                    case "profissional" -> obtida.profissional();
                    case "condicional_grupo" -> obtida.condicionalGrupo();
                    case "prazo" -> obtida.prazo() == null ? null : obtida.prazo().toString();
                    default -> throw new IllegalStateException(
                            "campo esperado que o teste nao sabe comparar: " + nome);
                };
                String esperadoTexto = valor.isNull() ? null : valor.asText();
                if (!java.util.Objects.equals(esperadoTexto, obtidoTexto)) {
                    return obtida.tipo() + ": " + nome + " = " + obtidoTexto + ", esperado "
                            + esperadoTexto;
                }
            }
        }
        for (String trecho : lista(caso.get("alertas_contem"))) {
            if (abertura.alertas().stream().noneMatch(a -> a.contains(trecho))) {
                return "alerta contendo \"" + trecho + "\" nao foi emitido — "
                        + abertura.alertas();
            }
        }
        return null;
    }

    // --- o que as suites nao cobrem e importa --------------------------------

    /**
     * E-10, decisao 1. A exigencia corporativa e uma so, compartilhada, mas o
     * prazo vem de regras que variam por contrato. O MAIOR prazo faria o cliente
     * mais exigente receber tarde.
     */
    static void prazoCorporativoEOMenor() {
        LocalDate cedo = LocalDate.of(2026, 4, 8);
        LocalDate tarde = LocalDate.of(2026, 4, 15);
        ok("E-10 . o prazo corporativo compartilhado e o MENOR entre os ciclos",
                cedo.equals(Resolvedor.prazoCorporativo(List.of(tarde, cedo))));
        ok("E-10 . prazo ausente (aguardando evento) nao conta",
                cedo.equals(Resolvedor.prazoCorporativo(
                        java.util.Arrays.asList(null, cedo, null))));
        ok("E-10 . e so ausentes devolve ausente",
                Resolvedor.prazoCorporativo(java.util.Arrays.asList((LocalDate) null)) == null);
    }

    /**
     * Comparar a vigencia contra o dia 1 dispensaria em silencio a exigencia do
     * mes em que a regra entrou em vigor — justamente o mes em que alguem
     * decidiu que ela passaria a valer.
     */
    static void vigenciaEComparadaContraOMes() {
        Regra doMeioDoMes = new Regra("CER.CND_RFB", "MENSAL", "MODALIDADE", "OUTSOURCING",
                "OBRIGATORIO", null, new Prazo.Cadastrado("INICIO_COMPETENCIA", "UTIL", 5),
                "FINANCEIRO", LocalDate.of(2026, 4, 15), null);
        ok("Cap. 7.1 . regra que passa a valer no dia 15 vale para a competencia toda",
                doMeioDoMes.vigenteEm(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));
        ok("Cap. 7.1 . mas nao para a competencia anterior",
                !doMeioDoMes.vigenteEm(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        Regra encerrada = new Regra("CER.CND_RFB", "MENSAL", "MODALIDADE", "OUTSOURCING",
                "OBRIGATORIO", null, new Prazo.Cadastrado("INICIO_COMPETENCIA", "UTIL", 5),
                "FINANCEIRO", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 4, 2));
        ok("Cap. 7.1 . regra encerrada no dia 2 ainda vale para aquela competencia",
                encerrada.vigenteEm(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));
        ok("Cap. 7.1 . e nao vale para a seguinte",
                !encerrada.vigenteEm(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));
    }

    /**
     * E-10, decisao 2. Quem foi desligado no dia 3 trabalhou 3 dias, tem
     * contracheque e encargos — nao exigir deixaria passar exatamente o caso que
     * a responsabilidade subsidiaria alcanca.
     */
    static void alocacaoDeUmDiaConta() {
        LocalDate ini = LocalDate.of(2026, 4, 1);
        LocalDate fim = LocalDate.of(2026, 4, 30);
        ok("E-10 . desligado no dia 3 conta na competencia",
                new Alocacao("0001", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 4, 3))
                        .ativoEm(ini, fim));
        ok("E-10 . admitido no dia 30 tambem",
                new Alocacao("0002", LocalDate.of(2026, 4, 30), null).ativoEm(ini, fim));
        ok("E-10 . desligado no mes anterior nao",
                !new Alocacao("0003", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 31))
                        .ativoEm(ini, fim));
    }

    // --- contra o banco ------------------------------------------------------

    static void materializarGravaEAbrePendencia(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDaMatriz.Materializacao m =
                new RepositorioDaMatriz(sgdf).materializar(f.ciclo, "fulano");

        ok("F0-07 . a resolucao produziu as tres exigencias da matriz",
                m.resolvidas() == 3 && m.criadas() == 3);
        ok("F0-06 . o prazo gravado e o 5o dia util de abril de 2026, com o feriado",
                "2026-04-08".equals(escalar(sgdf, "SELECT min(prazo_calculado)::text "
                        + "FROM exigencia WHERE criado_por = 'fulano'")));
        ok("Cap. 7.3 . cada exigencia com prazo abriu uma pendencia",
                m.pendencias() == 3);
        ok("Cap. 7.1 . a corporativa foi endereçada por empresa, nao por ciclo",
                1 == contar(sgdf, "exigencia WHERE criado_por = 'fulano' "
                        + "AND empresa_id IS NOT NULL AND ciclo_id IS NULL"));
        ok("Cap. 7.1 . e a de contrato, por ciclo",
                1 == contar(sgdf, "exigencia WHERE criado_por = 'fulano' "
                        + "AND ciclo_id = '" + f.ciclo + "' AND profissional_id IS NULL"));
        ok("Cap. 7.1 . a profissional saiu uma por alocado",
                1 == contar(sgdf, "exigencia WHERE criado_por = 'fulano' "
                        + "AND profissional_id IS NOT NULL"));
    }

    /** A abertura do cap. 7.6 roda por agendador, e agendador repete. */
    static void reexecutarNaoDuplica(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDaMatriz repo = new RepositorioDaMatriz(sgdf);
        repo.materializar(f.ciclo, "fulano");
        long depois = contar(sgdf, "exigencia WHERE criado_por = 'fulano'");
        RepositorioDaMatriz.Materializacao segunda = repo.materializar(f.ciclo, "fulano");

        ok("F0-07 . reexecutar nao cria exigencia nova", segunda.criadas() == 0);
        ok("F0-07 . e o total nao muda",
                depois == contar(sgdf, "exigencia WHERE criado_por = 'fulano'"));
        ok("Cap. 7.3 . nem abre uma segunda pendencia para a mesma exigencia",
                segunda.pendencias() == 0);
    }

    /**
     * O CRITERIO DE ACEITE DA F0-05, e a linha que o sustenta.
     *
     * <p><b>Nao basta verificar que nada mudou.</b> "Nada mudou" tambem acontece
     * quando a consulta nao encontra regra nenhuma — foi assim que uma primeira
     * versao deste teste passou com a leitura por versao corrente injetada de
     * proposito. Entao aqui as exigencias sao APAGADAS depois de publicada a
     * 2.0, e a rematerializacao precisa RECRIA-LAS com o prazo da 1.0: a
     * afirmacao passa a ser sobre o que foi produzido, nao sobre o que deixou de
     * ser.
     */
    static void alterarRegraNaoAfetaCicloAberto(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDaMatriz repo = new RepositorioDaMatriz(sgdf);
        repo.materializar(f.ciclo, "fulano");
        ok("F0-05 . o ciclo abre com o prazo da versao 1.0",
                "2026-04-08".equals(escalar(sgdf, "SELECT max(prazo_calculado)::text "
                        + "FROM exigencia WHERE criado_por = 'fulano'")));

        // Versao 2.0, com o ciclo de abril ABERTO: mesmo alvo, prazo diferente.
        UUID v2 = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo) "
                + "VALUES ('9.9-teste-" + f.seq + "', '" + MARCA + "', 'mudanca de prazo') "
                + "RETURNING id");
        executar(sgdf, "INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id,"
                + " obrigatoriedade, prazo, responsavel_titular, vigencia_ini,"
                + " versao_matriz_id, criado_por)"
                + " SELECT r.tipo_id, r.alvo, r.alvo_modalidade_id, r.obrigatoriedade,"
                + "  '{\"ancora\":\"INICIO_COMPETENCIA\",\"tipo_dia\":\"CORRIDO\","
                + "    \"offset\":20}'::jsonb,"
                + "  r.responsavel_titular, r.vigencia_ini, '" + v2 + "', '" + MARCA + "'"
                + " FROM regra_exigibilidade r WHERE r.versao_matriz_id = '" + f.versao + "'");

        // Apaga o que ja fora materializado: a rematerializacao tem de DECIDIR
        // de novo, e nao apenas deixar de mexer no que estava la.
        executar(sgdf, "DELETE FROM pendencia WHERE exigencia_id IN (SELECT id FROM exigencia"
                + " WHERE criado_por = 'fulano')");
        executar(sgdf, "DELETE FROM exigencia WHERE criado_por = 'fulano'");

        RepositorioDaMatriz.Materializacao depois = repo.materializar(f.ciclo, "fulano");

        ok("F0-05 . a rematerializacao recria as exigencias do ciclo", depois.criadas() == 3);
        ok("F0-05 . com o prazo da versao 1.0, nao o da 2.0 publicada depois",
                "2026-04-08".equals(escalar(sgdf, "SELECT max(prazo_calculado)::text "
                        + "FROM exigencia WHERE criado_por = 'fulano'")));
        ok("F0-05 . porque a materializacao le a versao QUE O CICLO CONGELOU",
                f.versao.equals(depois.versaoMatriz()));

        // E um ciclo aberto DEPOIS, apontando para a 2.0, ve a regra nova.
        UUID novo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + f.contrato + "', '2026-05',"
                + " 'ABERTO', '" + v2 + "', '" + MARCA + "') RETURNING id");
        RepositorioDaMatriz.Materializacao doNovo = repo.materializar(novo, "sicrano");
        ok("F0-05 . o ciclo aberto na versao nova materializa pela 2.0",
                doNovo.criadas() == 3);
        ok("F0-05 . e usa o prazo novo — 20 dias corridos",
                "2026-05-20".equals(escalar(sgdf, "SELECT max(prazo_calculado)::text "
                        + "FROM exigencia WHERE criado_por = 'sicrano'")));
    }

    /**
     * Cap. 7.1: a corporativa e "1 por CNPJ por competencia, compartilhada entre
     * ciclos, satisfeita uma unica vez". Com 15 contratos, duplicar faria a
     * mesma CND ser cobrada 15 vezes.
     */
    static void aCorporativaEUmaSoEComOMenorPrazo(Sgdf sgdf) {
        Fixture a = fixture(sgdf);
        // Segundo contrato da MESMA empresa, com prazo mais folgado na sua regra.
        Fixture b = fixture(sgdf, a.empresa, 10);

        RepositorioDaMatriz repo = new RepositorioDaMatriz(sgdf);
        repo.materializar(b.ciclo, "prazo-folgado");
        repo.materializar(a.ciclo, "prazo-apertado");

        ok("Cap. 7.1 . a corporativa da empresa existe uma vez so na competencia",
                1 == contar(sgdf, "exigencia WHERE empresa_id = '" + a.empresa
                        + "' AND competencia = '2026-04'"));
        ok("E-10 . e fica com o MENOR prazo entre os contratos que a exigem",
                "2026-04-08".equals(escalar(sgdf, "SELECT prazo_calculado::text FROM exigencia"
                        + " WHERE empresa_id = '" + a.empresa + "' AND competencia = '2026-04'")));
        ok("Cap. 7.1 . o segundo contrato nao a conta como criada — ha uma, nao duas",
                0 == contar(sgdf, "exigencia WHERE criado_por = 'prazo-apertado'"
                        + " AND empresa_id IS NOT NULL"));
        ok("Cap. 7.1 . e a view a devolve para os DOIS ciclos",
                2 == contar(sgdf, "exigencia_do_ciclo WHERE procedencia = 'CORPORATIVA'"
                        + " AND ciclo_id IN ('" + a.ciclo + "', '" + b.ciclo + "')"));
    }

    static void aAberturaFicaNaTrilha(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        new RepositorioDaMatriz(sgdf).materializar(f.ciclo, "fulano");
        ok("Cap. 16 . a materializacao registra a versao da matriz usada",
                escalar(sgdf, "SELECT detalhe::text FROM log_auditoria WHERE acao = "
                        + "'CICLO_MATERIALIZAR' AND objeto_id = '" + f.ciclo + "'")
                        .contains(f.versao.toString()));
    }

    // --- fixture -------------------------------------------------------------

    static final String MARCA = "teste-matriz";
    static int sequencia = 0;

    record Fixture(int seq, UUID empresa, UUID contrato, UUID ciclo, UUID versao) {}

    static Fixture fixture(Sgdf sgdf) {
        return fixture(sgdf, null, 5);
    }

    /**
     * Um contrato outsourcing com um alocado e tres regras: uma corporativa, uma
     * de contrato e uma profissional.
     *
     * @param empresaExistente para exercitar o compartilhamento corporativo
     * @param offsetDoPrazo    dia util da competencia
     */
    static Fixture fixture(Sgdf sgdf, UUID empresaExistente, int offsetDoPrazo) {
        int n = ++sequencia;
        UUID empresa = empresaExistente != null ? empresaExistente
                : uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES "
                        + "('Prestador Matriz " + n + "', '" + String.format("6%013d", n)
                        + "', '" + MARCA + "') RETURNING id");
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-matriz-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Matriz " + n + "', '" + String.format("5%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-MATRIZ-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/teste/" + n + "',"
                + " '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\",\"offset\":3}'::jsonb,"
                + " 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        UUID ciclo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2026-04',"
                + " 'ABERTO', '" + versao + "', '" + MARCA + "') RETURNING id");

        // Tres tipos, um de cada escopo. O corporativo e COMPARTILHADO entre os
        // fixtures da mesma empresa, por isso o codigo nao leva o contador.
        String corporativo = "TST.MAT_CORP";
        executar(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento,"
                + " defasagem, criticidade, sigilo, criado_por) VALUES"
                + " ('" + corporativo + "', 'Corporativo', 'Teste', 'CORPORATIVO', 'MENSAL',"
                + "  'M', 'BLOQUEANTE', 'INTERNO', '" + MARCA + "'),"
                + " ('TST.MAT_CT" + n + "', 'Contrato', 'Teste', 'CONTRATO', 'MENSAL', 'M',"
                + "  'BLOQUEANTE', 'INTERNO', '" + MARCA + "'),"
                + " ('TST.MAT_PR" + n + "', 'Profissional', 'Teste', 'PROFISSIONAL', 'MENSAL',"
                + "  'M', 'BLOQUEANTE', 'PESSOAL', '" + MARCA + "')"
                + " ON CONFLICT (codigo) DO NOTHING");

        for (String codigo : List.of(corporativo, "TST.MAT_CT" + n, "TST.MAT_PR" + n)) {
            executar(sgdf, "INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id,"
                    + " obrigatoriedade, prazo, responsavel_titular, vigencia_ini,"
                    + " versao_matriz_id, criado_por)"
                    + " SELECT t.id, 'MODALIDADE', m.id, 'OBRIGATORIO',"
                    + " '{\"ancora\":\"INICIO_COMPETENCIA\",\"tipo_dia\":\"UTIL\","
                    + "   \"offset\":" + offsetDoPrazo + "}'::jsonb,"
                    + " 'FINANCEIRO', DATE '2025-01-01', '" + versao + "', '" + MARCA + "'"
                    + " FROM tipo_documental t, modalidade m"
                    + " WHERE t.codigo = '" + codigo + "' AND m.codigo = 'OUTSOURCING'");
        }

        UUID profissional = uuid(sgdf, "INSERT INTO profissional (matricula, nome, cpf_cifrado,"
                + " cpf_hash, admissao, criado_por) VALUES ('MAT" + n + "', 'Trabalhador',"
                + " '\\x00', '\\x" + String.format("%02x", n % 256) + "', DATE '2025-01-01',"
                + " '" + MARCA + "') RETURNING id");
        executar(sgdf, "INSERT INTO alocacao (profissional_id, contrato_servico_id, inicio,"
                + " criado_por) VALUES ('" + profissional + "', '" + contrato + "',"
                + " DATE '2025-01-01', '" + MARCA + "')");

        // A Sexta-Feira Santa de 2026 desloca o 5o dia util de 07 para 08.
        executar(sgdf, "INSERT INTO calendario_feriados (uf, data, descricao, criado_por)"
                + " VALUES (NULL, DATE '2026-04-03', 'Sexta-Feira Santa', '" + MARCA + "')"
                + " ON CONFLICT DO NOTHING");

        return new Fixture(n, empresa, contrato, ciclo, versao);
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
                    + " WHERE criado_por IN ('" + MARCA + "', 'fulano', 'sicrano',"
                    + " 'prazo-folgado', 'prazo-apertado'))",
            "DELETE FROM exigencia WHERE criado_por IN ('" + MARCA + "', 'fulano', 'sicrano',"
                    + " 'prazo-folgado', 'prazo-apertado')",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM alocacao WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM profissional WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM regra_exigibilidade WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
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

    // --- leitura das suites --------------------------------------------------

    static Path raiz() {
        return Path.of(System.getProperty("sgdf.raiz", "."));
    }

    static Calendario calendario(JsonNode datas) {
        Set<LocalDate> feriados = new LinkedHashSet<>();
        for (String d : lista(datas)) {
            feriados.add(LocalDate.parse(d));
        }
        return new Calendario(feriados);
    }

    /**
     * NÃO usa asText()/asInt(): eles convertem, e converter é o defeito que o
     * caso ERRO-07 procura. O valor vai cru para a fábrica, que decide.
     */
    static Prazo.Cadastrado prazoDe(JsonNode no) {
        return Prazo.Cadastrado.deValores(bruto(no.get("ancora")), bruto(no.get("tipo_dia")),
                bruto(no.get("offset")));
    }

    static Object bruto(JsonNode no) {
        if (no == null || no.isNull()) {
            return null;
        }
        if (no.isBoolean()) {
            return no.booleanValue();
        }
        if (no.isIntegralNumber()) {
            return no.intValue();
        }
        if (no.isNumber()) {
            return no.doubleValue();
        }
        return no.asText();
    }

    static Map<String, TipoDoCadastro> tipos(JsonNode no) {
        Map<String, TipoDoCadastro> tipos = new LinkedHashMap<>();
        var campos = no.fields();
        while (campos.hasNext()) {
            var e = campos.next();
            JsonNode t = e.getValue();
            tipos.put(e.getKey(), new TipoDoCadastro(e.getKey(), t.get("escopo").asText(),
                    t.get("evento").asText(), t.get("criticidade").asText(),
                    t.hasNonNull("condicional_grupo") ? t.get("condicional_grupo").asText()
                            : null));
        }
        return tipos;
    }

    /** Expande os padrões da suíte, como o montar_entrada de verificar.py. */
    static List<Regra> regras(JsonNode nos, JsonNode prazoPadrao) {
        List<Regra> regras = new ArrayList<>();
        for (JsonNode r : nos) {
            regras.add(new Regra(
                    r.get("tipo").asText(),
                    r.hasNonNull("evento") ? r.get("evento").asText() : null,
                    r.get("alvo").asText(),
                    r.get("alvo_id").asText(),
                    r.hasNonNull("obrigatoriedade") ? r.get("obrigatoriedade").asText()
                            : "OBRIGATORIO",
                    r.hasNonNull("criticidade") ? r.get("criticidade").asText() : null,
                    prazoDe(r.hasNonNull("prazo") ? r.get("prazo") : prazoPadrao),
                    r.hasNonNull("responsavel") ? r.get("responsavel").asText() : "FINANCEIRO",
                    LocalDate.parse(r.hasNonNull("vigencia_ini")
                            ? r.get("vigencia_ini").asText() : "2025-01-01"),
                    r.hasNonNull("vigencia_fim") ? LocalDate.parse(r.get("vigencia_fim").asText())
                            : null));
        }
        return regras;
    }

    static List<Alocacao> alocacoes(JsonNode nos) {
        List<Alocacao> alocacoes = new ArrayList<>();
        if (nos != null) {
            for (JsonNode a : nos) {
                alocacoes.add(new Alocacao(a.get("matricula").asText(),
                        LocalDate.parse(a.get("inicio").asText()),
                        a.hasNonNull("fim") ? LocalDate.parse(a.get("fim").asText()) : null));
            }
        }
        return alocacoes;
    }

    static Map<String, String> mapa(JsonNode no) {
        Map<String, String> mapa = new HashMap<>();
        if (no != null) {
            var campos = no.fields();
            while (campos.hasNext()) {
                var e = campos.next();
                mapa.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText());
            }
        }
        return mapa;
    }

    static List<String> lista(JsonNode no) {
        List<String> lista = new ArrayList<>();
        if (no != null && no.isArray()) {
            no.forEach(n -> lista.add(n.asText()));
        }
        return lista;
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

    private TestesDeMatriz() {}
}
