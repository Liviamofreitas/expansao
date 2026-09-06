package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.triagem.PadraoDeNome;
import br.com.engesoftware.sgdf.triagem.PedidoDeTriagem;
import br.com.engesoftware.sgdf.triagem.ResultadoDaTriagem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A fila de triagem e as três decisões — história F1-06.
 *
 * <p>Critério de aceite: <i>"confirmar tira da fila, cria alias e o mesmo padrão
 * não retorna"</i>. As três partes acontecem numa transação só, e é por isso
 * que estão na mesma classe: confirmar sem gravar o vínculo tira o documento da
 * fila sem contar como entrega; gravar o vínculo sem mover a exigência deixa o
 * ciclo bloqueado por uma exigência que já foi satisfeita; e gravar o alias fora
 * da transação ensina o sistema a partir de uma decisão que pode ter sido
 * desfeita.
 */
public final class RepositorioDeTriagem {

    private final Sgdf sgdf;

    public RepositorioDeTriagem(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * A fila, recortada pelos contratos que o ator enxerga.
     *
     * <p>É aqui que o RA-01 fecha. O recorte é um {@code IN (...)} sobre
     * {@code ciclo.contrato_servico_id}, e não um filtro aplicado depois em
     * memória: filtrar depois significa que a consulta leu as linhas dos outros
     * contratos, e uma leitura que aconteceu não deixa de ter acontecido porque
     * o resultado foi descartado.
     *
     * @param contratos visão global quando {@code null}; nunca quando vazio
     */
    public List<ItemDaFila> fila(Set<UUID> contratos, int limite) {
        boolean global = contratos == null;
        if (!global && contratos.isEmpty()) {
            // Ator com recorte e sem contrato algum não vê nada. Devolver a fila
            // inteira aqui seria transformar "não tem contrato atribuído" em
            // "vê todos", que é o erro de configuração virando privilégio.
            return List.of();
        }
        String sql = """
                SELECT candidatura_id, documento_id, exigencia_id, contrato_servico_id,
                       competencia, nome_arquivo, caminho, tipo_proposto, sigilo,
                       score, motivo
                FROM   fila_de_triagem
                WHERE  (?::boolean OR contrato_servico_id = ANY (?))
                ORDER  BY score DESC, aberta_em
                LIMIT  ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setBoolean(1, global);
            ps.setArray(2, sgdf.conexao().createArrayOf("uuid",
                    global ? new UUID[0] : contratos.toArray(new UUID[0])));
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<ItemDaFila> fila = new ArrayList<>();
                while (rs.next()) {
                    fila.add(new ItemDaFila(rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                            rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                            rs.getString(7), rs.getString(8), rs.getString(9),
                            rs.getBigDecimal(10).doubleValue(), rs.getString(11)));
                }
                return fila;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a fila de triagem", e);
        }
    }

    /** De que contrato é a candidatura — o alvo da autorização, vindo do dado. */
    public UUID contratoDaCandidatura(UUID candidaturaId) {
        String sql = """
                SELECT ci.contrato_servico_id
                FROM   candidatura c
                JOIN   exigencia e ON e.id = c.exigencia_id
                JOIN   ciclo ci    ON ci.id = e.ciclo_id
                WHERE  c.id = ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, candidaturaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o contrato da candidatura", e);
        }
    }

    /** Abre a candidatura que o motor produziu — cap. 8.3, faixa de triagem. */
    public UUID abrirCandidatura(UUID exigenciaId, UUID documentoId, UUID tipoPropostoId,
                                 double score, String motivo, UUID regraReconId) {
        return sgdf.emTransacao(conexao -> {
            String sql = """
                    INSERT INTO candidatura (exigencia_id, documento_id, tipo_proposto_id,
                                             score, motivo, regra_recon_id, situacao)
                    VALUES (?, ?, ?, ?, ?, ?, 'ABERTA')
                    ON CONFLICT (exigencia_id, documento_id) DO NOTHING
                    RETURNING id
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, exigenciaId);
                ps.setObject(2, documentoId);
                ps.setObject(3, tipoPropostoId);
                ps.setDouble(4, score);
                ps.setString(5, motivo);
                ps.setObject(6, regraReconId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID id = rs.getObject(1, UUID.class);
                        moverExigencia(conexao, exigenciaId, "EM_TRIAGEM", "PENDENTE", "sistema");
                        return id;
                    }
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao abrir a candidatura", e);
            }
            return idDaCandidatura(conexao, exigenciaId, documentoId);
        });
    }

    /**
     * Aplica a decisão humana.
     *
     * <p>Idempotente por recusa, não por omissão: decidir duas vezes a mesma
     * candidatura levanta {@link CandidaturaJaDecidida}. Aceitar em silêncio
     * gravaria o segundo vínculo e o segundo registro de trilha para uma decisão
     * que só foi tomada uma vez — e é assim que uma entrega passa a ser contada
     * duas vezes.
     */
    public ResultadoDaTriagem decidir(PedidoDeTriagem pedido, String ator, String papel) {
        return sgdf.emTransacao(conexao -> {
            Candidatura c = carregar(conexao, pedido.candidaturaId());
            return switch (pedido) {
                case PedidoDeTriagem.Confirmar p -> confirmar(conexao, c, ator, papel);
                case PedidoDeTriagem.Reclassificar p -> reclassificar(conexao, c, p, ator, papel);
                case PedidoDeTriagem.Ilegivel p -> ilegivel(conexao, c, p, ator, papel);
            };
        });
    }

    // --- as três decisões ----------------------------------------------------

    private ResultadoDaTriagem confirmar(Connection conexao, Candidatura c,
                                         String ator, String papel) {
        executar(conexao, """
                UPDATE documento SET tipo_id = ?, status_triagem = 'CONFIRMADO' WHERE id = ?
                """, c.tipoPropostoId(), c.documentoId());

        // O vínculo é o que faz a entrega contar. ON CONFLICT porque a chave já
        // é (exigencia, documento, formato): reconfirmar não duplica.
        executar(conexao, """
                INSERT INTO vinculo_exigencia_documento
                       (exigencia_id, documento_id, formato, decidido_por, decidido_ator)
                SELECT ?, id, formato, 'USUARIO', ? FROM documento WHERE id = ?
                ON CONFLICT DO NOTHING
                """, c.exigenciaId(), ator, c.documentoId());

        String status = moverExigencia(conexao, c.exigenciaId(), "RECEBIDO", "EM_TRIAGEM", ator);

        // Cap. 7.5, história F2-05: o documento que chega satisfaz a irmã do
        // grupo condicional. Aqui dentro, na mesma transação do vínculo — se a
        // vinculação for desfeita, a satisfação da irmã vai junto.
        List<UUID> irmas = GrupoCondicional.satisfazerIrmas(conexao, c.exigenciaId(), ator);

        Alias alias = aprenderAlias(conexao, c.tipoPropostoId(), c.nomeArquivo(), ator);
        fecharCandidatura(conexao, c.id(), "CONFIRMADA", null, ator);

        TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                ator, papel, "TRIAGEM_CONFIRMAR", "candidatura", c.id().toString(),
                Map.of("documento", List.of(c.documentoId().toString()),
                        "exigencia", List.of(c.exigenciaId().toString()),
                        "condicional_satisfeita",
                        irmas.stream().map(UUID::toString).toList(),
                        "alias", List.of(alias.descricao()))));

        return new ResultadoDaTriagem(c.id(), "CONFIRMADA", status, alias.gravado(),
                alias.aviso());
    }

    private ResultadoDaTriagem reclassificar(Connection conexao, Candidatura c,
                                             PedidoDeTriagem.Reclassificar p,
                                             String ator, String papel) {
        if (p.tipoEscolhidoId().equals(c.tipoPropostoId())) {
            throw new DecisaoInvalida("reclassificar para o tipo que o motor já propôs é "
                    + "confirmar. A diferença entre concordar e corrigir é o que mede o "
                    + "acerto do motor (risco P01) — use confirmar");
        }
        executar(conexao, """
                UPDATE documento SET tipo_id = ?, status_triagem = 'RECLASSIFICADO' WHERE id = ?
                """, p.tipoEscolhidoId(), c.documentoId());

        // A exigência volta a PENDENTE: ela continua sem o documento que
        // esperava. Reclassificar diz o que o documento É, não que esta
        // exigência foi satisfeita.
        String status = moverExigencia(conexao, c.exigenciaId(), "PENDENTE", "EM_TRIAGEM", ator);
        Alias alias = aprenderAlias(conexao, p.tipoEscolhidoId(), c.nomeArquivo(), ator);
        fecharCandidatura(conexao, c.id(), "RECLASSIFICADA", p.motivo(), ator);

        TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                ator, papel, "TRIAGEM_RECLASSIFICAR", "candidatura", c.id().toString(),
                Map.of("documento", List.of(c.documentoId().toString()),
                        "tipo_proposto", List.of(c.tipoPropostoId().toString()),
                        "tipo_escolhido", List.of(p.tipoEscolhidoId().toString()),
                        "motivo", List.of(p.motivo()),
                        "alias", List.of(alias.descricao()))));

        return new ResultadoDaTriagem(c.id(), "RECLASSIFICADA", status, alias.gravado(),
                alias.aviso());
    }

    private ResultadoDaTriagem ilegivel(Connection conexao, Candidatura c,
                                        PedidoDeTriagem.Ilegivel p, String ator, String papel) {
        executar(conexao, "UPDATE documento SET status_triagem = 'ILEGIVEL' WHERE id = ?",
                c.documentoId());
        String status = moverExigencia(conexao, c.exigenciaId(), "PENDENTE", "EM_TRIAGEM", ator);
        fecharCandidatura(conexao, c.id(), "ILEGIVEL", p.motivo(), ator);

        TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                ator, papel, "TRIAGEM_ILEGIVEL", "candidatura", c.id().toString(),
                Map.of("documento", List.of(c.documentoId().toString()),
                        "motivo", List.of(p.motivo()))));

        // Nada é aprendido: um arquivo que ninguém conseguiu ler não ensina
        // padrão nenhum, e gravar o alias aqui seria transformar um palpite em
        // conhecimento permanente.
        return new ResultadoDaTriagem(c.id(), "ILEGIVEL", status, null,
                "ilegível não ensina alias — não há o que aprender de um documento "
                        + "que ninguém conseguiu ler");
    }

    // --- o alias -------------------------------------------------------------

    /**
     * Grava o padrão do nome do arquivo, quando dá — cap. 8.3.
     *
     * <p>Três desfechos, e os três precisam ser ditos a quem confirmou: gravou,
     * já conhecia, ou não gravou porque o padrão pertence a outro tipo. Este
     * último não é erro da pessoa nem motivo para desfazer a confirmação: a
     * decisão sobre <i>este documento</i> continua válida. É por isso que a
     * colisão vira aviso e não exceção.
     */
    private Alias aprenderAlias(Connection conexao, UUID tipoId, String nomeArquivo,
                                String ator) {
        PadraoDeNome padrao = PadraoDeNome.de(nomeArquivo);
        if (!padrao.aprendivel()) {
            return new Alias(null, padrao.recusa());
        }
        UUID dono = donoDoAlias(conexao, padrao.normalizado());
        if (tipoId.equals(dono)) {
            return new Alias(padrao.normalizado(),
                    "o padrão \"" + padrao.normalizado() + "\" já era conhecido para este tipo");
        }
        if (dono != null) {
            return new Alias(null, "o padrão \"" + padrao.normalizado() + "\" já aponta para "
                    + "outro tipo. Um alias que serve a dois destrói o determinismo da "
                    + "classificação (achado E-02), então nada foi gravado — a confirmação "
                    + "deste documento vale do mesmo jeito");
        }
        executar(conexao, """
                INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado,
                                        origem, criado_por)
                VALUES (?, ?, ?, 'TRIAGEM', ?)
                """, tipoId, padrao.original(), padrao.normalizado(), ator);
        return new Alias(padrao.normalizado(), null);
    }

    private UUID donoDoAlias(Connection conexao, String normalizado) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT tipo_id FROM tipo_alias WHERE texto_normalizado = ?")) {
            ps.setString(1, normalizado);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao consultar o alias", e);
        }
    }

    // --- apoio ---------------------------------------------------------------

    /**
     * Move a exigência, só a partir do estado esperado.
     *
     * <p>O {@code AND status = ?} não é zelo: outra candidatura da mesma
     * exigência pode ter sido confirmada antes, e sobrescrever RECEBIDO com
     * PENDENTE por causa de um "ilegível" tardio devolveria à fila uma exigência
     * já satisfeita. Devolve o estado REAL depois da tentativa, que é o que a
     * resposta precisa dizer.
     */
    private String moverExigencia(Connection conexao, UUID exigenciaId, String destino,
                                  String origem, String ator) {
        executar(conexao, """
                UPDATE exigencia SET status = ?, atualizado_em = now(), atualizado_por = ?
                WHERE id = ? AND status = ?
                """, destino, ator, exigenciaId, origem);
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT status FROM exigencia WHERE id = ?")) {
            ps.setObject(1, exigenciaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o estado da exigência", e);
        }
    }

    private void fecharCandidatura(Connection conexao, UUID id, String situacao,
                                   String motivo, String ator) {
        executar(conexao, """
                UPDATE candidatura
                SET    situacao = ?, decisao_motivo = ?, decidida_em = now(), decidida_por = ?
                WHERE  id = ? AND situacao = 'ABERTA'
                """, situacao, motivo, ator, id);
    }

    private Candidatura carregar(Connection conexao, UUID id) {
        String sql = """
                SELECT c.id, c.exigencia_id, c.documento_id, c.tipo_proposto_id,
                       c.situacao, d.nome_arquivo
                FROM   candidatura c
                JOIN   documento d ON d.id = c.documento_id
                WHERE  c.id = ?
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DecisaoInvalida("candidatura " + id + " não existe");
                }
                String situacao = rs.getString(5);
                if (!"ABERTA".equals(situacao)) {
                    throw new CandidaturaJaDecidida("a candidatura " + id + " já foi decidida ("
                            + situacao + "). Decidir de novo gravaria um segundo vínculo para "
                            + "uma entrega que aconteceu uma vez só");
                }
                return new Candidatura(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getString(6));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao carregar a candidatura", e);
        }
    }

    private UUID idDaCandidatura(Connection conexao, UUID exigenciaId, UUID documentoId) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT id FROM candidatura WHERE exigencia_id = ? AND documento_id = ?")) {
            ps.setObject(1, exigenciaId);
            ps.setObject(2, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao localizar a candidatura", e);
        }
    }

    private void executar(Connection conexao, String sql, Object... parametros) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao gravar a decisão de triagem", e);
        }
    }

    private record Candidatura(UUID id, UUID exigenciaId, UUID documentoId, UUID tipoPropostoId,
                               String nomeArquivo) {
    }

    private record Alias(String gravado, String aviso) {
        String descricao() {
            return gravado != null ? gravado : "nenhum: " + aviso;
        }
    }

    /**
     * Um item da fila, com contrato — é o contrato que permite o recorte.
     *
     * @param sigilo do tipo proposto; decide quem pode abrir o conteúdo
     * @param motivo por que está em triagem (cap. 12)
     */
    public record ItemDaFila(UUID candidaturaId, UUID documentoId, UUID exigenciaId,
                             UUID contratoId, String competencia, String nomeArquivo,
                             String caminho, String tipoProposto, String sigilo,
                             double score, String motivo) {
    }

    /** O pedido não faz sentido sobre esta candidatura. */
    public static final class DecisaoInvalida extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DecisaoInvalida(String motivo) {
            super(motivo);
        }
    }

    /** Alguém já decidiu. Não é erro do sistema; é corrida entre duas pessoas. */
    public static final class CandidaturaJaDecidida extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CandidaturaJaDecidida(String motivo) {
            super(motivo);
        }
    }
}
