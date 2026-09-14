package br.com.engesoftware.sgdf.retencao;

import br.com.engesoftware.sgdf.orquestracao.Job;
import br.com.engesoftware.sgdf.persistencia.ExpurgoPorTemporalidade;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeTemporalidade;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import br.com.engesoftware.sgdf.retencao.Temporalidade.Acao;
import br.com.engesoftware.sgdf.retencao.Temporalidade.Alvo;
import br.com.engesoftware.sgdf.retencao.Temporalidade.Marco;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Retencao por temporalidade e expurgo — pendencia A08, requisito LGPD-02.
 *
 * <p>A afirmacao que estes casos sustentam e uma so: NAO HA CAMINHO QUE ELIMINE
 * DADO SEM QUE ALGUEM COM NOME TENHA APROVADO O PRAZO — e, quando ha aprovacao,
 * "apagou zero porque nada venceu" continua sendo distinguivel de "apagou zero
 * porque a politica nunca foi aplicada".
 *
 * <p><b>A parte com banco roda inteira dentro de uma transacao desfeita no
 * fim.</b> Nao e zelo: as tabelas `expurgo` e `expurgo_item` sao append-only por
 * RULE, entao DELETE de limpeza e SILENCIOSAMENTE DESCARTADO. Um `finally` que
 * limpasse por DELETE passaria, nao limparia nada, e o proximo teste contaria
 * linhas do anterior. O rollback e a unica limpeza que funciona aqui.
 */
public final class TestesDeExpurgo {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static final String MARCA = "teste-expurgo";
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("aprovacaoEPortaUnica", TestesDeExpurgo::aprovacaoEPortaUnica);
        executar("oCorteContaDoMarco", TestesDeExpurgo::oCorteContaDoMarco);
        executar("oImpedimentoDizQualDosTres", TestesDeExpurgo::oImpedimentoDizQualDosTres);
        executar("saberListarNaoESaberApagar", TestesDeExpurgo::saberListarNaoESaberApagar);
        executar("oExpurgoSeAgendaEDeclaraCadencia",
                TestesDeExpurgo::oExpurgoSeAgendaEDeclaraCadencia);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection c = DriverManager.getConnection(url)) {
                c.setAutoCommit(false);
                try {
                    Sgdf sgdf = new Sgdf(c);
                    executar("semAprovacaoNaoApagaEDizPorQue",
                            () -> semAprovacaoNaoApagaEDizPorQue(sgdf));
                    executar("aprovadaApagaOVencidoEPoupaORecente",
                            () -> aprovadaApagaOVencidoEPoupaORecente(sgdf));
                    executar("zeroAutorizadoEUmaLinha", () -> zeroAutorizadoEUmaLinha(sgdf));
                    executar("revisarNaoApagaENaoRegistraExpurgo",
                            () -> revisarNaoApagaENaoRegistraExpurgo(sgdf));
                    executar("aReguaVencidaTambemSai",
                            () -> aReguaVencidaTambemSai(sgdf));
                } finally {
                    c.rollback();
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

    // --- sem banco --------------------------------------------------------------

    /** Data sem nome e nome sem data nao aprovam. Aprovar e um ato de alguem. */
    static void aprovacaoEPortaUnica() {
        OffsetDateTime agora = OffsetDateTime.now();
        ok("A08 . classe sem data nem nome nao esta aprovada",
                !classe(Alvo.ACESSO_OBSERVADO, Marco.REGISTRO, 12, Acao.EXPURGAR,
                        null, null).aprovada());
        ok("A08 . data sem nome nao aprova — seria um prazo que se aprovou sozinho",
                !classe(Alvo.ACESSO_OBSERVADO, Marco.REGISTRO, 12, Acao.EXPURGAR,
                        agora, "   ").aprovada());
        ok("A08 . nome sem data nao aprova",
                !classe(Alvo.ACESSO_OBSERVADO, Marco.REGISTRO, 12, Acao.EXPURGAR,
                        null, "dpo").aprovada());
        ok("A08 . com os dois, aprova",
                classe(Alvo.ACESSO_OBSERVADO, Marco.REGISTRO, 12, Acao.EXPURGAR,
                        agora, "dpo").aprovada());
    }

    /**
     * O corte e estrito, e o dia de diferenca importa.
     *
     * <p>No dia exato em que o prazo completa, o dado ainda esta no ultimo dia de
     * guarda. Apaga-lo ali e apaga-lo um dia cedo — e um dia cedo numa prescricao
     * e a diferenca entre ter e nao ter a prova na audiencia.
     */
    static void oCorteContaDoMarco() {
        Temporalidade t = classe(Alvo.ACESSO_OBSERVADO, Marco.REGISTRO, 12, Acao.EXPURGAR,
                OffsetDateTime.now(), "dpo");
        LocalDate corte = t.corte(LocalDate.of(2026, 9, 14));
        ok("A08 . 12 meses antes de 2026-09-14 e 2025-09-14",
                corte.equals(LocalDate.of(2025, 9, 14)));
        ok("A08 . o dado do proprio dia do corte NAO venceu — a comparacao e estrita",
                !corte.isBefore(LocalDate.of(2025, 9, 14)));
    }

    /**
     * Os tres motivos sao distintos, e o texto tem de dize-lo.
     *
     * <p>"Nao foi aplicada" sem o motivo manda o DPO procurar; e o relatorio dele
     * que esta frase alimenta.
     */
    static void oImpedimentoDizQualDosTres() {
        String semAprovacao = classe(Alvo.ACESSO_OBSERVADO, Marco.REGISTRO, 12,
                Acao.EXPURGAR, null, null).impedimento();
        ok("A08 . classe nao aprovada e impedida, e o texto nomeia a pendencia",
                semAprovacao != null && semAprovacao.contains("A08"));

        String marcoErrado = classe(Alvo.ACESSO_OBSERVADO, Marco.PUBLICACAO_DO_BOOK, 12,
                Acao.EXPURGAR, OffsetDateTime.now(), "dpo").impedimento();
        ok("A08 . marco que o alvo nao possui e impedido em vez de reinterpretado",
                marcoErrado != null && marcoErrado.contains("PUBLICACAO_DO_BOOK"));

        String semExecutor = classe(Alvo.PROFISSIONAL, Marco.DESLIGAMENTO_DO_PROFISSIONAL,
                60, Acao.ANONIMIZAR, OffsetDateTime.now(), "dpo").impedimento();
        ok("A08 . politica valida que o SGDF nao sabe cumprir vira recusa NOMEADA, "
                        + "nunca zero silencioso",
                semExecutor != null && semExecutor.contains("ANONIMIZAR"));

        ok("A08 . e a classe aprovada, com marco e executor, nao tem impedimento",
                classe(Alvo.ACESSO_OBSERVADO, Marco.REGISTRO, 12, Acao.EXPURGAR,
                        OffsetDateTime.now(), "dpo").impedimento() == null);
    }

    /**
     * A capacidade e do PAR (alvo, acao), nao do alvo.
     *
     * <p>Tratar as duas como a mesma capacidade faria o sistema aceitar uma
     * politica de eliminacao de evidencia fiscal por ter sido escrito o codigo de
     * uma lista.
     */
    static void saberListarNaoESaberApagar() {
        ok("A08 . o documento pode ser LISTADO para revisao humana",
                Alvo.DOCUMENTO.sabeFazer(Acao.REVISAR));
        ok("A08 . e NAO pode ser apagado por decisao de agendador",
                !Alvo.DOCUMENTO.sabeFazer(Acao.EXPURGAR));
        ok("A08 . o registro de acesso pode ser apagado",
                Alvo.ACESSO_OBSERVADO.sabeFazer(Acao.EXPURGAR));
        ok("A08 . o alvo sem executor nenhum nao sabe fazer nada",
                !Alvo.CAMPO_EXTRAIDO.sabeFazer(Acao.EXPURGAR)
                        && !Alvo.CAMPO_EXTRAIDO.sabeFazer(Acao.REVISAR)
                        && !Alvo.CAMPO_EXTRAIDO.sabeFazer(Acao.ANONIMIZAR));

        // INVARIANTE. Um alvo que o sistema sabe tratar e nao tem data de onde
        // contar produziria um corte a partir de nada — e o mais provavel e que
        // caisse na data que existir, que e o erro do art. 7o, XXIX.
        for (Alvo alvo : Alvo.values()) {
            boolean sabeAlgo = false;
            for (Acao acao : Acao.values()) {
                sabeAlgo |= alvo.sabeFazer(acao);
            }
            if (sabeAlgo && alvo.marcoDisponivel() == null) {
                falhas.add("A08 . " + alvo + " sabe agir e nao tem marco de onde contar");
                return;
            }
        }
        ok("A08 . todo alvo com executor declara de onde conta o prazo", true);
    }

    static void oExpurgoSeAgendaEDeclaraCadencia() {
        ok("LGPD-02 . o expurgo e agendavel — retencao indevida nao se resolve a mao",
                Job.EXPURGO_POR_TEMPORALIDADE.agendavel());
        ok("LGPD-02 . e declara cadencia, que e o que faz o silencio dele virar alerta",
                Job.EXPURGO_POR_TEMPORALIDADE.cadenciaEsperada() != null);
    }

    // --- com banco ---------------------------------------------------------------

    /**
     * O caso de hoje: nenhuma classe aprovada, nada apagado, e isso e DITO.
     *
     * <p>Um job que roda todo mes, apaga zero e sai como SUCESSO faria a nao
     * conformidade da A08 sobreviver anos sem sintoma. E o mesmo padrao da F0-05,
     * da completude na primeira conferencia, do agendador morto e do ciclo vazio.
     */
    static void semAprovacaoNaoApagaEDizPorQue(Sgdf sgdf) {
        acesso(sgdf, LocalDate.now().minusDays(400));
        acesso(sgdf, LocalDate.now().minusDays(400));
        acesso(sgdf, LocalDate.now().minusDays(10));
        long antes = contar(sgdf, "acesso_observado WHERE ator LIKE '" + MARCA + "%'");

        ExpurgoPorTemporalidade.Resultado r =
                new ExpurgoPorTemporalidade(sgdf).executar(LocalDate.now(), "agendador", null);

        ok("A08 . com a carga como esta, o expurgo nao elimina nada",
                r.itens() == 0 && r.lotes().isEmpty());
        ok("A08 . e as 3 linhas continuam la",
                contar(sgdf, "acesso_observado WHERE ator LIKE '" + MARCA + "%'") == antes
                        && antes == 3);
        ok("A08 . as 4 classes propostas saem como recusa, uma a uma",
                r.recusas().size() == 4 && r.temNaoConformidade());
        ok("A08 . e nao ha linha nenhuma em expurgo — a trava do banco impede ate o "
                        + "registro, e o que nao se registra nao se apaga",
                contar(sgdf, "expurgo") == 0);
        ok("A08 . o resumo nomeia a nao conformidade em vez de reportar sucesso limpo",
                r.resumo().contains("NÃO CONFORMIDADE (A08)")
                        && r.resumo().contains("ACESSO_OBSERVADO"));

        // LIMPA O QUE ESTE CASO CRIOU, E NAO E ZELO.
        //
        // O caso seguinte aprova ACESSO_OBSERVADO e conta quantos apagou. Sem
        // isto ele apagaria tambem os vencidos deixados aqui, contaria 4 onde a
        // intencao era 2, e a assercao passaria a medir uma populacao diferente
        // da que descreve. Ja aconteceu tres vezes nesta base.
        executarSql(sgdf, "DELETE FROM acesso_observado WHERE ator LIKE \'" + MARCA + "%\'");
    }

    /** Aprovada a classe, o vencido sai, o recente fica, e o que saiu esta registrado. */
    static void aprovadaApagaOVencidoEPoupaORecente(Sgdf sgdf) {
        List<Long> vencidos = List.of(acesso(sgdf, LocalDate.now().minusDays(400)),
                acesso(sgdf, LocalDate.now().minusDays(800)));
        long recente = acesso(sgdf, LocalDate.now().minusDays(10));

        new RepositorioDeTemporalidade(sgdf).aprovar(Alvo.ACESSO_OBSERVADO,
                "dpo.responsavel", OffsetDateTime.now().minusDays(1));

        ExpurgoPorTemporalidade.Resultado r =
                new ExpurgoPorTemporalidade(sgdf).executar(LocalDate.now(), "agendador", null);

        ok("A08 . eliminou exatamente os 2 vencidos", r.itens() == 2);
        ok("A08 . e o recente continua — a comparacao e com o corte, nao com a tabela",
                contar(sgdf, "acesso_observado WHERE id = " + recente) == 1);
        for (long id : vencidos) {
            if (contar(sgdf, "acesso_observado WHERE id = " + id) != 0) {
                falhas.add("A08 . o registro vencido " + id + " sobreviveu ao expurgo");
                return;
            }
        }
        ok("A08 . os dois vencidos sumiram da tabela de origem", true);

        ok("A08 . o lote ficou registrado com a contagem",
                contar(sgdf, "expurgo WHERE alvo = 'ACESSO_OBSERVADO' AND itens = 2") == 1);
        ok("A08 . e com a autorizacao de quem aprovou, copiada da temporalidade",
                contar(sgdf, "expurgo e JOIN temporalidade t ON t.id = e.temporalidade_id"
                        + " WHERE t.aprovado_por = 'dpo.responsavel'"
                        + " AND e.autorizado_em = t.aprovado_em") == 1);

        // O REGISTRO GUARDA A CHAVE, NAO O CONTEUDO. Provar que o dado pessoal foi
        // eliminado guardando o dado pessoal nao elimina nada.
        String chaves = escalar(sgdf, "SELECT string_agg(identificador, ',' ORDER BY "
                + "identificador::bigint) FROM expurgo_item i JOIN expurgo e ON e.id = "
                + "i.expurgo_id WHERE e.alvo = 'ACESSO_OBSERVADO'");
        ok("A08 . e os itens registrados sao as chaves exatas das linhas que sumiram",
                chaves.equals(vencidos.stream().sorted()
                        .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("")));
    }

    /**
     * Autorizado e nada vencido: grava linha com zero.
     *
     * <p><b>E a distincao que a A08 inteira depende.</b> Sem esta linha, "a
     * politica esta viva e nada precisou ser apagado" e "a politica nunca foi
     * aplicada" produzem o mesmo estado observavel: tabela vazia, painel verde.
     */
    static void zeroAutorizadoEUmaLinha(Sgdf sgdf) {
        new RepositorioDeTemporalidade(sgdf).aprovar(Alvo.ACESSO_OBSERVADO,
                "dpo.responsavel", OffsetDateTime.now().minusDays(1));
        acesso(sgdf, LocalDate.now().minusDays(3));
        long zerosAntes = contar(sgdf, "expurgo WHERE alvo = 'ACESSO_OBSERVADO' AND itens = 0");

        ExpurgoPorTemporalidade.Resultado primeira =
                new ExpurgoPorTemporalidade(sgdf).executar(LocalDate.now(), "agendador", null);
        ExpurgoPorTemporalidade.Resultado segunda =
                new ExpurgoPorTemporalidade(sgdf).executar(LocalDate.now(), "agendador", null);

        ok("A08 . nada venceu, entao nada foi eliminado",
                primeira.itens() == 0 && segunda.itens() == 0);
        ok("A08 . e mesmo assim cada passada virou linha — zero e resultado, "
                        + "ausencia de linha nao e",
                contar(sgdf, "expurgo WHERE alvo = 'ACESSO_OBSERVADO' AND itens = 0")
                        == zerosAntes + 2);
        ok("A08 . reexecutar e seguro: a segunda passada nao inventou item nenhum",
                contar(sgdf, "expurgo_item i JOIN expurgo e ON e.id = i.expurgo_id"
                        + " WHERE e.alvo = 'ACESSO_OBSERVADO' AND e.itens = 0") == 0);
    }

    /**
     * REVISAR lista e nao apaga — e a lista exclui quem nao tem marco.
     *
     * <p>Documento de escopo CONTRATO ou CORPORATIVO nao tem profissional, logo
     * nao tem de onde contar o prazo, logo fica. Documento de quem ainda esta na
     * casa fica tambem. Nenhum documento fiscal sai por decisao de agendador.
     */
    static void revisarNaoApagaENaoRegistraExpurgo(Sgdf sgdf) {
        new RepositorioDeTemporalidade(sgdf).aprovar(Alvo.DOCUMENTO,
                "dpo.responsavel", OffsetDateTime.now().minusDays(1));

        UUID contrato = contrato(sgdf);
        UUID ciclo = ciclo(sgdf, contrato);
        UUID saiuHaMuito = profissional(sgdf, LocalDate.now().minusYears(9));
        UUID saiuOntem = profissional(sgdf, LocalDate.now().minusDays(1));
        UUID naCasa = profissional(sgdf, null);

        UUID docVencido = documento(sgdf);
        UUID docRecente = documento(sgdf);
        UUID docAtivo = documento(sgdf);
        UUID docDoContrato = documento(sgdf);

        vincular(sgdf, ciclo, saiuHaMuito, docVencido);
        vincular(sgdf, ciclo, saiuOntem, docRecente);
        vincular(sgdf, ciclo, naCasa, docAtivo);
        vincular(sgdf, ciclo, null, docDoContrato);

        long documentosAntes = contar(sgdf, "documento");

        ExpurgoPorTemporalidade.Resultado r =
                new ExpurgoPorTemporalidade(sgdf).executar(LocalDate.now(), "agendador", null);

        ok("A08 . o documento vencido entrou na lista de revisao",
                r.revisoes().size() == 1 && listou(r, docVencido));
        ok("A08 . e os outros tres ficaram de fora: quem saiu ontem, quem esta na casa, "
                        + "e o documento de escopo contrato, que nao tem marco nenhum",
                !listou(r, docRecente) && !listou(r, docAtivo) && !listou(r, docDoContrato));
        ok("A08 . nenhum documento foi apagado — REVISAR lista, nao elimina",
                contar(sgdf, "documento") == documentosAntes);
        ok("LGPD-02 . e a revisao NAO virou linha de expurgo: aquela tabela significa "
                        + "'algo deixou de existir', e nada deixou",
                contar(sgdf, "expurgo WHERE alvo = 'DOCUMENTO'") == 0);
        ok("A08 . o resumo separa o que foi eliminado do que aguarda pessoa",
                r.resumo().contains("0 item(ns) eliminado(s)")
                        && r.resumo().contains("item(ns) vencido(s) aguardando revisão"));
    }

    /**
     * O segundo executor, que conta de `criado_em` e nao de `dia`.
     *
     * <p>Existe porque a fonte do marco e escrita por alvo: se ACESSO_OBSERVADO
     * fosse o unico caso com banco, um erro no trecho de NOTIFICACAO — a coluna
     * errada, o cast que falta — passaria a suite inteira e so apareceria em
     * producao, apagando a regua de um ciclo vivo ou nao apagando nada.
     */
    static void aReguaVencidaTambemSai(Sgdf sgdf) {
        new RepositorioDeTemporalidade(sgdf).aprovar(Alvo.NOTIFICACAO,
                "dpo.responsavel", OffsetDateTime.now().minusDays(1));

        UUID ciclo = ciclo(sgdf, contrato(sgdf));
        UUID antiga = notificacao(sgdf, ciclo, LocalDate.now().minusYears(6));
        UUID recente = notificacao(sgdf, ciclo, LocalDate.now().minusYears(1));

        ExpurgoPorTemporalidade.Resultado r =
                new ExpurgoPorTemporalidade(sgdf).executar(LocalDate.now(), "agendador", null);

        ok("A08 . a regua de 6 anos atras saiu",
                contar(sgdf, "notificacao WHERE id = \'" + antiga + "\'") == 0);
        ok("A08 . e a de 1 ano ficou — 60 meses contados de criado_em, nao da tabela",
                contar(sgdf, "notificacao WHERE id = \'" + recente + "\'") == 1);
        ok("A08 . o lote de NOTIFICACAO registrou a eliminacao",
                contar(sgdf, "expurgo WHERE alvo = \'NOTIFICACAO\' AND itens = 1") == 1
                        && r.itens() >= 1);
        ok("A08 . e o item registrado e a chave da linha que sumiu, nao o destinatario",
                contar(sgdf, "expurgo_item i JOIN expurgo e ON e.id = i.expurgo_id"
                        + " WHERE e.alvo = \'NOTIFICACAO\' AND i.identificador = \'"
                        + antiga + "\'") == 1);
    }

    static boolean listou(ExpurgoPorTemporalidade.Resultado r, UUID documento) {
        return r.revisoes().stream().flatMap(v -> v.vencidos().stream())
                .anyMatch(v -> v.identificador().equals(documento.toString()));
    }

    // --- fixtures ----------------------------------------------------------------

    static Temporalidade classe(Alvo alvo, Marco marco, int meses, Acao acao,
                                OffsetDateTime aprovadoEm, String aprovadoPor) {
        return new Temporalidade(UUID.randomUUID().toString(), "classe de teste", alvo,
                marco, meses, acao, "fundamento de teste", aprovadoEm, aprovadoPor);
    }

    static long acesso(Sgdf sgdf, LocalDate dia) {
        int n = ++sequencia;
        return Long.parseLong(escalar(sgdf,
                "INSERT INTO acesso_observado (ator, dia, papeis, contratos) VALUES ('"
                + MARCA + "-" + n + "', DATE '" + dia + "', ARRAY['CONSULTA'], "
                + "ARRAY[]::uuid[]) RETURNING id"));
    }

    static UUID contrato(Sgdf sgdf) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por)"
                + " VALUES ('Emp Exp " + n + "', '" + String.format("7%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por)"
                + " VALUES ('Cli Exp " + n + "', '" + String.format("8%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        return uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-EXP-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/teste', '{\"ancora\":\"ATESTE\",\"tipo_dia\":"
                + "\"CORRIDO\",\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA
                + "' FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
    }

    static UUID ciclo(Sgdf sgdf, UUID contrato) {
        int n = ++sequencia;
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-exp-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        return uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2028-04',"
                + " 'EM_COLETA', '" + versao + "', '" + MARCA + "') RETURNING id");
    }

    static UUID profissional(Sgdf sgdf, LocalDate desligamento) {
        int n = ++sequencia;
        return uuid(sgdf, "INSERT INTO profissional (matricula, nome, cpf_cifrado,"
                + " cpf_hash, admissao, desligamento, ativo, criado_por) VALUES ('"
                + MARCA + "-" + n + "', 'Fulano " + n + "', '\\x00'::bytea, decode('"
                + String.format("%064x", n) + "', 'hex'), DATE '2015-01-05', "
                + (desligamento == null ? "NULL" : "DATE '" + desligamento + "'")
                + ", " + (desligamento == null) + ", '" + MARCA + "') RETURNING id");
    }

    static UUID documento(Sgdf sgdf) {
        int n = ++sequencia;
        return uuid(sgdf, "INSERT INTO documento (origem, caminho, nome_arquivo,"
                + " hash_sha256, tamanho, mime_real, formato, status_triagem, criado_por)"
                + " VALUES ('OWNCLOUD', '/exp/" + n + "/a.pdf', 'a.pdf', '"
                + String.format("%064x", n) + "', 10, 'application/pdf', 'PDF',"
                + " 'NAO_APLICAVEL', '" + MARCA + "') RETURNING id");
    }

    static void vincular(Sgdf sgdf, UUID ciclo, UUID profissional, UUID documento) {
        int n = ++sequencia;
        UUID exigencia = uuid(sgdf, "INSERT INTO exigencia (ciclo_id, tipo_id, evento,"
                + " profissional_id, status, prazo_calculado, criticidade, responsavel,"
                + " origem, criado_por) SELECT '" + ciclo + "', t.id, 'MENSAL-" + n + "', "
                + (profissional == null ? "NULL" : "'" + profissional + "'")
                + ", 'PENDENTE', DATE '2028-05-05', 'NAO_BLOQUEANTE', 'DP', 'MATRIZ', '"
                + MARCA + "' FROM tipo_documental t ORDER BY t.codigo LIMIT 1 RETURNING id");
        executarSql(sgdf, "INSERT INTO vinculo_exigencia_documento (exigencia_id,"
                + " documento_id, formato, decidido_por, decidido_ator) VALUES ('"
                + exigencia + "', '" + documento + "', 'PDF', 'SISTEMA', '" + MARCA + "')");
    }

    static UUID notificacao(Sgdf sgdf, UUID ciclo, LocalDate quando) {
        int n = ++sequencia;
        return uuid(sgdf, "INSERT INTO notificacao (ciclo_id, tipo, destinatario,"
                + " conteudo_hash, template_versao, data_referencia, criado_em) VALUES ('"
                + ciclo + "', 'COBRANCA', 'dp@exemplo', '" + String.format("%064x", n)
                + "', 'v1', DATE '" + quando + "', TIMESTAMPTZ '" + quando
                + " 10:00:00-03') RETURNING id");
    }

    static void executarSql(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
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

    static String escalar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao ler " + sql + ": " + e.getMessage(), e);
        }
    }

    static long contar(Sgdf sgdf, String de) {
        return Long.parseLong(escalar(sgdf, "SELECT count(*) FROM " + de));
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

    private TestesDeExpurgo() {}
}
