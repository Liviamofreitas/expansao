package br.com.engesoftware.sgdf.persistencia;

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
 * Solicitar, aprovar e negar exceções — história F0-09.
 *
 * <p>Critério de aceite: <i>"solicitante não consegue aprovar a própria exceção;
 * aprovação exige APROVADOR_DAF"</i>.
 *
 * <p><b>A segregação de funções é verificada três vezes, e não é redundância
 * inútil.</b> O {@code Autorizador} barra na fronteira, com mensagem; este
 * repositório barra de novo, porque um caminho de código novo pode não passar
 * pelo controlador; e a restrição {@code excecao_sod} barra no banco, que é o
 * único lugar por onde nada passa sem passar. Cada camada protege de uma classe
 * diferente de erro.
 *
 * <p><b>O que ela não protege.</b> A comparação é entre strings de identidade. Se
 * a mesma pessoa autenticar com dois identificadores — o {@code sub} do OIDC hoje
 * e um e-mail amanhã — as três camadas concordam que são duas pessoas. A defesa
 * está no provedor de identidade, não aqui; registrado em PENDENCIAS.
 */
public final class RepositorioDeExcecao {

    /**
     * Os estados de onde uma exigência pode ser dispensada.
     *
     * <p>Cap. 6.1: {@code PENDENTE|DIVERGENTE → DISPENSADO}. REJEITADO entra
     * porque o cap. 6.1 o devolve automaticamente a PENDENTE — dispensar uma
     * exigência cuja última entrega foi rejeitada é o caso mais comum de todos.
     */
    static final Set<String> DISPENSAVEIS = Set.of("PENDENTE", "DIVERGENTE", "REJEITADO");

    // O UPDATE repete a lista em SQL literal em vez de receber um array: o
    // conjunto acima é a mesma verdade, e mantê-los lado a lado é mais legível
    // que montar um java.sql.Array para três constantes. Se um terceiro lugar
    // precisar da lista, ela vira parâmetro.

    private final Sgdf sgdf;

    public RepositorioDeExcecao(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /** Abre a solicitação. Não muda a exigência: pedir não é obter. */
    public UUID solicitar(UUID exigenciaId, String motivo, String evidencia, String solicitante,
                          String papel) {
        return sgdf.emTransacao(conexao -> {
            Exigencia e = exigencia(conexao, exigenciaId);
            if (!DISPENSAVEIS.contains(e.status())) {
                throw new ExcecaoInvalida("a exigência está em " + e.status()
                        + " e não há o que dispensar (cap. 6.1)");
            }
            String sql = """
                    INSERT INTO excecao (exigencia_id, motivo, solicitante, evidencia,
                                         situacao)
                    VALUES (?, ?, ?, ?, 'SOLICITADA') RETURNING id
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, exigenciaId);
                ps.setString(2, motivo);
                ps.setString(3, solicitante);
                ps.setString(4, evidencia);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    UUID id = rs.getObject(1, UUID.class);
                    TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                            solicitante, papel, "EXCECAO_SOLICITAR", "excecao", id.toString(),
                            Map.of("exigencia", List.of(exigenciaId.toString()),
                                    "motivo", List.of(motivo))));
                    return id;
                }
            } catch (SQLException ex) {
                if ("23505".equals(ex.getSQLState())) {
                    throw new ExcecaoInvalida("já existe exceção em aberto para esta "
                            + "exigência: duas solicitações produzem duas decisões possíveis "
                            + "para o mesmo fato");
                }
                if ("23514".equals(ex.getSQLState())) {
                    throw new ExcecaoInvalida("o motivo da exceção precisa ter ao menos 20 "
                            + "caracteres: quem aprova decide a partir dele");
                }
                throw new Sgdf.FalhaDePersistencia("falha ao solicitar a exceção", ex);
            }
        });
    }

    /**
     * Aprova: a exigência vai para DISPENSADO e a pendência fecha.
     *
     * @param aprovadorDaf o ator tem {@code APROVAR_EXCECAO} (cap. 15.1)
     */
    public Decidida aprovar(UUID excecaoId, String ator, String papel, boolean aprovadorDaf) {
        return decidir(excecaoId, "APROVADA", null, ator, papel, aprovadorDaf);
    }

    /** Nega, com motivo. A exigência continua exatamente onde estava. */
    public Decidida negar(UUID excecaoId, String motivo, String ator, String papel,
                          boolean aprovadorDaf) {
        if (motivo == null || motivo.strip().length() < 20) {
            throw new ExcecaoInvalida("negar exige motivo de ao menos 20 caracteres: sem ele "
                    + "quem solicitou pede de novo igual, ou desiste de uma exceção que "
                    + "talvez fosse legítima com outra evidência");
        }
        return decidir(excecaoId, "NEGADA", motivo.strip(), ator, papel, aprovadorDaf);
    }

    private Decidida decidir(UUID excecaoId, String situacao, String motivoDecisao, String ator,
                             String papel, boolean aprovadorDaf) {
        // AS RECUSAS FICAM FORA DA TRANSAÇÃO DE ESCRITA, E É POR ISSO.
        //
        // Registrar a negativa dentro do bloco que em seguida levanta a exceção
        // grava a trilha e a desfaz no mesmo instante: o rollback leva junto o
        // registro de que alguém tentou. A negativa é o fato mais auditável do
        // fluxo — é ela que mostra alguém insistindo — e some exatamente por ser
        // negativa. Descoberto por um teste que afirmava sobre a trilha; sem
        // essa asserção o caminho pareceria correto para sempre.
        Solicitacao s = solicitacao(sgdf.conexao(), excecaoId);
        if (!"SOLICITADA".equals(s.situacao())) {
            throw new ExcecaoJaDecidida("a exceção " + excecaoId + " já está " + s.situacao());
        }
        // SoD: a mesma checagem que o Autorizador faz na fronteira e que a
        // restrição excecao_sod faz no banco. Aqui é onde a mensagem existe.
        if (s.solicitante().equals(ator)) {
            registrarNegado(ator, papel, excecaoId, "quem solicitou não decide (cap. 15.1)");
            throw new SegregacaoDeFuncoes("quem solicitou a exceção não a decide "
                    + "(cap. 15.1). Outro APROVADOR_DAF precisa avaliar");
        }
        if (!aprovadorDaf) {
            registrarNegado(ator, papel, excecaoId, "papel sem APROVAR_EXCECAO");
            throw new SegregacaoDeFuncoes("decidir exceção exige APROVADOR_DAF (cap. 15.1)");
        }

        return sgdf.emTransacao(conexao -> {
            Exigencia e = exigencia(conexao, s.exigenciaId());
            String statusFinal = e.status();
            if ("APROVADA".equals(situacao)) {
                // A ENTREGA PODE TER CHEGADO ENQUANTO A EXCEÇÃO ESPERAVA.
                //
                // Aprovar sobre uma exigência que já foi atendida a empurraria de
                // RECEBIDO para DISPENSADO — desfazendo uma entrega real e
                // tirando do book um documento que existe. A decisão foi tomada
                // sobre um estado que não é mais o atual, e a resposta certa é
                // dizer isso, não aplicá-la.
                if (!DISPENSAVEIS.contains(e.status())) {
                    throw new ExcecaoInvalida("a exigência passou para " + e.status()
                            + " enquanto a exceção esperava decisão: a entrega chegou, e "
                            + "dispensá-la agora apagaria um documento que existe");
                }
                atualizar(conexao, """
                        UPDATE exigencia SET status = 'DISPENSADO', atualizado_em = now(),
                                             atualizado_por = ?
                        WHERE id = ? AND status IN ('PENDENTE', 'DIVERGENTE', 'REJEITADO')
                        """, ator, s.exigenciaId());
                statusFinal = "DISPENSADO";
                fecharPendencia(conexao, s.exigenciaId());
            }

            atualizar(conexao, """
                    UPDATE excecao SET situacao = ?, aprovador = ?, aprovado_em = now(),
                                       motivo_decisao = ?
                    WHERE id = ? AND situacao = 'SOLICITADA'
                    """, situacao, ator, motivoDecisao, excecaoId);

            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, "EXCECAO_" + situacao, "excecao", excecaoId.toString(),
                    Map.of("exigencia", List.of(s.exigenciaId().toString()),
                            "solicitante", List.of(s.solicitante()),
                            "status_exigencia", List.of(statusFinal),
                            "motivo", List.of(motivoDecisao == null ? "" : motivoDecisao))));

            return new Decidida(excecaoId, situacao, s.exigenciaId(), statusFinal);
        });
    }

    /** As exceções à espera de decisão — a fila do APROVADOR_DAF. */
    public List<EmAberto> emAberto(int limite) {
        String sql = """
                SELECT x.id, x.exigencia_id, t.codigo, x.solicitante, x.motivo, x.criado_em,
                       e.status, cs.numero, c.competencia
                FROM   excecao x
                JOIN   exigencia e ON e.id = x.exigencia_id
                JOIN   tipo_documental t ON t.id = e.tipo_id
                LEFT   JOIN ciclo c ON c.id = e.ciclo_id
                LEFT   JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
                WHERE  x.situacao = 'SOLICITADA'
                ORDER  BY x.criado_em
                LIMIT  ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<EmAberto> fila = new ArrayList<>();
                while (rs.next()) {
                    fila.add(new EmAberto(rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getObject(6, java.time.OffsetDateTime.class),
                            rs.getString(7), rs.getString(8), rs.getString(9)));
                }
                return fila;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as exceções em aberto", e);
        }
    }

    // -------------------------------------------------------------------------

    /** Cap. 5.2: a pendência fecha automaticamente quando a exigência é satisfeita. */
    private void fecharPendencia(Connection conexao, UUID exigenciaId) {
        atualizar(conexao, """
                UPDATE pendencia SET resolvida_em = now(), resolucao = 'EXCECAO'
                WHERE exigencia_id = ? AND resolvida_em IS NULL
                """, exigenciaId);
    }

    private Exigencia exigencia(Connection conexao, UUID id) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT status, criticidade, empresa_id FROM exigencia WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ExcecaoInvalida("exigência " + id + " não existe");
                }
                return new Exigencia(rs.getString(1), rs.getString(2),
                        rs.getObject(3, UUID.class));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a exigência", e);
        }
    }

    private Solicitacao solicitacao(Connection conexao, UUID id) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT exigencia_id, solicitante, situacao FROM excecao WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ExcecaoInvalida("exceção " + id + " não existe");
                }
                return new Solicitacao(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a exceção", e);
        }
    }

    /** Numa transação própria: a negativa precisa sobreviver ao rollback da recusa. */
    private void registrarNegado(String ator, String papel, UUID excecaoId, String motivo) {
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, new TrilhaDeAuditoria.Registro(
                    ator, papel, "EXCECAO_DECIDIR", "excecao", excecaoId.toString(), "NEGADO",
                    Map.of("motivo", List.of(motivo))));
            return null;
        });
    }

    private void atualizar(Connection conexao, String sql, Object... valores) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                ps.setObject(i + 1, valores[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23514".equals(e.getSQLState())) {
                throw new SegregacaoDeFuncoes("o banco recusou a decisão: "
                        + "quem solicitou não decide (restrição excecao_sod)");
            }
            throw new Sgdf.FalhaDePersistencia("falha ao gravar a decisão da exceção", e);
        }
    }

    private record Exigencia(String status, String criticidade, UUID empresaId) {}

    private record Solicitacao(UUID exigenciaId, String solicitante, String situacao) {}

    /** @param statusExigencia onde a exigência ficou — DISPENSADO só quando aprovada */
    public record Decidida(UUID excecaoId, String situacao, UUID exigenciaId,
                           String statusExigencia) {
    }

    public record EmAberto(UUID id, UUID exigenciaId, String tipo, String solicitante,
                           String motivo, java.time.OffsetDateTime solicitadaEm,
                           String statusExigencia, String contrato, String competencia) {
    }

    /** Cap. 15.1: quem solicitou não decide; decidir exige APROVADOR_DAF. */
    public static final class SegregacaoDeFuncoes extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SegregacaoDeFuncoes(String motivo) {
            super(motivo);
        }
    }

    /** O pedido não faz sentido sobre esta exigência. */
    public static final class ExcecaoInvalida extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ExcecaoInvalida(String motivo) {
            super(motivo);
        }
    }

    /** Alguém já decidiu. */
    public static final class ExcecaoJaDecidida extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ExcecaoJaDecidida(String motivo) {
            super(motivo);
        }
    }
}
