package br.com.engesoftware.sgdf.triagem;

import br.com.engesoftware.sgdf.classificacao.Bonus;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeAlias;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeTriagem;
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
 * Testes da triagem com escrita — historia F1-06.
 *
 * <p>Criterio de aceite: <i>"confirmar tira da fila, cria alias e o mesmo padrao
 * nao retorna"</i>. As tres partes sao testadas separadamente porque falham
 * separadamente: tirar da fila sem gravar o vinculo esconde o documento sem
 * conta-lo como entrega; gravar o alias sem que ninguem o leia faz o mesmo
 * arquivo voltar no mes seguinte.
 *
 * <p>A parte do padrao de nome nao precisa de banco. O resto precisa do
 * PostgreSQL de verdade, pelas razoes de TestesDePersistencia.
 */
public final class TestesDeTriagem {

    static final String MARCA = "teste-triagem";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("padraoTiraOQueVaria", TestesDeTriagem::padraoTiraOQueVaria);
        executar("padraoNuncaCarregaCpf", TestesDeTriagem::padraoNuncaCarregaCpf);
        executar("padraoCurtoDemaisERecusado", TestesDeTriagem::padraoCurtoDemaisERecusado);
        executar("pedidoRecusaDecisaoSemMotivo", TestesDeTriagem::pedidoRecusaDecisaoSemMotivo);
        executar("aliasFechaAFaixaDeTriagem", TestesDeTriagem::aliasFechaAFaixaDeTriagem);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte de banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("confirmarVinculaEEnsina", () -> confirmarVinculaEEnsina(sgdf));
                    executar("oMesmoPadraoNaoRetorna", () -> oMesmoPadraoNaoRetorna(sgdf));
                    executar("decidirDuasVezesERecusado", () -> decidirDuasVezesERecusado(sgdf));
                    executar("reclassificarDevolveAPendente",
                            () -> reclassificarDevolveAPendente(sgdf));
                    executar("ilegivelNaoEnsinaNada", () -> ilegivelNaoEnsinaNada(sgdf));
                    executar("aliasDeOutroTipoNaoDesfazAConfirmacao",
                            () -> aliasDeOutroTipoNaoDesfazAConfirmacao(sgdf));
                    executar("filaERecortadaPorContrato", () -> filaERecortadaPorContrato(sgdf));
                    executar("atorSemContratoNaoVeTudo", () -> atorSemContratoNaoVeTudo(sgdf));
                    executar("aDecisaoFicaNaTrilha", () -> aDecisaoFicaNaTrilha(sgdf));
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

    // --- o padrao do nome, sem banco -----------------------------------------

    static void padraoTiraOQueVaria() {
        ok("F1-06 . a matricula sai e o tipo fica",
                "contracheque".equals(PadraoDeNome.de("1041601__CONTRACHEQUE.pdf").normalizado()));
        ok("F1-06 . a competencia no fim do nome tambem sai",
                "relac_de_va_vr_mensal".equals(
                        PadraoDeNome.de("RELAC_A_O_DE_VA_VR__MENSAL_06.2026.pdf").normalizado()));
        ok("F1-06 . acento nao muda o padrao",
                PadraoDeNome.de("RELAÇÃO_PLANO_DE_SAÚDE.pdf").normalizado()
                        .equals(PadraoDeNome.de("RELACAO_PLANO_DE_SAUDE.pdf").normalizado()));
        ok("F1-06 . e o do mes seguinte da o MESMO padrao — e o criterio de aceite",
                PadraoDeNome.de("1041601__CONTRACHEQUE.pdf").normalizado()
                        .equals(PadraoDeNome.de("1041602__CONTRACHEQUE.pdf").normalizado()));
        ok("F1-06 . nome sem extensao tambem e lido",
                "comprovante_pg_folha".equals(
                        PadraoDeNome.de("COMPROVANTE_PG_FOLHA").normalizado()));
    }

    /**
     * A massa real trouxe nome de arquivo com CPF dentro. tipo_alias e tabela de
     * configuracao: ninguem procuraria dado pessoal ali, e ela nao esta em
     * inventario nenhum do cap. 17.
     */
    static void padraoNuncaCarregaCpf() {
        String comSeparador = PadraoDeNome.de("052.190.471-40 folha.pdf").normalizado();
        ok("SEC-02 . o CPF separado por pontos nao entra no alias",
                "folha".equals(comSeparador));

        String colado = PadraoDeNome.de("folha05219047140.pdf").normalizado();
        ok("SEC-02 . nem o colado dentro da palavra", "folha".equals(colado));

        for (String nome : List.of("052.190.471-40 folha.pdf", "folha05219047140.pdf",
                "1041601__CONTRACHEQUE.pdf")) {
            String padrao = PadraoDeNome.de(nome).normalizado();
            ok("SEC-02 . nenhum digito sobrevive em \"" + padrao + "\"",
                    padrao != null && padrao.chars().noneMatch(Character::isDigit));
        }
    }

    static void padraoCurtoDemaisERecusado() {
        PadraoDeNome so = PadraoDeNome.de("2026-06.pdf");
        ok("F1-06 . nome so de numeros nao ensina nada", !so.aprendivel());
        ok("F1-06 . e a recusa explica por que", so.recusa().contains("curto demais"));
        ok("F1-06 . duas letras nao discriminam tipo",
                !PadraoDeNome.de("NF_06.pdf").aprendivel());
        ok("F1-06 . cinco letras discriminam — e o menor codigo real da massa",
                "fopag".equals(PadraoDeNome.de("FOPAG.pdf").normalizado()));
    }

    static void pedidoRecusaDecisaoSemMotivo() {
        ok("Cap. 12 . reclassificar sem tipo e recusado",
                recusa(() -> new PedidoDeTriagem.Reclassificar(UUID.randomUUID(), null,
                        "o layout e de outra operadora")));
        ok("Cap. 12 . reclassificar sem motivo e recusado",
                recusa(() -> new PedidoDeTriagem.Reclassificar(UUID.randomUUID(),
                        UUID.randomUUID(), "erro")));
        ok("Cap. 12 . ilegivel sem motivo e recusado",
                recusa(() -> new PedidoDeTriagem.Ilegivel(UUID.randomUUID(), " ")));
        ok("Cap. 12 . confirmar NAO exige motivo — concordar nao e corrigir",
                new PedidoDeTriagem.Confirmar(UUID.randomUUID()) != null);
    }

    /**
     * O bonus corrobora, nao elege. 0,10 sobre 0,86 fecha a faixa de triagem;
     * sobre 0,72 nao fecha — e nao deve fechar.
     */
    static void aliasFechaAFaixaDeTriagem() {
        double peso = RepositorioDeAlias.PESO_DO_ALIAS;
        ok("F1-06 . o alias fecha a faixa para quem o conteudo ja pos perto",
                0.86 + peso >= 0.95);
        ok("F1-06 . e NAO fecha para quem o conteudo deixou longe",
                0.72 + peso < 0.95);
        ok("Cap. 8.3 . o peso cabe no teto que o Bonus impoe",
                new Bonus(java.util.Map.of("TST.X", peso)).para("TST.X") == peso);
    }

    // --- contra o banco ------------------------------------------------------

    /** Criterio de aceite, primeira e segunda partes: tira da fila e cria alias. */
    static void confirmarVinculaEEnsina(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CONTRACHEQUE_ALFA.pdf");
        RepositorioDeTriagem repo = new RepositorioDeTriagem(sgdf);

        ok("F1-06 . a candidatura aberta aparece na fila",
                repo.fila(java.util.Set.of(f.contrato), 50).size() == 1);

        ResultadoDaTriagem r = repo.decidir(new PedidoDeTriagem.Confirmar(f.candidatura),
                "fulano", "PUBLICADOR_AP");

        ok("F1-06 . confirmar tira da fila",
                repo.fila(java.util.Set.of(f.contrato), 50).isEmpty());
        ok("F1-06 . e grava o vinculo, que e o que faz a entrega contar",
                1 == contar(sgdf, "vinculo_exigencia_documento WHERE exigencia_id = '"
                        + f.exigencia + "' AND decidido_por = 'USUARIO'"));
        ok("Cap. 6.1 . a exigencia vai de EM_TRIAGEM para RECEBIDO",
                "RECEBIDO".equals(r.statusExigencia()));
        ok("F1-06 . o alias aprendido e o padrao estavel",
                "contracheque_alfa".equals(r.aliasAprendido()));
        ok("Cap. 8.3 . e fica marcado como vindo da triagem",
                "TRIAGEM".equals(escalar(sgdf, "SELECT origem FROM tipo_alias "
                        + "WHERE texto_normalizado = 'contracheque_alfa'")));
    }

    /**
     * Terceira parte do criterio: o mesmo padrao nao retorna.
     *
     * <p>Gravar o alias so cumpre a promessa se alguem o LER. Este teste e o que
     * liga as duas pontas — sem ele, "cria alias" seria verdade e "nao retorna"
     * seria fé.
     */
    static void oMesmoPadraoNaoRetorna(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "RELATORIO_BRAVO_06.2026.pdf");
        RepositorioDeAlias aliases = new RepositorioDeAlias(sgdf);

        ok("F1-06 . antes de confirmar, o nome nao concede bonus nenhum",
                aliases.doNome("RELATORIO_BRAVO_07.2026.pdf").para(f.tipoCodigo) == 0.0);

        new RepositorioDeTriagem(sgdf).decidir(new PedidoDeTriagem.Confirmar(f.candidatura),
                "fulano", "PUBLICADOR_AP");

        // O arquivo do MES SEGUINTE — nome diferente, padrao igual.
        Bonus depois = aliases.doNome("RELATORIO_BRAVO_07.2026.pdf");
        ok("F1-06 . depois de confirmar, o arquivo do mes seguinte ja chega com bonus",
                depois.para(f.tipoCodigo) == RepositorioDeAlias.PESO_DO_ALIAS);
        ok("F1-06 . e o bonus e do tipo confirmado, nao de outro",
                depois.para("TST.NAO_EXISTE") == 0.0);
    }

    /** Duas pessoas na mesma fila. A segunda recebe recusa, nao um segundo vinculo. */
    static void decidirDuasVezesERecusado(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CONTRACHEQUE_CHARLIE.pdf");
        RepositorioDeTriagem repo = new RepositorioDeTriagem(sgdf);
        repo.decidir(new PedidoDeTriagem.Confirmar(f.candidatura), "fulano", "PUBLICADOR_AP");

        boolean recusou = false;
        try {
            repo.decidir(new PedidoDeTriagem.Confirmar(f.candidatura), "sicrano",
                    "PUBLICADOR_AP");
        } catch (RepositorioDeTriagem.CandidaturaJaDecidida e) {
            recusou = true;
        }
        ok("F1-06 . decidir de novo e recusado", recusou);
        ok("F1-06 . e nao existe um segundo vinculo para uma entrega so",
                1 == contar(sgdf, "vinculo_exigencia_documento WHERE exigencia_id = '"
                        + f.exigencia + "'"));
    }

    /**
     * Reclassificar diz o que o documento E, nao que esta exigencia foi
     * satisfeita: ela continua sem o documento que esperava.
     */
    static void reclassificarDevolveAPendente(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "RELATORIO_DELTA.pdf");
        UUID outroTipo = umTipo(sgdf, "TST.TRI_D2");
        ResultadoDaTriagem r = new RepositorioDeTriagem(sgdf).decidir(
                new PedidoDeTriagem.Reclassificar(f.candidatura, outroTipo,
                        "o layout e da outra operadora, nao desta"),
                "fulano", "PUBLICADOR_AP");

        ok("Cap. 6.1 . a exigencia volta a PENDENTE", "PENDENTE".equals(r.statusExigencia()));
        ok("F1-06 . e NAO ganha vinculo — ela continua sem o documento que esperava",
                0 == contar(sgdf, "vinculo_exigencia_documento WHERE exigencia_id = '"
                        + f.exigencia + "'"));
        ok("F1-06 . o documento passa a ser do tipo escolhido",
                outroTipo.toString().equals(escalar(sgdf, "SELECT tipo_id::text FROM documento "
                        + "WHERE id = '" + f.documento + "'")));
        ok("F1-06 . e o alias e aprendido para o tipo ESCOLHIDO, nao para o proposto",
                outroTipo.toString().equals(escalar(sgdf, "SELECT tipo_id::text FROM tipo_alias "
                        + "WHERE texto_normalizado = 'relatorio_delta'")));

        boolean recusou = false;
        try {
            Fixture g = fixture(sgdf, "RELATORIO_ECHO.pdf");
            new RepositorioDeTriagem(sgdf).decidir(
                    new PedidoDeTriagem.Reclassificar(g.candidatura, g.tipo,
                            "quis dizer que concordo"), "fulano", "PUBLICADOR_AP");
        } catch (RepositorioDeTriagem.DecisaoInvalida e) {
            recusou = true;
        }
        ok("P01 . reclassificar para o tipo proposto e recusado — concordar nao e corrigir",
                recusou);
    }

    static void ilegivelNaoEnsinaNada(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CONTRACHEQUE_FOXTROT.pdf");
        ResultadoDaTriagem r = new RepositorioDeTriagem(sgdf).decidir(
                new PedidoDeTriagem.Ilegivel(f.candidatura,
                        "digitalizado torto, o cabecalho esta cortado"),
                "fulano", "PUBLICADOR_AP");

        ok("Cap. 12 . ilegivel devolve a exigencia a PENDENTE",
                "PENDENTE".equals(r.statusExigencia()));
        ok("F1-06 . e nao aprende alias — palpite nao vira conhecimento", !r.aprendeu());
        ok("F1-06 . nada foi gravado em tipo_alias",
                0 == contar(sgdf, "tipo_alias WHERE texto_normalizado = "
                        + "'contracheque_foxtrot'"));
        ok("Cap. 12 . o motivo fica gravado, para quem reentrega",
                escalar(sgdf, "SELECT decisao_motivo FROM candidatura WHERE id = '"
                        + f.candidatura + "'").contains("cabecalho"));
    }

    /**
     * A colisao de alias e aviso, nao excecao: a decisao sobre ESTE documento
     * continua valida, e desfaze-la por causa de um efeito colateral seria punir
     * quem triou por um problema de cadastro.
     */
    static void aliasDeOutroTipoNaoDesfazAConfirmacao(Sgdf sgdf) {
        UUID ocupante = umTipo(sgdf, "TST.TRI_OCUPA");
        executar(sgdf, "INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, "
                + "origem, criado_por) VALUES ('" + ocupante + "', 'x', 'contracheque_golf', "
                + "'LEGADO', '" + MARCA + "')");

        Fixture f = fixture(sgdf, "CONTRACHEQUE_GOLF.pdf");
        ResultadoDaTriagem r = new RepositorioDeTriagem(sgdf).decidir(
                new PedidoDeTriagem.Confirmar(f.candidatura), "fulano", "PUBLICADOR_AP");

        ok("E-02 . o alias nao e roubado do outro tipo", !r.aprendeu());
        ok("F1-06 . mas a confirmacao vale — o vinculo existe",
                1 == contar(sgdf, "vinculo_exigencia_documento WHERE exigencia_id = '"
                        + f.exigencia + "'"));
        ok("Cap. 12 . e o aviso diz por que nada foi aprendido",
                r.avisoSobreAlias().contains("já aponta para"));
        ok("E-02 . o alias continua apontando para o dono original",
                ocupante.toString().equals(escalar(sgdf, "SELECT tipo_id::text FROM tipo_alias "
                        + "WHERE texto_normalizado = 'contracheque_golf'")));
    }

    /** RA-01 fechado: a fila tem contrato, e o recorte incide sobre ele. */
    static void filaERecortadaPorContrato(Sgdf sgdf) {
        Fixture meu = fixture(sgdf, "CONTRACHEQUE_HOTEL.pdf");
        Fixture alheio = fixture(sgdf, "CONTRACHEQUE_INDIA.pdf");
        RepositorioDeTriagem repo = new RepositorioDeTriagem(sgdf);

        List<RepositorioDeTriagem.ItemDaFila> so = repo.fila(java.util.Set.of(meu.contrato), 50);
        ok("RA-01 . a fila do meu contrato traz o meu item", so.size() == 1
                && so.get(0).candidaturaId().equals(meu.candidatura));
        ok("RA-01 . e nao traz o do contrato alheio",
                so.stream().noneMatch(i -> i.candidaturaId().equals(alheio.candidatura)));
        ok("Cap. 15.1 . visao global enxerga os dois",
                repo.fila(null, 200).stream()
                        .filter(i -> i.candidaturaId().equals(meu.candidatura)
                                || i.candidaturaId().equals(alheio.candidatura))
                        .count() == 2);
        ok("F1-06 . o item da fila traz o contrato, o sigilo e o motivo",
                so.get(0).contratoId().equals(meu.contrato)
                        && "PESSOAL".equals(so.get(0).sigilo())
                        && so.get(0).motivo().contains("margem"));
    }

    /**
     * Ator com recorte e sem contrato nenhum e erro de configuracao. Devolver a
     * fila inteira transformaria esse erro em privilegio.
     */
    static void atorSemContratoNaoVeTudo(Sgdf sgdf) {
        fixture(sgdf, "CONTRACHEQUE_JULIET.pdf");
        ok("Cap. 15.1 . recorte vazio nao vira visao global",
                new RepositorioDeTriagem(sgdf).fila(java.util.Set.of(), 50).isEmpty());
    }

    /** Primeira escrita humana do sistema — cap. 16. */
    static void aDecisaoFicaNaTrilha(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CONTRACHEQUE_KILO.pdf");
        new RepositorioDeTriagem(sgdf).decidir(new PedidoDeTriagem.Confirmar(f.candidatura),
                "fulano.tal", "PUBLICADOR_AP");

        ok("Cap. 16 . a decisao humana fica na trilha, com quem e sobre o quê",
                1 == contar(sgdf, "log_auditoria WHERE ator = 'fulano.tal' "
                        + "AND acao = 'TRIAGEM_CONFIRMAR' AND objeto_id = '"
                        + f.candidatura + "'"));
        ok("Cap. 16 . e o detalhe permite reconstruir o que foi decidido",
                escalar(sgdf, "SELECT detalhe::text FROM log_auditoria WHERE objeto_id = '"
                        + f.candidatura + "'").contains(f.documento.toString()));
    }

    // --- fixture -------------------------------------------------------------

    record Fixture(UUID contrato, UUID ciclo, UUID tipo, String tipoCodigo, UUID exigencia,
                   UUID documento, UUID candidatura) {
    }

    static Fixture fixture(Sgdf sgdf, String nomeArquivo) {
        int n = ++sequencia;
        UUID tipo = umTipo(sgdf, "TST.TRI_" + n);
        String codigo = "TST.TRI_" + n;
        UUID ciclo = uuid(sgdf, "WITH c AS ("
                + " INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por)"
                + " VALUES ('Cliente Triagem " + n + "', '" + cnpj(n) + "', 'PRIVADA', false, '"
                + MARCA + "') RETURNING id),"
                + " m AS (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING' LIMIT 1),"
                + " v AS (INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + "  VALUES ('0.0-triagem-" + n + "', '" + MARCA + "', 'fixture') RETURNING id),"
                + " cs AS (INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + "   modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + "   calendario_uf, ativo, criado_por)"
                + "  SELECT c.id, 'CT-TRIAGEM-" + n + "', 'Teste', m.id, DATE '2026-01-01',"
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
                + " VALUES ('" + ciclo + "', '" + tipo + "', 'MENSAL', 'EM_TRIAGEM',"
                + " DATE '2026-07-05', 'BLOQUEANTE', 'AP', 'MATRIZ', '" + MARCA + "')"
                + " RETURNING id");
        UUID documento = uuid(sgdf, "INSERT INTO documento (origem, caminho, nome_arquivo,"
                + " hash_sha256, tamanho, mime_real, formato, status_triagem, criado_por)"
                + " VALUES ('OWNCLOUD', '/teste/" + n + "', " + literal(nomeArquivo) + ", '"
                + String.format("%064d", n) + "', 1024, 'application/pdf', 'pdf', 'PENDENTE', '"
                + MARCA + "') RETURNING id");
        UUID candidatura = new RepositorioDeTriagem(sgdf).abrirCandidatura(exigencia, documento,
                tipo, 0.72, "margem de 0,04 sobre o segundo colocado", null);
        return new Fixture(contrato, ciclo, tipo, codigo, exigencia, documento, candidatura);
    }

    static UUID umTipo(Sgdf sgdf, String codigo) {
        return uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento,"
                + " defasagem, criticidade, sigilo, criado_por)"
                + " VALUES (" + literal(codigo) + ", 'Tipo de teste', 'Teste', 'CONTRATO',"
                + " 'MENSAL', 'M', 'BLOQUEANTE', 'PESSOAL', '" + MARCA + "') RETURNING id");
    }

    static String cnpj(int n) {
        return String.format("9%013d", n);
    }

    static String literal(String valor) {
        return "'" + valor.replace("'", "''") + "'";
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

    /**
     * log_auditoria e append-only por RULE (V002): o DELETE e um no-op, nao um
     * erro. As linhas da trilha ficam, de proposito — apaga-las no teste seria
     * exercitar um caminho que a producao nao tem.
     */
    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM candidatura WHERE exigencia_id IN (SELECT id FROM exigencia "
                    + "WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM vinculo_exigencia_documento WHERE exigencia_id IN "
                    + "(SELECT id FROM exigencia WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM exigencia WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM documento WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            // Por tipo, e nao por criado_por: o alias gravado pela triagem leva o
            // ator da decisao ("fulano"), nao a marca do teste, e um alias orfao
            // impediria de remover o tipo_documental que o teste criou.
            "DELETE FROM tipo_alias WHERE tipo_id IN (SELECT id FROM tipo_documental "
                    + "WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM tipo_documental WHERE criado_por = '" + MARCA + "'",
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

    static boolean recusa(Runnable chamada) {
        try {
            chamada.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
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

    private TestesDeTriagem() {}
}
