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
public final class RepositorioDeCadastro {

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
