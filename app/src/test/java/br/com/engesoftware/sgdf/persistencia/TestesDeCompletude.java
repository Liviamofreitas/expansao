package br.com.engesoftware.sgdf.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Completude por equipe no escopo profissional — historia F2-03, cap. 12.
 *
 * <p>Criterio de aceite: <i>"«faltam 3 contracheques de 42» calculado e
 * exibido"</i>. O numero e facil; o dificil e que ele seja verdade — quatro
 * situacoes diferentes se escondem atras de "falta", e junta-las manda a pessoa
 * errada atras da coisa errada.
 */
public final class TestesDeCompletude {

    static final String MARCA = "teste-completude";

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
                executar("faltamTresDeQuarentaEDois", () -> faltamTresDeQuarentaEDois(sgdf));
                executar("dispensadaNaoFalta", () -> dispensadaNaoFalta(sgdf));
                executar("emTriagemNaoEFaltaDaArea", () -> emTriagemNaoEFaltaDaArea(sgdf));
                executar("oGrupoCondicionalEntraNaConta",
                        () -> oGrupoCondicionalEntraNaConta(sgdf));
                executar("oGrupoEPorProfissional", () -> oGrupoEPorProfissional(sgdf));
                executar("oGrupoQueValeEODaExigencia", () -> oGrupoQueValeEODaExigencia(sgdf));
                executar("aCorporativaApareceUmaVezSo",
                        () -> aCorporativaApareceUmaVezSo(sgdf));
                executar("asMatriculasFaltantesSaoAsQueFaltam",
                        () -> asMatriculasFaltantesSaoAsQueFaltam(sgdf));
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

    /** O criterio de aceite, com os numeros dele. */
    static void faltamTresDeQuarentaEDois(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID tipo = umTipo(sgdf, "TST.CONTRACHEQUE" + f.seq, "PROFISSIONAL", null);
        for (int i = 1; i <= 42; i++) {
            umaExigencia(sgdf, f, tipo, profissional(sgdf, f, i),
                    i <= 39 ? "VALIDADO" : "PENDENTE");
        }

        ConsultaDoPainel.Completude c = daLinha(sgdf, f.ciclo, "TST.CONTRACHEQUE" + f.seq);
        ok("F2-03 . o denominador e o numero de exigencias materializadas",
                c.esperadas() == 42);
        ok("F2-03 . e o texto do criterio de aceite sai pronto",
                "faltam 3 de 42".equals(c.resumo()));
        ok("F2-03 . 39 entregues", c.entregues() == 39);
        ok("F2-03 . 3 ausentes", c.ausentes() == 3);
        ok("F2-03 . e o tipo nao esta completo", !c.completo());
    }

    /**
     * Contar a dispensada como falta faz alguem correr atras de um documento
     * formalmente dispensado — contra a decisao do APROVADOR_DAF.
     */
    static void dispensadaNaoFalta(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID tipo = umTipo(sgdf, "TST.DISP" + f.seq, "PROFISSIONAL", null);
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 1), "VALIDADO");
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 2), "DISPENSADO");
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 3), "PENDENTE");

        ConsultaDoPainel.Completude c = daLinha(sgdf, f.ciclo, "TST.DISP" + f.seq);
        ok("F0-09 . a dispensada tem balde proprio", c.dispensadas() == 1);
        ok("F0-09 . e nao entra nas que faltam", c.ausentes() == 1);
        ok("F2-03 . o resumo conta so o que falta de verdade",
                "faltam 1 de 3".equals(c.resumo()));
    }

    /**
     * O documento chegou e travou noutra mesa. Cobrar a area por isso e cobrar
     * quem ja entregou.
     */
    static void emTriagemNaoEFaltaDaArea(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID tipo = umTipo(sgdf, "TST.TRI" + f.seq, "PROFISSIONAL", null);
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 1), "EM_TRIAGEM");
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 2), "DIVERGENTE");
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 3), "REJEITADO");

        ConsultaDoPainel.Completude c = daLinha(sgdf, f.ciclo, "TST.TRI" + f.seq);
        ok("F2-03 . triagem e divergencia sao 'com problema', nao falta",
                c.comProblema() == 2);
        ok("F2-03 . mas o REJEITADO falta — a entrega foi recusada e precisa voltar",
                c.ausentes() == 1);
        ok("F2-03 . e o tipo nao e completo, porque ha coisa travada",
                !c.completo() && c.entregues() == 0);
    }

    /**
     * Cap. 7.5: o termo de nao adesao satisfaz o VT DAQUELE profissional. Sem
     * isto o painel diria "faltam 2 relacoes de VT" com os dois termos de nao
     * adesao entregues ao lado.
     */
    static void oGrupoCondicionalEntraNaConta(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID vt = umTipo(sgdf, "TST.VT" + f.seq, "PROFISSIONAL", "VT_ADESAO");
        UUID termo = umTipo(sgdf, "TST.TERMO" + f.seq, "PROFISSIONAL", "VT_ADESAO");
        UUID pessoa = profissional(sgdf, f, 1);
        umaExigencia(sgdf, f, vt, pessoa, "PENDENTE");
        umaExigencia(sgdf, f, termo, pessoa, "VALIDADO");

        ok("Cap. 7.5 . o termo entregue satisfaz o VT do mesmo profissional",
                daLinha(sgdf, f.ciclo, "TST.VT" + f.seq).ausentes() == 0);
        ok("Cap. 7.5 . e o VT conta como entregue, nao como dispensado",
                daLinha(sgdf, f.ciclo, "TST.VT" + f.seq).entregues() == 1);
        ok("Cap. 7.5 . o proprio termo tambem esta entregue",
                daLinha(sgdf, f.ciclo, "TST.TERMO" + f.seq).entregues() == 1);
    }

    /** Um termo de fulano nao satisfaz o VT de sicrano. */
    static void oGrupoEPorProfissional(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID vt = umTipo(sgdf, "TST.VT2_" + f.seq, "PROFISSIONAL", "VT_ADESAO2");
        UUID termo = umTipo(sgdf, "TST.TERMO2_" + f.seq, "PROFISSIONAL", "VT_ADESAO2");
        UUID fulano = profissional(sgdf, f, 1);
        UUID sicrano = profissional(sgdf, f, 2);
        umaExigencia(sgdf, f, termo, fulano, "VALIDADO");
        umaExigencia(sgdf, f, vt, fulano, "PENDENTE");
        umaExigencia(sgdf, f, vt, sicrano, "PENDENTE");

        ConsultaDoPainel.Completude c = daLinha(sgdf, f.ciclo, "TST.VT2_" + f.seq);
        ok("Cap. 7.5 . o VT de quem tem termo esta satisfeito e o do outro nao",
                c.esperadas() == 2 && c.entregues() == 1 && c.ausentes() == 1);
    }

    /**
     * A corporativa e uma so por empresa e competencia (V004), mas a view a
     * devolve para cada ciclo. Na completude de UM ciclo ela conta uma vez.
     */
    static void aCorporativaApareceUmaVezSo(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID tipo = umTipo(sgdf, "TST.CORP" + f.seq, "CORPORATIVO", null);
        executar(sgdf, "INSERT INTO exigencia (empresa_id, competencia, tipo_id, evento,"
                + " status, prazo_calculado, criticidade, responsavel, origem, criado_por)"
                + " VALUES ('" + f.empresa + "', '2026-04', '" + tipo + "', 'MENSAL',"
                + " 'PENDENTE', DATE '2026-05-05', 'BLOQUEANTE', 'FIN', 'MATRIZ', '"
                + MARCA + "')");

        ConsultaDoPainel.Completude c = daLinha(sgdf, f.ciclo, "TST.CORP" + f.seq);
        ok("Cap. 7.1 . a corporativa conta uma vez no ciclo",
                c.esperadas() == 1 && c.ausentes() == 1);
        ok("Cap. 7.1 . e vem marcada como CORPORATIVO",
                "CORPORATIVO".equals(c.escopo()));
    }

    static void asMatriculasFaltantesSaoAsQueFaltam(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID tipo = umTipo(sgdf, "TST.MAT" + f.seq, "PROFISSIONAL", null);
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 1), "VALIDADO");
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 2), "PENDENTE");
        umaExigencia(sgdf, f, tipo, profissional(sgdf, f, 3), "REJEITADO");

        List<String> faltantes = new ConsultaDoPainel(sgdf)
                .matriculasFaltantes(f.ciclo, "TST.MAT" + f.seq, 200);
        ok("F2-03 . a lista traz quem nao entregou e quem foi recusado",
                faltantes.size() == 2);
        ok("F2-03 . e nao traz quem entregou",
                faltantes.stream().noneMatch(m -> m.endsWith("-1")));
    }

    // -------------------------------------------------------------------------

    static ConsultaDoPainel.Completude daLinha(Sgdf sgdf, UUID ciclo, String tipo) {
        return new ConsultaDoPainel(sgdf).completudePorTipo(ciclo).stream()
                .filter(c -> c.tipo().equals(tipo)).findFirst().orElseThrow(
                        () -> new IllegalStateException("tipo " + tipo + " nao veio"));
    }

    record Fixture(int seq, UUID ciclo, UUID contrato, UUID empresa) {}

    static Fixture fixture(Sgdf sgdf) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Comp " + n + "', '" + String.format("5%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-comp-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Comp " + n + "', '" + String.format("6%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-COMP-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/comp', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        UUID ciclo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2026-04',"
                + " 'ABERTO', '" + versao + "', '" + MARCA + "') RETURNING id");
        return new Fixture(n, ciclo, contrato, empresa);
    }

    static UUID umTipo(Sgdf sgdf, String codigo, String escopo, String grupo) {
        return uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento,"
                + " defasagem, criticidade, sigilo, condicional_grupo, criado_por) VALUES ('"
                + codigo + "', 'Tipo', 'Teste', '" + escopo + "', 'MENSAL', 'M',"
                + " 'BLOQUEANTE', 'PESSOAL', "
                + (grupo == null ? "NULL" : "'" + grupo + "'") + ", '" + MARCA + "')"
                + " RETURNING id");
    }

    static UUID profissional(Sgdf sgdf, Fixture f, int i) {
        return uuid(sgdf, "INSERT INTO profissional (matricula, nome, cpf_cifrado, cpf_hash,"
                + " admissao, criado_por) VALUES ('C" + f.seq + "-" + i + "', 'Trabalhador',"
                + " '\\x00', '\\x" + String.format("%04x%04x", f.seq, i)
                + "', DATE '2025-01-01', '" + MARCA + "') RETURNING id");
    }

    /**
     * A exigencia COPIA o condicional_grupo do tipo na materializacao, como faz
     * RepositorioDaMatriz — e a consulta le a copia, nao o valor atual do tipo.
     * E a mesma logica do versao_matriz_id congelado: o ciclo responde pela regra
     * da epoca, e mudar o cadastro hoje nao reescreve o que ja foi materializado.
     * A primeira versao deste fixture nao copiava, e os tres testes de grupo
     * falharam por isso.
     */
    static void umaExigencia(Sgdf sgdf, Fixture f, UUID tipo, UUID profissional, String status) {
        executar(sgdf, "INSERT INTO exigencia (ciclo_id, tipo_id, evento, profissional_id,"
                + " status, prazo_calculado, criticidade, condicional_grupo, responsavel,"
                + " origem, criado_por)"
                + " SELECT '" + f.ciclo + "', t.id, 'MENSAL', '" + profissional + "', '"
                + status + "', DATE '2026-05-05', 'BLOQUEANTE', t.condicional_grupo, 'AP',"
                + " 'MATRIZ', '" + MARCA + "' FROM tipo_documental t WHERE t.id = '"
                + tipo + "'");
    }

    /** O grupo que vale e o da exigencia, nao o do tipo hoje. */
    static void oGrupoQueValeEODaExigencia(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        UUID vt = umTipo(sgdf, "TST.VT3_" + f.seq, "PROFISSIONAL", "VT_ADESAO3");
        UUID termo = umTipo(sgdf, "TST.TERMO3_" + f.seq, "PROFISSIONAL", "VT_ADESAO3");
        UUID pessoa = profissional(sgdf, f, 1);
        umaExigencia(sgdf, f, termo, pessoa, "VALIDADO");
        umaExigencia(sgdf, f, vt, pessoa, "PENDENTE");

        // Alguem tira o VT do grupo NO CADASTRO, depois de o ciclo materializar.
        executar(sgdf, "UPDATE tipo_documental SET condicional_grupo = NULL WHERE id = '"
                + vt + "'");

        ok("F0-05 . mudar o cadastro nao reescreve o que o ciclo ja materializou",
                daLinha(sgdf, f.ciclo, "TST.VT3_" + f.seq).ausentes() == 0);
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

    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM exigencia WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM profissional WHERE criado_por = '" + MARCA + "'",
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

    private TestesDeCompletude() {}
}
