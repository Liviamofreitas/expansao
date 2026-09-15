package br.com.engesoftware.sgdf.persistencia;

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
 * Cadastro de cliente, contrato-servico, tipo e alias — historias F0-02 e F0-03.
 *
 * <p>Criterio de aceite da F0-02: <i>"CAIXA cadastrada como 3 contratos-servico
 * distintos; unicidade (cliente, numero, servico)"</i>. E o caso que explica por
 * que a chave tem TRES colunas: um contrato guarda-chuva com tres servicos tem
 * tres ciclos por competencia, tres pastas de origem e tres medicoes.
 */
public final class TestesDeCadastro {

    static final String MARCA = "teste-cadastro";
    static final String PAPEL = "CURADOR_MATRIZ";

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
                executar("aCaixaSaoTresContratosServico",
                        () -> aCaixaSaoTresContratosServico(sgdf));
                executar("clienteRepetidoERecusado", () -> clienteRepetidoERecusado(sgdf));
                executar("tipoECodigoValidado", () -> tipoECodigoValidado(sgdf));
                executar("oAliasUsaAMesmaNormalizacaoDaTriagem",
                        () -> oAliasUsaAMesmaNormalizacaoDaTriagem(sgdf));
                executar("desativarNaoApaga", () -> desativarNaoApaga(sgdf));
                executar("todoCadastroFicaNaTrilha", () -> todoCadastroFicaNaTrilha(sgdf));
                executar("aPastaDeOrigemMudaComTrilha",
                        () -> aPastaDeOrigemMudaComTrilha(sgdf));
                executar("asRecusasDaTrocaDePasta", () -> asRecusasDaTrocaDePasta(sgdf));
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

    /** O criterio de aceite da F0-02, literal. */
    static void aCaixaSaoTresContratosServico(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        UUID caixa = repo.cadastrarCliente("Caixa Economica Federal " + n,
                String.format("2%013d", n), "PUBLICA", MARCA, PAPEL);
        UUID empresa = umaEmpresa(sgdf, n);

        for (String servico : List.of("SUSTENTACAO", "DESENVOLVIMENTO", "INFRAESTRUTURA")) {
            repo.cadastrarContrato(contrato(caixa, empresa, "CT-CAIXA-" + n, servico), MARCA,
                    PAPEL);
        }
        ok("F0-02 . a CAIXA e cadastrada como tres contratos-servico distintos",
                repo.contratosDoCliente(caixa).size() == 3);
        ok("F0-02 . com o MESMO numero de contrato e servicos diferentes",
                repo.contratosDoCliente(caixa).stream()
                        .allMatch(c -> c.numero().equals("CT-CAIXA-" + n))
                        && repo.contratosDoCliente(caixa).stream()
                                .map(RepositorioDeCadastro.ContratoCadastrado::servico)
                                .distinct().count() == 3);

        boolean recusou = false;
        try {
            repo.cadastrarContrato(contrato(caixa, empresa, "CT-CAIXA-" + n, "SUSTENTACAO"),
                    MARCA, PAPEL);
        } catch (RepositorioDeCadastro.JaCadastrado e) {
            recusou = true;
        }
        ok("F0-02 . repetir (cliente, numero, servico) e recusado", recusou);
    }

    static void clienteRepetidoERecusado(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        String cnpj = String.format("2%013d", n);
        repo.cadastrarCliente("Cliente " + n, cnpj, "PRIVADA", MARCA, PAPEL);

        boolean recusou = false;
        try {
            repo.cadastrarCliente("Mesmo cliente, outro nome", cnpj, "PRIVADA", MARCA, PAPEL);
        } catch (RepositorioDeCadastro.JaCadastrado e) {
            recusou = true;
        }
        ok("F0-02 . o CNPJ e a identidade do cliente — dois cadastros sao um erro", recusou);
    }

    static void tipoECodigoValidado(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        UUID id = repo.cadastrarTipo(tipo("TST.CAD" + n), MARCA, PAPEL);
        ok("F0-03 . o tipo e cadastrado", id != null);

        boolean recusou = false;
        try {
            repo.cadastrarTipo(tipo("codigo minusculo"), MARCA, PAPEL);
        } catch (RuntimeException e) {
            recusou = true;
        }
        ok("Cap. 5.1 . codigo fora do formato FAM.NOME e recusado pelo esquema", recusou);

        boolean repetido = false;
        try {
            repo.cadastrarTipo(tipo("TST.CAD" + n), MARCA, PAPEL);
        } catch (RepositorioDeCadastro.JaCadastrado e) {
            repetido = true;
        }
        ok("F0-03 . codigo repetido e recusado", repetido);
    }

    /**
     * Se o cadastro normalizasse de um jeito e a triagem de outro, o alias
     * digitado a mao e o aprendido seriam textos diferentes para o mesmo padrao
     * — a unicidade global do E-02 deixaria de valer.
     */
    static void oAliasUsaAMesmaNormalizacaoDaTriagem(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        UUID tipo = repo.cadastrarTipo(tipo("TST.ALI" + n), MARCA, PAPEL);
        repo.cadastrarAlias(tipo, "RELAÇÃO_DE_VA_VR_MENSAL_" + n + ".pdf", MARCA, PAPEL);

        ok("F0-03 . o alias e gravado normalizado, sem acento e sem numero",
                ("relacao_de_va_vr_mensal").equals(escalar(sgdf,
                        "SELECT texto_normalizado FROM tipo_alias WHERE tipo_id = '"
                        + tipo + "'")));
        ok("F0-03 . e com origem LEGADO — nao veio da triagem",
                "LEGADO".equals(escalar(sgdf, "SELECT origem FROM tipo_alias "
                        + "WHERE tipo_id = '" + tipo + "'")));

        UUID outro = repo.cadastrarTipo(tipo("TST.ALJ" + n), MARCA, PAPEL);
        boolean recusou = false;
        try {
            // Nome diferente, MESMO padrao: sem acento, sem numero, e o mesmo texto.
            repo.cadastrarAlias(outro, "relacao de va vr mensal.pdf", MARCA, PAPEL);
        } catch (RepositorioDeCadastro.JaCadastrado e) {
            recusou = true;
        }
        ok("E-02 . o mesmo padrao para dois tipos e recusado", recusou);

        boolean curto = false;
        try {
            repo.cadastrarAlias(outro, "NF_" + n + ".pdf", MARCA, PAPEL);
        } catch (RepositorioDeCadastro.CadastroInvalido e) {
            curto = true;
        }
        ok("F0-03 . alias curto demais e recusado antes de chegar ao banco", curto);
    }

    /**
     * tipo_alias, regra_exigibilidade e exigencias materializadas referenciam o
     * tipo. Apagar quebraria a leitura de ciclos antigos.
     */
    static void desativarNaoApaga(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        UUID tipo = repo.cadastrarTipo(tipo("TST.DES" + n), MARCA, PAPEL);
        repo.cadastrarAlias(tipo, "documento_desativado_" + n, MARCA, PAPEL);

        boolean semMotivo = false;
        try {
            repo.desativarTipo(tipo, "x", MARCA, PAPEL);
        } catch (RepositorioDeCadastro.CadastroInvalido e) {
            semMotivo = true;
        }
        ok("Cap. 16 . desativar tipo exige motivo", semMotivo);

        repo.desativarTipo(tipo, "substituido pelo TST.NOVO na revisao de 2026", MARCA, PAPEL);
        ok("F0-03 . o tipo fica inativo",
                "false".equals(escalar(sgdf, "SELECT ativo::text FROM tipo_documental "
                        + "WHERE id = '" + tipo + "'")));
        ok("F0-03 . mas continua existindo, com os aliases que o apontam",
                1 == contar(sgdf, "tipo_alias WHERE tipo_id = '" + tipo + "'"));
    }

    static void todoCadastroFicaNaTrilha(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        UUID cliente = repo.cadastrarCliente("Trilha " + n, String.format("2%013d", n),
                "PRIVADA", "quem-cadastrou", PAPEL);
        ok("Cap. 16 . o cadastro de cliente fica na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'CADASTRAR_CLIENTE' "
                        + "AND objeto_id = '" + cliente + "' AND ator = 'quem-cadastrou'"));
    }

    // --- ADR-005: a pasta muda de lugar dentro da nuvem -------------------------

    /**
     * O VALOR ANTERIOR NA TRILHA E O MOTIVO OBRIGATORIO SAO O TESTE INTEIRO.
     *
     * <p>Sem o anterior, reconstruir para onde apontavam os documentos ja
     * registrados e impossivel. Sem o motivo, a trilha responde "quem" e
     * "quando" e deixa sem resposta a unica pergunta que alguem vai fazer:
     * <i>por que a pasta mudou?</i>
     */
    static void aPastaDeOrigemMudaComTrilha(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        UUID cliente = repo.cadastrarCliente("Mudanca " + n, String.format("2%013d", n),
                "PRIVADA", MARCA, PAPEL);
        UUID contrato = repo.cadastrarContrato(
                contrato(cliente, umaEmpresa(sgdf, n), "CT-MUD-" + n, "PRINCIPAL"),
                MARCA, PAPEL);

        var r = repo.alterarPastaOrigem(contrato, "/Departamento de Pessoal/FATURAMENTO",
                "a nuvem foi reorganizada em 2026-09 e a pasta saiu de /caixa",
                "quem-mudou", "ADMIN_SISTEMA");

        ok("ADR-005 . a pasta nova fica no contrato",
                "/Departamento de Pessoal/FATURAMENTO".equals(escalar(sgdf,
                        "SELECT pasta_origem FROM contrato_servico WHERE id = '"
                        + contrato + "'")));
        ok("ADR-005 . e a resposta diz de onde veio", "/caixa/PRINCIPAL".equals(r.anterior()));

        String detalhe = escalar(sgdf, "SELECT detalhe::text FROM log_auditoria"
                + " WHERE acao = 'ALTERAR_PASTA_ORIGEM' AND objeto_id = '" + contrato + "'");
        ok("Cap. 16 . a trilha registra a pasta ANTERIOR",
                detalhe != null && detalhe.contains("/caixa/PRINCIPAL"));
        ok("Cap. 16 . e a nova",
                detalhe != null && detalhe.contains("/Departamento de Pessoal/FATURAMENTO"));
        ok("Cap. 16 . e o motivo, que e o que reconstroi a decisao",
                detalhe != null && detalhe.contains("a nuvem foi reorganizada"));
        ok("Cap. 16 . com o ator que agiu, e nao a marca do teste",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'ALTERAR_PASTA_ORIGEM'"
                        + " AND objeto_id = '" + contrato + "' AND ator = 'quem-mudou'"));

        // A CANONICALIZACAO E A MESMA DA VARREDURA, E NAO UMA PARECIDA.
        //
        // A contencao de raiz do cap. 14.1 compara o caminho do arquivo com
        // ESTA pasta. Se o cadastro guardasse '/a/b/../c' e a varredura
        // canonicalizasse o href para '/a/c', nenhum arquivo estaria dentro da
        // raiz e a pasta inteira apareceria como fora dela.
        repo.alterarPastaOrigem(contrato, "/a/b/../c/", "normalizacao conferida no cadastro",
                MARCA, PAPEL);
        ok("R-03 . a pasta e canonicalizada e perde a barra final",
                "/a/c".equals(escalar(sgdf, "SELECT pasta_origem FROM contrato_servico"
                        + " WHERE id = '" + contrato + "'")));
    }

    static void asRecusasDaTrocaDePasta(Sgdf sgdf) {
        RepositorioDeCadastro repo = new RepositorioDeCadastro(sgdf);
        int n = ++sequencia;
        UUID cliente = repo.cadastrarCliente("Recusa " + n, String.format("2%013d", n),
                "PRIVADA", MARCA, PAPEL);
        UUID contrato = repo.cadastrarContrato(
                contrato(cliente, umaEmpresa(sgdf, n), "CT-REC-" + n, "PRINCIPAL"),
                MARCA, PAPEL);

        ok("Cap. 16 . motivo curto e recusado",
                recusa(() -> repo.alterarPastaOrigem(contrato, "/nova", "ajuste",
                        MARCA, PAPEL)));
        ok("ADR-005 . pasta vazia e recusada",
                recusa(() -> repo.alterarPastaOrigem(contrato, "  ",
                        "motivo suficientemente longo", MARCA, PAPEL)));
        ok("Cap. 15.1 . a raiz '/' e recusada ? varreria a nuvem inteira",
                recusa(() -> repo.alterarPastaOrigem(contrato, "/",
                        "motivo suficientemente longo", MARCA, PAPEL)));
        ok("ADR-005 . contrato inexistente e recusado",
                recusa(() -> repo.alterarPastaOrigem(UUID.randomUUID(), "/nova",
                        "motivo suficientemente longo", MARCA, PAPEL)));

        // UM UPDATE QUE NAO MUDA NADA NAO PODE VIRAR LINHA DE TRILHA.
        // "alterado de X para X" e ruido dentro do registro que existe para
        // responder o que mudou.
        ok("Cap. 16 . a mesma pasta e recusada",
                recusa(() -> repo.alterarPastaOrigem(contrato, "/caixa/PRINCIPAL",
                        "motivo suficientemente longo", MARCA, PAPEL)));
        ok("Cap. 16 . e nenhuma recusa deixou linha na trilha",
                0 == contar(sgdf, "log_auditoria WHERE acao = 'ALTERAR_PASTA_ORIGEM'"
                        + " AND objeto_id = '" + contrato + "'"));
    }

    static boolean recusa(Runnable trabalho) {
        try {
            trabalho.run();
            return false;
        } catch (RepositorioDeCadastro.CadastroInvalido e) {
            return true;
        }
    }

    // -------------------------------------------------------------------------

    static RepositorioDeCadastro.Contrato contrato(UUID cliente, UUID empresa, String numero,
                                                   String servico) {
        return new RepositorioDeCadastro.Contrato(cliente, numero, servico, "OUTSOURCING",
                LocalDate.of(2025, 1, 1), null, "/caixa/" + servico,
                "{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\",\"offset\":3}", "DF", empresa,
                true);
    }

    static RepositorioDeCadastro.Tipo tipo(String codigo) {
        return new RepositorioDeCadastro.Tipo(codigo, "Tipo de teste", "Teste", "CONTRATO",
                "MENSAL", "M", "NAO_BLOQUEANTE", "INTERNO", null, "fundamento de teste");
    }

    static UUID umaEmpresa(Sgdf sgdf, int n) {
        return uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES "
                + "('Prestador Cadastro " + n + "', '" + String.format("1%013d", n) + "', '"
                + MARCA + "') RETURNING id");
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
            "DELETE FROM tipo_alias WHERE criado_por IN ('" + MARCA + "', 'quem-cadastrou')",
            "DELETE FROM contrato_servico WHERE criado_por IN ('" + MARCA
                    + "', 'quem-cadastrou')",
            "DELETE FROM cliente WHERE criado_por IN ('" + MARCA + "', 'quem-cadastrou')",
            "DELETE FROM tipo_documental WHERE criado_por IN ('" + MARCA
                    + "', 'quem-cadastrou')",
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

    private TestesDeCadastro() {}
}
