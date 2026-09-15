package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.triagem.PadraoDeNome;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cadastro de cliente, contrato-serviço, tipo documental e alias — F0-02 e F0-03.
 *
 * <p><b>Todo o cadastro passa por aqui e não por {@code psql}.</b> É o que o
 * cap. 16 pede: alteração de cadastro é escrita de negócio e gera evento de
 * trilha. Um ajuste feito direto no banco não deixa quem, quando nem por quê — e
 * até esta história era assim que destinatários, tolerâncias e tipos entravam.
 *
 * <p>As unicidades continuam no banco, não aqui. Estes métodos as traduzem em
 * recusas explicadas; a garantia é do esquema, porque um caminho de código novo
 * não pode reabrir o que uma restrição fecha.
 */
public class RepositorioDeCadastro {

    private final Sgdf sgdf;

    public RepositorioDeCadastro(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /** Um cliente. O CNPJ é a identidade — dois cadastros do mesmo são um erro. */
    public UUID cadastrarCliente(String nome, String cnpj, String esfera, String ator,
                                 String papel) {
        return sgdf.emTransacao(conexao -> {
            UUID id = inserir(conexao, """
                    INSERT INTO cliente (nome, cnpj, esfera, criado_por)
                    VALUES (?, ?, ?, ?) RETURNING id
                    """, "cliente com CNPJ " + cnpj + " já está cadastrado",
                    nome, cnpj, esfera, ator);
            trilha(conexao, ator, papel, "CADASTRAR_CLIENTE", "cliente", id,
                    Map.of("nome", List.of(nome)));
            return id;
        });
    }

    /**
     * Um contrato-serviço.
     *
     * <p>Critério de aceite da F0-02: <i>"CAIXA cadastrada como 3
     * contratos-serviço distintos; unicidade (cliente, número, serviço)"</i>. O
     * mesmo contrato com o mesmo cliente pode existir várias vezes — um por
     * SERVIÇO. É por isso que a chave tem três colunas e não duas: um contrato
     * guarda-chuva com três serviços tem três ciclos por competência, três
     * pastas de origem e três medições, e colapsá-los perderia duas delas.
     */
    public UUID cadastrarContrato(Contrato contrato, String ator, String papel) {
        return sgdf.emTransacao(conexao -> {
            UUID id = inserir(conexao, """
                    INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id,
                                                  vigencia_ini, vigencia_fim, pasta_origem,
                                                  data_contratual_faturamento, calendario_uf,
                                                  empresa_id, ativo, criado_por)
                    VALUES (?, ?, ?, (SELECT id FROM modalidade WHERE codigo = ?), ?, ?, ?,
                            ?::jsonb, ?, ?, ?, ?) RETURNING id
                    """,
                    "o contrato " + contrato.numero() + " serviço " + contrato.servico()
                            + " já está cadastrado para este cliente",
                    contrato.clienteId(), contrato.numero(), contrato.servico(),
                    contrato.modalidade(), contrato.vigenciaIni(), contrato.vigenciaFim(),
                    contrato.pastaOrigem(), contrato.prazoDeFaturamento(),
                    contrato.calendarioUf(), contrato.empresaId(), contrato.ativo(), ator);
            trilha(conexao, ator, papel, "CADASTRAR_CONTRATO", "contrato_servico", id,
                    Map.of("numero", List.of(contrato.numero()),
                            "servico", List.of(contrato.servico())));
            return id;
        });
    }

    /** Um tipo documental. O código FAM.NOME é validado pelo esquema. */
    public UUID cadastrarTipo(Tipo tipo, String ator, String papel) {
        return sgdf.emTransacao(conexao -> {
            UUID id = inserir(conexao, """
                    INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento,
                                                 defasagem, criticidade, sigilo,
                                                 condicional_grupo, fundamento, criado_por)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, "o tipo " + tipo.codigo() + " já está cadastrado",
                    tipo.codigo(), tipo.nome(), tipo.familia(), tipo.escopo(), tipo.evento(),
                    tipo.defasagem(), tipo.criticidade(), tipo.sigilo(),
                    tipo.condicionalGrupo(), tipo.fundamento(), ator);
            trilha(conexao, ator, papel, "CADASTRAR_TIPO", "tipo_documental", id,
                    Map.of("codigo", List.of(tipo.codigo()),
                            "criticidade", List.of(tipo.criticidade()),
                            "sigilo", List.of(tipo.sigilo())));
            return id;
        });
    }

    /**
     * Um alias de cadastro (origem LEGADO).
     *
     * <p>Normaliza pelo MESMO {@link PadraoDeNome} que a triagem usa. Se cada um
     * normalizasse do seu jeito, o alias cadastrado à mão e o aprendido em
     * triagem seriam textos diferentes para o mesmo padrão — a unicidade global
     * do achado E-02 deixaria de valer e o bônus de nome seria concedido duas
     * vezes.
     */
    public UUID cadastrarAlias(UUID tipoId, String texto, String ator, String papel) {
        PadraoDeNome padrao = PadraoDeNome.de(texto);
        if (!padrao.aprendivel()) {
            throw new CadastroInvalido("alias inutilizável: " + padrao.recusa());
        }
        return sgdf.emTransacao(conexao -> {
            UUID id = inserir(conexao, """
                    INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado,
                                            origem, criado_por)
                    VALUES (?, ?, ?, 'LEGADO', ?) RETURNING id
                    """,
                    "o padrão \"" + padrao.normalizado() + "\" já aponta para um tipo. "
                            + "Um alias que serve a dois destrói o determinismo da "
                            + "classificação (achado E-02)",
                    tipoId, texto, padrao.normalizado(), ator);
            trilha(conexao, ator, papel, "CADASTRAR_ALIAS", "tipo_alias", id,
                    Map.of("padrao", List.of(padrao.normalizado())));
            return id;
        });
    }

    /**
     * Desativa um tipo. Nunca apaga.
     *
     * <p>{@code tipo_alias} e {@code regra_exigibilidade} referenciam o tipo, e
     * exigências já materializadas também. Apagar quebraria a leitura de ciclos
     * antigos; desativar tira o tipo das próximas aberturas e preserva o que já
     * aconteceu — que é o que o cap. 16 exige de qualquer histórico.
     */
    public void desativarTipo(UUID tipoId, String motivo, String ator, String papel) {
        if (motivo == null || motivo.strip().length() < 10) {
            throw new CadastroInvalido("desativar tipo exige motivo: quem reabrir o cadastro "
                    + "seis meses depois precisa saber por que ele saiu");
        }
        sgdf.emTransacao(conexao -> {
            executar(conexao, """
                    UPDATE tipo_documental SET ativo = false, atualizado_em = now(),
                                               atualizado_por = ?
                    WHERE id = ? AND ativo
                    """, ator, tipoId);
            trilha(conexao, ator, papel, "DESATIVAR_TIPO", "tipo_documental", tipoId,
                    Map.of("motivo", List.of(motivo.strip())));
            return null;
        });
    }

    /** Os contratos-serviço de um cliente — a tela da F0-02. */
    public List<ContratoCadastrado> contratosDoCliente(UUID clienteId) {
        String sql = """
                SELECT cs.id, cs.numero, cs.servico, m.codigo, cs.ativo, cs.pasta_origem
                FROM   contrato_servico cs JOIN modalidade m ON m.id = cs.modalidade_id
                WHERE  cs.cliente_id = ?
                ORDER  BY cs.numero, cs.servico
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ContratoCadastrado> contratos = new java.util.ArrayList<>();
                while (rs.next()) {
                    contratos.add(new ContratoCadastrado(rs.getObject(1, UUID.class),
                            rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getBoolean(5), rs.getString(6)));
                }
                return contratos;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os contratos do cliente", e);
        }
    }

    // -------------------------------------------------------------------------

    private UUID inserir(Connection conexao, String sql, String seJaExiste, Object... valores) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                ps.setObject(i + 1, valores[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        } catch (SQLException e) {
            // 23505 = unique_violation. Traduz a restrição do esquema numa
            // recusa que diz o que fazer, em vez de vazar o nome do índice.
            if ("23505".equals(e.getSQLState())) {
                throw new JaCadastrado(seJaExiste);
            }
            throw new Sgdf.FalhaDePersistencia("falha ao gravar o cadastro", e);
        }
    }

    private void executar(Connection conexao, String sql, Object... valores) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                ps.setObject(i + 1, valores[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao atualizar o cadastro", e);
        }
    }

    /**
     * Muda a pasta de origem de um contrato, com registro de quem e por quê.
     *
     * <p><b>Por que existe.</b> A ADR-005 tornou {@code pasta_origem} relativo à
     * base WebDAV: trocar de nuvem virou uma variável de ambiente. O que aquela
     * decisão NÃO resolve é a pasta mudar de lugar <i>dentro</i> da nuvem — e
     * até aqui a única forma de acompanhar essa mudança era um {@code UPDATE}
     * direto no banco, sem ator, sem data e sem motivo. A ADR registrou isso
     * como bloqueio obrigatório antes de a varredura real ser ligada; este
     * método é o desbloqueio.
     *
     * <p><b>O valor ANTERIOR vai na trilha, e é o ponto todo.</b> Sem ele,
     * reconstruir para onde apontavam os documentos já registrados é impossível,
     * e a pergunta que a auditoria faz — <i>"de onde veio este documento, na
     * época?"</i> — fica sem resposta. É a mesma razão pela qual o motivo é
     * obrigatório: "quem" e "quando" sem "por quê" não reconstrói decisão
     * nenhuma.
     *
     * <p><b>Não há recusa por pasta repetida, e isso foi MEDIDO, não suposto.</b>
     * A carga real tem {@code /CAIXA - 09705.2025} em três contratos e
     * {@code /BNB - 482023} em dois: um contrato guarda-chuva com vários
     * serviços compartilha a pasta por construção (F0-02). Uma trava de
     * unicidade aqui recusaria o cadastro correto.
     *
     * @return quantos documentos já registrados apontam para a pasta ANTERIOR —
     *         ver {@link PastaAlterada#documentosNaPastaAnterior()}
     */
    public PastaAlterada alterarPastaOrigem(UUID contratoId, String pastaNova, String motivo,
                                            String ator, String papel) {
        String nova = canonica(pastaNova);
        if (motivo == null || motivo.strip().length() < 15) {
            throw new CadastroInvalido(
                    "a alteração de pasta exige motivo com ao menos 15 caracteres: quem "
                    + "auditar vai perguntar POR QUE a pasta mudou, e 'ajuste' não responde");
        }
        return sgdf.emTransacao(conexao -> {
            String anterior = pastaAtual(conexao, contratoId);
            if (anterior == null) {
                throw new CadastroInvalido("contrato " + contratoId + " não existe");
            }
            // UM UPDATE QUE NÃO MUDA NADA NÃO PODE VIRAR LINHA DE TRILHA.
            //
            // "alterado de X para X" é ruído dentro do registro que existe
            // justamente para responder o que mudou — e ninguém que audita
            // consegue distinguir esse ruído de uma alteração real desfeita.
            if (anterior.equals(nova)) {
                throw new CadastroInvalido(
                        "a pasta já é '" + nova + "': nada a alterar");
            }

            long documentos = documentosSob(conexao, contratoId, anterior);
            atualizar(conexao, contratoId, nova);
            trilha(conexao, ator, papel, "ALTERAR_PASTA_ORIGEM", "contrato_servico",
                    contratoId, Map.of(
                            "anterior", List.of(anterior),
                            "nova", List.of(nova),
                            "motivo", List.of(motivo.strip()),
                            "documentos_na_pasta_anterior", List.of(String.valueOf(documentos))));
            return new PastaAlterada(anterior, nova, documentos);
        });
    }

    /**
     * O desfecho da alteração.
     *
     * @param documentosNaPastaAnterior documentos já ingeridos cujo caminho está
     *        sob a pasta antiga. <b>Eles não são movidos nem reescritos</b>: o
     *        {@code caminho} registra de onde o documento veio, e reescrevê-lo
     *        seria falsificar o histórico que o cap. 16 existe para preservar.
     *        A consequência prática é uma só, e é branda: o delta do cap. 8.1
     *        indexa por caminho, então os arquivos sob a pasta nova entram como
     *        nunca vistos e são baixados de novo uma vez — a deduplicação por
     *        hash reconhece o conteúdo e não duplica documento.
     */
    public record PastaAlterada(String anterior, String nova, long documentosNaPastaAnterior) {}

    /** Canonicaliza e recusa o que não é caminho absoluto de pasta. */
    private static String canonica(String pasta) {
        if (pasta == null || pasta.isBlank()) {
            throw new CadastroInvalido("a pasta de origem não pode ser vazia: sem ela o "
                    + "contrato não tem onde ser varrido");
        }
        String limpa;
        try {
            limpa = br.com.engesoftware.sgdf.coleta.CaminhoRemoto.canonicalizar(pasta.strip());
        } catch (IllegalArgumentException e) {
            throw new CadastroInvalido("pasta de origem inválida: " + e.getMessage());
        }
        // A MESMA CANONICALIZAÇÃO QUE A VARREDURA USA, E NÃO UMA PARECIDA.
        //
        // A contenção de raiz do cap. 14.1 compara o caminho do arquivo com
        // ESTA pasta. Se o cadastro guardasse '/a/b/../c' e a varredura
        // canonicalizasse o href para '/a/c', nenhum arquivo estaria "dentro da
        // raiz" e a pasta inteira apareceria como fora dela.
        if (limpa.endsWith("/") && limpa.length() > 1) {
            limpa = limpa.substring(0, limpa.length() - 1);
        }
        if (limpa.equals("/")) {
            throw new CadastroInvalido("a raiz '/' não é pasta de contrato: varrer a nuvem "
                    + "inteira ignoraria o recorte por contrato do cap. 15.1");
        }
        return limpa;
    }

    private static String pastaAtual(Connection conexao, UUID contratoId) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT pasta_origem FROM contrato_servico WHERE id = ?")) {
            ps.setObject(1, contratoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a pasta do contrato", e);
        }
    }

    private static long documentosSob(Connection conexao, UUID contratoId, String pasta) {
        String sql = """
                SELECT count(*)
                FROM   documento d
                JOIN   vinculo_exigencia_documento v ON v.documento_id = d.id
                JOIN   exigencia e ON e.id = v.exigencia_id
                JOIN   ciclo c ON c.id = e.ciclo_id
                WHERE  c.contrato_servico_id = ? AND d.caminho LIKE ? || '/%'
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, contratoId);
            ps.setString(2, pasta);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao contar documentos da pasta", e);
        }
    }

    private static void atualizar(Connection conexao, UUID contratoId, String pasta) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "UPDATE contrato_servico SET pasta_origem = ? WHERE id = ?")) {
            ps.setString(1, pasta);
            ps.setObject(2, contratoId);
            if (ps.executeUpdate() != 1) {
                // Inalcançável: pastaAtual() já provou que a linha existe, na
                // mesma transação. Fica porque um UPDATE que não atinge linha
                // nenhuma e segue para a trilha registraria uma alteração que
                // não houve — e a trilha é append-only, então o registro falso
                // não sai mais de lá.
                throw new CadastroInvalido("contrato " + contratoId + " não existe");
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao alterar a pasta do contrato", e);
        }
    }

    private static void trilha(Connection conexao, String ator, String papel, String acao,
                               String objetoTipo, UUID objetoId,
                               Map<String, List<String>> detalhe) {
        TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                ator, papel, acao, objetoTipo, objetoId.toString(), detalhe));
    }

    /** @param prazoDeFaturamento o prazo estruturado do cap. 7.3, em JSON */
    public record Contrato(UUID clienteId, String numero, String servico, String modalidade,
                           LocalDate vigenciaIni, LocalDate vigenciaFim, String pastaOrigem,
                           String prazoDeFaturamento, String calendarioUf, UUID empresaId,
                           boolean ativo) {
    }

    public record Tipo(String codigo, String nome, String familia, String escopo, String evento,
                       String defasagem, String criticidade, String sigilo,
                       String condicionalGrupo, String fundamento) {
    }

    public record ContratoCadastrado(UUID id, String numero, String servico, String modalidade,
                                     boolean ativo, String pastaOrigem) {
    }

    /**
     * O código do tipo, ou {@code null} se ele não existe.
     *
     * <p>Quem cadastra regra manda o id na URL; o classificador trabalha com o
     * CÓDIGO. Traduzir aqui, contra o banco, evita que a regra seja gravada para
     * um tipo e verificada contra outro — que é o tipo de erro que só apareceria
     * quando um documento fosse classificado errado, semanas depois.
     */
    public String codigoDoTipo(java.util.UUID tipoId) {
        return sgdf.emTransacao(conexao -> {
            try (java.sql.PreparedStatement ps = conexao.prepareStatement(
                    "SELECT codigo FROM tipo_documental WHERE id = ?")) {
                ps.setObject(1, tipoId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("falha ao ler o tipo " + tipoId, e);
            }
        });
    }

    /** Uma unicidade do esquema, traduzida. */
    public static final class JaCadastrado extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JaCadastrado(String motivo) {
            super(motivo);
        }
    }

    /** O cadastro não faz sentido — recusado antes de chegar ao banco. */
    public static final class CadastroInvalido extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CadastroInvalido(String motivo) {
            super(motivo);
        }
    }
}
