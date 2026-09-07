package br.com.engesoftware.sgdf.notificacao;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeNotificacao;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeParametro;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ativacao de notificacoes por contrato — historia F3-02.
 *
 * <p>Criterio de aceite: <i>"regua completa D-2/D+0/D+2/D+5; max. 1
 * e-mail/area/dia"</i>. A regua e a consolidacao vieram na F1-09 e continuam
 * verificadas la; o que esta historia acrescenta e QUEM decide, por contrato.
 */
public final class TestesDeAtivacao {

    static final String MARCA = "teste-ativacao";
    static final String MOTIVO = "contrato piloto aprovado em reuniao da DAF de 03/09";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("aPermissaoPerigosaNaoSeHerda",
                TestesDeAtivacao::aPermissaoPerigosaNaoSeHerda);
        executar("aOrdemDosPortoes", TestesDeAtivacao::aOrdemDosPortoes);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("ligarFalhaNaTrocaENaoNoUso",
                            () -> ligarFalhaNaTrocaENaoNoUso(sgdf));
                    executar("aTentativaNegadaFicaNaTrilha",
                            () -> aTentativaNegadaFicaNaTrilha(sgdf));
                    executar("desligarEsempreAceito", () -> desligarEsempreAceito(sgdf));
                    executar("oContratoSemParametroEstaEmSombra",
                            () -> oContratoSemParametroEstaEmSombra(sgdf));
                    executar("aTelaMostraAOrigemDaDecisao",
                            () -> aTelaMostraAOrigemDaDecisao(sgdf));
                    executar("aReguaContinuaGravandoEmSombra",
                            () -> aReguaContinuaGravandoEmSombra(sgdf));
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

    // --- a decisao, sem banco --------------------------------------------------

    /**
     * O modo sombra herda; a adesao nao.
     *
     * <p>Resolver as duas por escopo — contrato, senao cliente, senao global —
     * faria alguem desligando a chave global para testar UM contrato ativar a
     * cobranca de TODOS, e o sintoma seria e-mail saindo para areas que nunca
     * souberam que o sistema comecou a cobra-las.
     */
    static void aPermissaoPerigosaNaoSeHerda() {
        // Com transporte, para conseguir olhar o caminho todo.
        Ativacao semAdesao = Ativacao.decidir(false, false, true);
        ok("F3-02 . contrato sem parametro proprio NAO envia, mesmo com a chave "
                        + "global desligada",
                !semAdesao.envioAtivo()
                        && Ativacao.Origem.SEM_ADESAO_DO_CONTRATO == semAdesao.origem());
        ok("F3-02 . e o motivo diz que a adesao nao se herda",
                semAdesao.motivo().contains("não se") && semAdesao.motivo().contains("herda"));

        Ativacao comAdesao = Ativacao.decidir(false, true, true);
        ok("F3-02 . com adesao explicita e a chave global desligada, envia",
                comAdesao.envioAtivo()
                        && Ativacao.Origem.ADESAO_DO_CONTRATO == comAdesao.origem());

        // A CHAVE GLOBAL E DE DESLIGAR, E DESLIGAR VENCE SEMPRE.
        Ativacao sombraVence = Ativacao.decidir(true, true, true);
        ok("F3-02 . a chave global ligada vence a adesao do contrato",
                !sombraVence.envioAtivo()
                        && Ativacao.Origem.SOMBRA_GLOBAL == sombraVence.origem());
        ok("Cap. 11.2 . e nesse caso a regua GRAVA em vez de enviar",
                sombraVence.gravaSemEnviar() && !sombraVence.incoerente());
    }

    /**
     * O portao de transporte vem antes do de adesao, e a ordem e o ponto.
     *
     * <p>Desligar a chave global e uma DECLARACAO DE INTENCAO DE ENVIAR. Checar
     * a adesao primeiro faria "global desligada, ninguem aderido" cair em
     * sombra silenciosa — e quem desligou passaria a esperar e-mails que nunca
     * sairiam. A F1-09 recusou esse silencio; ele continua recusado.
     */
    static void aOrdemDosPortoes() {
        Ativacao semTransporte = Ativacao.decidir(false, false, false);
        ok("F1-09 . global desligada e sem transporte e INCOERENTE, nao sombra",
                semTransporte.incoerente()
                        && Ativacao.Origem.SEM_TRANSPORTE == semTransporte.origem());
        ok("F1-09 . e nao se resolve como 'grava sem enviar'",
                !semTransporte.gravaSemEnviar());

        ok("F1-09 . o mesmo vale com o contrato aderido",
                Ativacao.decidir(false, true, false).incoerente());

        // Com a chave global LIGADA, a falta de transporte nao e incoerencia
        // nenhuma: ninguem declarou intencao de enviar.
        Ativacao sombra = Ativacao.decidir(true, false, false);
        ok("Cap. 11.2 . com a chave global ligada, a falta de transporte e irrelevante",
                sombra.gravaSemEnviar() && !sombra.incoerente());
        ok("Cap. 11.2 . e a origem e a chave global, nao o transporte",
                Ativacao.Origem.SOMBRA_GLOBAL == sombra.origem());
    }

    // --- com banco --------------------------------------------------------------

    /**
     * O criterio que da valor a esta historia.
     *
     * <p>O RepositorioDeNotificacao ja recusava executar sem transporte — mas
     * NA EXECUCAO, que acontece um dia depois de alguem virar a chave. Nesse
     * intervalo quem virou acredita ter ativado a cobranca e a area acredita que
     * sera cobrada; as duas crencas sao falsas ao mesmo tempo.
     */
    static void ligarFalhaNaTrocaENaoNoUso(Sgdf sgdf) {
        UUID contrato = contrato(sgdf);
        RepositorioDeParametro repo = new RepositorioDeParametro(sgdf);

        boolean recusou = false;
        try {
            repo.ativarEnvio(contrato, MOTIVO, "admin.tec", "ADMIN_SISTEMA");
        } catch (RepositorioDeParametro.SemTransporte e) {
            recusou = true;
        }
        ok("F3-02 . ligar o envio sem transporte e recusado NO MOMENTO DE LIGAR",
                recusou);
        ok("F3-02 . e o parametro NAO foi gravado — nada ficou meio ligado",
                0 == contar(sgdf, "parametro WHERE contrato_id = '" + contrato
                        + "' AND chave = '" + Ativacao.CHAVE_ENVIO_ATIVO + "'"));
        ok("F3-02 . o contrato continua em sombra",
                !repo.ativacaoDe(contrato).envioAtivo());

        boolean semMotivo = false;
        try {
            repo.ativarEnvio(contrato, "porque sim", "admin.tec", "ADMIN_SISTEMA");
        } catch (RepositorioDeParametro.AtivacaoInvalida e) {
            semMotivo = true;
        }
        ok("F3-02 . e ativar sem motivo substantivo e recusado antes disso — alguem "
                + "vai perguntar quem autorizou", semMotivo);
    }

    /** A tentativa negada e o fato mais auditavel do fluxo, e some por ser negativa. */
    static void aTentativaNegadaFicaNaTrilha(Sgdf sgdf) {
        UUID contrato = contrato(sgdf);
        try {
            new RepositorioDeParametro(sgdf).ativarEnvio(contrato, MOTIVO, "admin.tec",
                    "ADMIN_SISTEMA");
        } catch (RepositorioDeParametro.SemTransporte e) {
            // esperado
        }
        ok("Cap. 16 . a tentativa negada sobrevive a recusa",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'NOTIFICACAO_ATIVAR'"
                        + " AND resultado = 'NEGADO' AND objeto_id = '" + contrato + "'"));
        ok("Cap. 16 . com quem tentou",
                "admin.tec".equals(escalar(sgdf, "SELECT ator FROM log_auditoria"
                        + " WHERE acao = 'NOTIFICACAO_ATIVAR' AND objeto_id = '" + contrato
                        + "'")));
    }

    /** Voltar para sombra e sempre a direcao segura: sem porta e sem motivo. */
    static void desligarEsempreAceito(Sgdf sgdf) {
        UUID contrato = contrato(sgdf);
        RepositorioDeParametro repo = new RepositorioDeParametro(sgdf);
        repo.desativarEnvio(contrato, "operador", "PUBLICADOR_FIN");

        ok("F3-02 . desligar e aceito sem transporte e sem motivo",
                "false".equals(escalar(sgdf, "SELECT valor #>> '{}' FROM parametro"
                        + " WHERE contrato_id = '" + contrato + "' AND chave = '"
                        + Ativacao.CHAVE_ENVIO_ATIVO + "'")));
        ok("Cap. 16 . e fica na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'NOTIFICACAO_DESATIVAR'"
                        + " AND objeto_id = '" + contrato + "'"));

        // Desligar duas vezes nao e erro: e o estado desejado.
        repo.desativarEnvio(contrato, "operador", "PUBLICADOR_FIN");
        ok("F3-02 . desligar de novo e idempotente",
                1 == contar(sgdf, "parametro WHERE contrato_id = '" + contrato
                        + "' AND chave = '" + Ativacao.CHAVE_ENVIO_ATIVO + "'"));
    }

    static void oContratoSemParametroEstaEmSombra(Sgdf sgdf) {
        UUID contrato = contrato(sgdf);
        Ativacao a = new RepositorioDeParametro(sgdf).ativacaoDe(contrato);
        ok("F3-02 . contrato recem-cadastrado nao envia", !a.envioAtivo());
        ok("Cap. 11.2 . e a origem e a chave global, que esta ligada por padrao",
                Ativacao.Origem.SOMBRA_GLOBAL == a.origem());
    }

    static void aTelaMostraAOrigemDaDecisao(Sgdf sgdf) {
        UUID contrato = contrato(sgdf);
        List<RepositorioDeParametro.SituacaoDoContrato> linhas =
                new RepositorioDeParametro(sgdf).situacaoDosContratos();
        RepositorioDeParametro.SituacaoDoContrato minha = linhas.stream()
                .filter(l -> l.contratoId().equals(contrato)).findFirst().orElseThrow();

        ok("F3-02 . a tela traz o contrato com numero e cliente",
                minha.numero().startsWith("CT-ATV") && minha.cliente() != null);
        ok("F3-02 . e a ORIGEM da decisao, nao so o sim/nao",
                minha.ativacao().origem() != null && !minha.ativacao().motivo().isBlank());
        ok("F3-02 . nenhum contrato aparece ativo hoje — nao ha transporte",
                linhas.stream().noneMatch(l -> l.ativacao().envioAtivo()));
    }

    /** A F1-09 continua valendo: a regua grava, e a segunda execucao do dia nao duplica. */
    static void aReguaContinuaGravandoEmSombra(Sgdf sgdf) {
        UUID contrato = contrato(sgdf);
        UUID ciclo = ciclo(sgdf, contrato);
        RepositorioDeNotificacao repo = new RepositorioDeNotificacao(sgdf);
        repo.executar(ciclo, LocalDate.now());
        long depois = contar(sgdf, "notificacao WHERE ciclo_id = '" + ciclo + "'");
        repo.executar(ciclo, LocalDate.now());

        ok("Cap. 11.2 . a regua continua gravando com a decisao por contrato",
                depois == contar(sgdf, "notificacao WHERE ciclo_id = '" + ciclo + "'"));
        ok("Cap. 11.2 . e tudo o que grava e modo sombra",
                0 == contar(sgdf, "notificacao WHERE ciclo_id = '" + ciclo
                        + "' AND modo_sombra = false"));
    }

    // -------------------------------------------------------------------------

    static UUID contrato(Sgdf sgdf) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Atv " + n + "', '" + String.format("1%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Atv " + n + "', '" + String.format("2%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        return uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-ATV-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/teste', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
    }

    static UUID ciclo(Sgdf sgdf, UUID contrato) {
        int n = ++sequencia;
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-atv-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        return uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2028-03',"
                + " 'EM_COLETA', '" + versao + "', '" + MARCA + "') RETURNING id");
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
            throw new IllegalStateException("falha ao ler " + sql, e);
        }
    }

    static long contar(Sgdf sgdf, String de) {
        return Long.parseLong(escalar(sgdf, "SELECT count(*) FROM " + de));
    }

    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM notificacao WHERE ciclo_id IN (SELECT id FROM ciclo"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM parametro WHERE contrato_id IN (SELECT id FROM contrato_servico"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
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

    private TestesDeAtivacao() {}
}
