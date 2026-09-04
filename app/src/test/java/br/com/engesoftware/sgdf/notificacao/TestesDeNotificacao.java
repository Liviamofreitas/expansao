package br.com.engesoftware.sgdf.notificacao;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeNotificacao;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Testes da regua de notificacao — historia F1-09.
 *
 * <p>Criterio de aceite: <i>"regua registra o que enviaria, nada e enviado"</i>.
 * As duas metades sao verificadas de formas diferentes, de proposito: o que
 * seria enviado, por comportamento; o "nada e enviado", por ESTRUTURA — nao ha
 * transporte no codigo, e o teste le os fontes para provar que continua nao
 * havendo. Um sinalizador booleano seria testavel e nao provaria nada: alguem o
 * inverte e o envio comeca.
 */
public final class TestesDeNotificacao {

    static final String MARCA = "teste-notificacao";
    static final UUID CICLO = UUID.randomUUID();
    static final LocalDate HOJE = LocalDate.of(2026, 7, 6);

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("aReguaDisparaNosMarcosExatos", TestesDeNotificacao::aReguaDisparaNosMarcosExatos);
        executar("umEmailPorPessoaPorDia", TestesDeNotificacao::umEmailPorPessoaPorDia);
        executar("itensIguaisViramQuantidade", TestesDeNotificacao::itensIguaisViramQuantidade);
        executar("faltaDeCadastroNaoESilencio", TestesDeNotificacao::faltaDeCadastroNaoESilencio);
        executar("oTitularRecebeOEscalonamentoComoCopia",
                TestesDeNotificacao::oTitularRecebeOEscalonamentoComoCopia);
        executar("resolvidaSoNoDiaDaEntrega", TestesDeNotificacao::resolvidaSoNoDiaDaEntrega);
        executar("oHashIdentificaOQueSeriaDito",
                TestesDeNotificacao::oHashIdentificaOQueSeriaDito);
        executar("oAvisoNaoTemOndeCarregarPessoa",
                TestesDeNotificacao::oAvisoNaoTemOndeCarregarPessoa);
        executar("naoExisteTransporteNoCodigo", TestesDeNotificacao::naoExisteTransporteNoCodigo);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte de banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("registraOQueEnviariaENadaMais",
                            () -> registraOQueEnviariaENadaMais(sgdf));
                    executar("rodarDuasVezesNaoGeraOSegundoEmail",
                            () -> rodarDuasVezesNaoGeraOSegundoEmail(sgdf));
                    executar("sombraDesligadaSemTransporteFalha",
                            () -> sombraDesligadaSemTransporteFalha(sgdf));
                    executar("oGestorGenericoAtendeTodasAsFamilias",
                            () -> oGestorGenericoAtendeTodasAsFamilias(sgdf));
                    executar("aExecucaoFicaNaTrilha", () -> aExecucaoFicaNaTrilha(sgdf));
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

    // --- a regua, sem banco --------------------------------------------------

    /**
     * Marco EXATO, nao "a partir de". Fosse "a partir de", uma pendencia vencida
     * ha dez dias produziria dez cobrancas acumuladas por dia.
     */
    static void aReguaDisparaNosMarcosExatos() {
        ok("Cap. 11.1 . D-2 e preventiva",
                momentos(HOJE.plusDays(2)).equals(List.of(Momento.PREVENTIVA)));
        ok("Cap. 11.1 . D+0 e cobranca",
                momentos(HOJE).equals(List.of(Momento.COBRANCA)));
        ok("Cap. 11.1 . D+2 e escalonamento nivel 1",
                momentos(HOJE.minusDays(2)).equals(List.of(Momento.ESCALONAMENTO_N1)));
        ok("Cap. 11.1 . D+5 e escalonamento nivel 2",
                momentos(HOJE.minusDays(5)).equals(List.of(Momento.ESCALONAMENTO_N2)));
        ok("Cap. 11.1 . D+3 nao tem marco — a regua nao repete cobranca todo dia",
                momentos(HOJE.minusDays(3)).isEmpty());
        ok("Cap. 11.1 . D-10 tambem nao",
                momentos(HOJE.plusDays(10)).isEmpty());
    }

    /**
     * Cap. 11.2. A consolidacao e por PESSOA, e nao por momento: quem tem algo
     * vencendo, algo vencido e algo escalado recebe UM aviso com as tres coisas.
     */
    static void umEmailPorPessoaPorDia() {
        Regua.Resultado r = Regua.avisos(CICLO, HOJE, List.of(
                pendencia("CER.CND", "Certidões", HOJE.plusDays(2)),
                pendencia("FOL.FOPAG", "Folha", HOJE),
                pendencia("FIS.NF", "Fiscal", HOJE.minusDays(2))), false, cadastroCompleto());

        List<Aviso> paraAna = avisosDe(r, "ana@engesoftware.com.br");
        ok("Cap. 11.2 . a titular recebe UM aviso, nao tres", paraAna.size() == 1);
        ok("Cap. 11.2 . com os tres momentos dentro", paraAna.get(0).itens().size() == 3);
        ok("Cap. 11.2 . e o tipo do aviso e o momento mais grave",
                paraAna.get(0).momento() == Momento.ESCALONAMENTO_N1);
        ok("Cap. 11.1 . o gestor tambem recebe o dele",
                avisosDe(r, "gestor@engesoftware.com.br").size() == 1);
        ok("F1-09 . e nada ficou sem destinatario", r.completo());
    }

    static void itensIguaisViramQuantidade() {
        Regua.Resultado r = Regua.avisos(CICLO, HOJE, List.of(
                pendencia("RES.CONTRACHEQUE", "Folha", HOJE),
                pendencia("RES.CONTRACHEQUE", "Folha", HOJE),
                pendencia("RES.CONTRACHEQUE", "Folha", HOJE)), false, cadastroCompleto());

        Aviso aviso = avisosDe(r, "ana@engesoftware.com.br").get(0);
        ok("Cap. 11.2 . tres exigencias do mesmo tipo viram uma linha",
                aviso.itens().size() == 1);
        ok("F1-09 . com quantidade 3", aviso.itens().get(0).quantidade() == 3);
    }

    /**
     * Uma pendencia cujo titular nao esta cadastrado simplesmente nao seria
     * cobrada. O painel a mostraria pendente e a area juraria nao ter sido
     * avisada — as duas versoes certas, e ninguem saberia por quê.
     */
    static void faltaDeCadastroNaoESilencio() {
        Regua.Cadastro vazio = (familia, papel) -> null;
        Regua.Resultado r = Regua.avisos(CICLO, HOJE,
                List.of(pendencia("CER.CND", "Certidões", HOJE)), false, vazio);

        ok("F1-09 . sem cadastro nao sai aviso", r.avisos().isEmpty());
        ok("F1-09 . mas a execucao NAO se declara completa", !r.completo());
        ok("F1-09 . e diz qual pendencia ficou sem dono",
                r.semDestinatario().stream().anyMatch(s -> s.contains("CER.CND")
                        && s.contains("TITULAR")));
    }

    static void oTitularRecebeOEscalonamentoComoCopia() {
        Regua.Resultado r = Regua.avisos(CICLO, HOJE,
                List.of(pendencia("FIS.NF", "Fiscal", HOJE.minusDays(2))), false,
                cadastroCompleto());

        Aviso doGestor = avisosDe(r, "gestor@engesoftware.com.br").get(0);
        Aviso daTitular = avisosDe(r, "ana@engesoftware.com.br").get(0);
        ok("Cap. 11.1 . o escalonamento e do gestor", !doGestor.itens().get(0).copia());
        ok("Cap. 11.1 . e chega ao titular como copia", daTitular.itens().get(0).copia());
    }

    static void resolvidaSoNoDiaDaEntrega() {
        PendenciaAberta resolvidaHoje = new PendenciaAberta(UUID.randomUUID(), "FOL.FOPAG",
                "Folha", HOJE.minusDays(3), HOJE);
        PendenciaAberta resolvidaOntem = new PendenciaAberta(UUID.randomUUID(), "FIS.NF",
                "Fiscal", HOJE.minusDays(3), HOJE.minusDays(1));

        Regua.Resultado r = Regua.avisos(CICLO, HOJE, List.of(resolvidaHoje, resolvidaOntem),
                false, cadastroCompleto());
        List<Aviso.Item> itens = avisosDe(r, "ana@engesoftware.com.br").get(0).itens();
        ok("Cap. 11.1 . entrega detectada hoje gera o 'resolvido'",
                itens.stream().anyMatch(i -> i.tipoCodigo().equals("FOL.FOPAG")));
        ok("F1-09 . a de ontem nao volta a ser anunciada",
                itens.stream().noneMatch(i -> i.tipoCodigo().equals("FIS.NF")));
        ok("F1-09 . e a pendencia ja resolvida nao gera cobranca por prazo vencido",
                itens.stream().allMatch(i -> i.momento() == Momento.RESOLVIDA));
    }

    /**
     * O hash e o que liga o registro do modo sombra ao que a regua diria. Sem
     * ele a calibragem da F3-01 compararia contagens e nao conteudo.
     */
    static void oHashIdentificaOQueSeriaDito() {
        Aviso a = umAviso(List.of(item(Momento.COBRANCA, "CER.CND", 1),
                item(Momento.PREVENTIVA, "FIS.NF", 1)));
        Aviso mesmaCoisaOutraOrdem = umAviso(List.of(item(Momento.PREVENTIVA, "FIS.NF", 1),
                item(Momento.COBRANCA, "CER.CND", 1)));
        Aviso outraCoisa = umAviso(List.of(item(Momento.COBRANCA, "CER.CND", 2),
                item(Momento.PREVENTIVA, "FIS.NF", 1)));

        String h1 = Conteudo.montar(a, template(), null).hash();
        String h2 = Conteudo.montar(mesmaCoisaOutraOrdem, template(), null).hash();
        String h3 = Conteudo.montar(outraCoisa, template(), null).hash();

        ok("F1-09 . a ordem de entrada nao muda o hash", h1.equals(h2));
        ok("F1-09 . mas a quantidade muda", !h1.equals(h3));
        ok("Cap. 11.2 . o hash tem 64 hex", h1.matches("[0-9a-f]{64}"));

        Conteudo.Template outraVersao = new Conteudo.Template(Momento.COBRANCA, "1.1",
                "x", "y {itens}");
        ok("Cap. 11.2 . e trocar a versao do template muda o hash",
                !h1.equals(Conteudo.montar(a, outraVersao, null).hash()));
    }

    /**
     * GARANTIA ESTRUTURAL. Exigencia de escopo profissional e uma por
     * trabalhador; um aviso que as listasse nominalmente mandaria nome de gente
     * para a caixa de uma area. O item carrega QUANTIDADE e nada mais — e este
     * teste quebra se alguem acrescentar um campo, forcando a decisao a ser
     * tomada de novo em vez de passar numa revisao distraida.
     */
    static void oAvisoNaoTemOndeCarregarPessoa() {
        List<String> campos = new ArrayList<>();
        for (RecordComponent c : Aviso.Item.class.getRecordComponents()) {
            campos.add(c.getName());
        }
        ok("SEC-02 . o item do aviso tem exatamente os campos previstos — " + campos,
                campos.equals(List.of("momento", "tipoCodigo", "familia", "prazo",
                        "quantidade", "papelRecebido")));

        List<String> daPendencia = new ArrayList<>();
        for (RecordComponent c : PendenciaAberta.class.getRecordComponents()) {
            daPendencia.add(c.getName());
        }
        ok("SEC-02 . e a pendencia nao carrega profissional — nao ha o que vazar",
                daPendencia.stream().noneMatch(n -> n.toLowerCase(java.util.Locale.ROOT)
                        .matches(".*(profissional|cpf|nome|matricula).*")));
    }

    /**
     * "Nada e enviado" como propriedade do codigo, nao como configuracao.
     *
     * <p>Le os fontes. Um teste de comportamento nao consegue provar ausencia:
     * ele so mostra que NAQUELE caminho nada saiu. O que se quer garantir e que
     * nao existe caminho — e isso se ve na lista de imports.
     */
    static void naoExisteTransporteNoCodigo() throws IOException {
        Path raiz = Path.of(System.getProperty("sgdf.raiz", "."), "app/src/main/java");
        List<Path> fontes = new ArrayList<>();
        try (Stream<Path> caminhos = Files.walk(raiz.resolve("br/com/engesoftware/sgdf"))) {
            caminhos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/notificacao/")
                            || p.getFileName().toString().equals("RepositorioDeNotificacao.java"))
                    .forEach(fontes::add);
        }
        ok("F1-09 . os fontes da notificacao foram encontrados", fontes.size() >= 7);

        List<String> suspeitos = new ArrayList<>();
        for (Path fonte : fontes) {
            for (String linha : Files.readAllLines(fonte)) {
                if (linha.startsWith("import ") && linha.matches(
                        ".*\\b(javax\\.mail|jakarta\\.mail|java\\.net|"
                        + "org\\.apache\\.http|okhttp3|smtp|Socket|HttpClient)\\b.*")) {
                    suspeitos.add(fonte.getFileName() + ": " + linha.strip());
                }
            }
        }
        ok("F1-09 . nenhum fonte da notificacao importa transporte — " + suspeitos,
                suspeitos.isEmpty());

        boolean temMetodoDeEnvio = Stream.of(RepositorioDeNotificacao.class.getMethods())
                .anyMatch(m -> m.getName().matches("(?i).*(enviar|send|dispatch|transmitir).*"));
        ok("F1-09 . e o repositorio nao expoe metodo de envio", !temMetodoDeEnvio);
    }

    // --- contra o banco ------------------------------------------------------

    static void registraOQueEnviariaENadaMais(Sgdf sgdf) {
        Fixture f = fixture(sgdf, HOJE);
        RepositorioDeNotificacao repo = new RepositorioDeNotificacao(sgdf);
        RepositorioDeNotificacao.Execucao e = repo.executar(f.ciclo, HOJE);

        ok("F1-09 . a regua registrou o que enviaria", !e.registrados().isEmpty());
        ok("F1-09 . e a execucao se declara em modo sombra", e.modoSombra());
        ok("F1-09 . as linhas nascem marcadas como sombra",
                0 == contar(sgdf, "notificacao WHERE ciclo_id = '" + f.ciclo
                        + "' AND NOT modo_sombra"));
        ok("F1-09 . NADA foi enviado — enviada_em e nulo em todas",
                0 == contar(sgdf, "notificacao WHERE ciclo_id = '" + f.ciclo
                        + "' AND enviada_em IS NOT NULL"));
        ok("Cap. 11.2 . o hash do conteudo ficou gravado",
                escalar(sgdf, "SELECT conteudo_hash FROM notificacao WHERE ciclo_id = '"
                        + f.ciclo + "' LIMIT 1").matches("[0-9a-f]{64}"));
        ok("Cap. 11.2 . e a versao do template tambem",
                "1.0".equals(escalar(sgdf, "SELECT template_versao FROM notificacao "
                        + "WHERE ciclo_id = '" + f.ciclo + "' LIMIT 1")));
    }

    /**
     * O agendador pode disparar duas vezes por qualquer motivo. A consolidacao
     * do cap. 11.2 e imposta pelo banco, nao confiada a ele.
     */
    static void rodarDuasVezesNaoGeraOSegundoEmail(Sgdf sgdf) {
        Fixture f = fixture(sgdf, HOJE);
        RepositorioDeNotificacao repo = new RepositorioDeNotificacao(sgdf);
        repo.executar(f.ciclo, HOJE);
        long depoisDaPrimeira = contar(sgdf, "notificacao WHERE ciclo_id = '" + f.ciclo + "'");
        repo.executar(f.ciclo, HOJE);

        ok("Cap. 11.2 . a segunda execucao do dia nao gera o segundo e-mail",
                depoisDaPrimeira == contar(sgdf, "notificacao WHERE ciclo_id = '"
                        + f.ciclo + "'"));
    }

    /**
     * Fingir envio e pior que nao enviar: a area nao recebe, o sistema jura que
     * mandou, e a discussao seguinte nao tem como ser resolvida.
     */
    static void sombraDesligadaSemTransporteFalha(Sgdf sgdf) {
        Fixture f = fixture(sgdf, HOJE);
        executar(sgdf, "UPDATE parametro SET valor = 'false'::jsonb "
                + "WHERE chave = 'notificacao.modo_sombra' AND escopo = 'GLOBAL'");
        boolean recusou = false;
        try {
            new RepositorioDeNotificacao(sgdf).executar(f.ciclo, HOJE);
        } catch (RepositorioDeNotificacao.SemTransporte e) {
            recusou = true;
        } finally {
            executar(sgdf, "UPDATE parametro SET valor = 'true'::jsonb "
                    + "WHERE chave = 'notificacao.modo_sombra' AND escopo = 'GLOBAL'");
        }
        ok("F1-09 . desligar a sombra sem transporte FALHA, em vez de fingir envio", recusou);
        ok("F1-09 . e nada foi gravado",
                0 == contar(sgdf, "notificacao WHERE ciclo_id = '" + f.ciclo + "'"));
    }

    /** Cap. 11.2: GESTOR e DAF nao variam por familia — ficam com familia nula. */
    static void oGestorGenericoAtendeTodasAsFamilias(Sgdf sgdf) {
        Fixture f = fixture(sgdf, HOJE.minusDays(2));
        RepositorioDeNotificacao.Execucao e =
                new RepositorioDeNotificacao(sgdf).executar(f.ciclo, HOJE);

        ok("Cap. 11.2 . o gestor generico recebeu o escalonamento",
                e.registrados().stream().anyMatch(r ->
                        r.aviso().destinatario().email().startsWith("gestor")));
        ok("F1-09 . e nenhuma pendencia ficou sem dono — " + e.semDestinatario(),
                e.completa());
    }

    static void aExecucaoFicaNaTrilha(Sgdf sgdf) {
        Fixture f = fixture(sgdf, HOJE);
        new RepositorioDeNotificacao(sgdf).executar(f.ciclo, HOJE);
        ok("Cap. 16 . a execucao da regua fica na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'NOTIFICACAO_MODO_SOMBRA' "
                        + "AND objeto_id = '" + f.ciclo + "'"));
    }

    // --- apoio ---------------------------------------------------------------

    static List<Momento> momentos(LocalDate prazo) {
        return Regua.momentosDe(pendencia("X.Y", "Folha", prazo), HOJE);
    }

    static PendenciaAberta pendencia(String tipo, String familia, LocalDate prazo) {
        return new PendenciaAberta(UUID.randomUUID(), tipo, familia, prazo, null);
    }

    static Aviso.Item item(Momento momento, String tipo, int quantidade) {
        return new Aviso.Item(momento, tipo, "Folha", HOJE, quantidade, PapelNoAviso.TITULAR);
    }

    static Aviso umAviso(List<Aviso.Item> itens) {
        return new Aviso(CICLO, new Destinatario("Ana", "ana@x.com", PapelNoAviso.TITULAR,
                "Folha"), HOJE, itens);
    }

    static Conteudo.Template template() {
        return new Conteudo.Template(Momento.COBRANCA, "1.0", "assunto {quantidade}",
                "corpo {itens} {link}");
    }

    /** Titular por familia; gestor e DAF genericos, como o cap. 11.2 descreve. */
    static Regua.Cadastro cadastroCompleto() {
        Map<String, Destinatario> mapa = new HashMap<>();
        for (String familia : List.of("Certidões", "Folha", "Fiscal")) {
            mapa.put(familia + "|TITULAR", new Destinatario("Ana",
                    "ana@engesoftware.com.br", PapelNoAviso.TITULAR, familia));
            mapa.put(familia + "|SUBSTITUTO", new Destinatario("Bruno",
                    "bruno@engesoftware.com.br", PapelNoAviso.SUBSTITUTO, familia));
        }
        return (familia, papel) -> {
            Destinatario d = mapa.get(familia + "|" + papel);
            if (d != null) {
                return d;
            }
            return switch (papel) {
                case GESTOR -> new Destinatario("Carla", "gestor@engesoftware.com.br",
                        PapelNoAviso.GESTOR, null);
                case DAF -> new Destinatario("Daniel", "daf@engesoftware.com.br",
                        PapelNoAviso.DAF, null);
                default -> null;
            };
        };
    }

    static List<Aviso> avisosDe(Regua.Resultado r, String email) {
        return r.avisos().stream().filter(a -> a.destinatario().email().equals(email)).toList();
    }

    record Fixture(UUID ciclo, UUID contrato) {}

    static Fixture fixture(Sgdf sgdf, LocalDate prazo) {
        int n = ++sequencia;
        UUID tipo = uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo,"
                + " evento, defasagem, criticidade, sigilo, criado_por)"
                + " VALUES ('TST.NOT" + n + "', 'Tipo', 'Folha', 'CONTRATO', 'MENSAL', 'M',"
                + " 'BLOQUEANTE', 'INTERNO', '" + MARCA + "') RETURNING id");
        UUID ciclo = uuid(sgdf, "WITH c AS ("
                + " INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por)"
                + " VALUES ('Cliente Notif " + n + "', '" + String.format("8%013d", n)
                + "', 'PRIVADA', false, '" + MARCA + "') RETURNING id),"
                + " m AS (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING' LIMIT 1),"
                + " v AS (INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + "  VALUES ('0.0-notif-" + n + "', '" + MARCA + "', 'fixture') RETURNING id),"
                + " cs AS (INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + "   modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + "   calendario_uf, ativo, criado_por)"
                + "  SELECT c.id, 'CT-NOTIF-" + n + "', 'Teste', m.id, DATE '2026-01-01',"
                + "   '/teste', '{\"ancora\": \"ATESTE\", \"offset\": 3,"
                + "    \"tipo_dia\": \"CORRIDO\"}'::jsonb, 'DF', false, '" + MARCA + "'"
                + "  FROM c, m RETURNING id)"
                + " INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + "  versao_matriz_id, criado_por)"
                + " SELECT cs.id, '2026-06', 'ABERTO', v.id, '" + MARCA + "' FROM cs, v"
                + " RETURNING id");
        UUID contrato = uuid(sgdf, "SELECT contrato_servico_id FROM ciclo WHERE id = '"
                + ciclo + "'");
        UUID exigencia = uuid(sgdf, "INSERT INTO exigencia (ciclo_id, tipo_id, evento, status,"
                + " prazo_calculado, criticidade, responsavel, origem, criado_por)"
                + " VALUES ('" + ciclo + "', '" + tipo + "', 'MENSAL', 'PENDENTE', DATE '"
                + prazo + "', 'BLOQUEANTE', 'AP', 'MATRIZ', '" + MARCA + "') RETURNING id");
        executar(sgdf, "INSERT INTO pendencia (exigencia_id, prazo) VALUES ('" + exigencia
                + "', DATE '" + prazo + "')");
        executar(sgdf, "INSERT INTO destinatario (contrato_servico_id, familia, papel, nome,"
                + " email, vigencia_ini, criado_por) VALUES"
                + " ('" + contrato + "', 'Folha', 'TITULAR', 'Ana', 'ana" + n
                + "@engesoftware.com.br', DATE '2025-01-01', '" + MARCA + "'),"
                + " ('" + contrato + "', 'Folha', 'SUBSTITUTO', 'Bruno', 'bruno" + n
                + "@engesoftware.com.br', DATE '2025-01-01', '" + MARCA + "'),"
                + " ('" + contrato + "', NULL, 'GESTOR', 'Carla', 'gestor" + n
                + "@engesoftware.com.br', DATE '2025-01-01', '" + MARCA + "'),"
                + " ('" + contrato + "', NULL, 'DAF', 'Daniel', 'daf" + n
                + "@engesoftware.com.br', DATE '2025-01-01', '" + MARCA + "')");
        return new Fixture(ciclo, contrato);
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
            "DELETE FROM notificacao WHERE ciclo_id IN (SELECT id FROM ciclo "
                    + "WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM pendencia WHERE exigencia_id IN (SELECT id FROM exigencia "
                    + "WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM destinatario WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM exigencia WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM tipo_documental WHERE criado_por = '" + MARCA + "'",
            "UPDATE parametro SET valor = 'true'::jsonb "
                    + "WHERE chave = 'notificacao.modo_sombra' AND escopo = 'GLOBAL'",
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

    private TestesDeNotificacao() {}
}
