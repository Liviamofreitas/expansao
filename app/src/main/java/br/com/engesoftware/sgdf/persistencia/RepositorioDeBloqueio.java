package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.limite.PoliticaDeUso;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conta negativas e bloqueia quem insiste — SEC-06.
 *
 * <p>A fonte da contagem é a própria {@code log_auditoria}: ela já registra todo
 * {@code NEGADO} desde a F1-06. Criar um contador paralelo daria duas verdades
 * sobre o mesmo fato, e a primeira vez que elas divergissem ninguém saberia
 * qual acreditar.
 */
public class RepositorioDeBloqueio {

    private final Sgdf sgdf;

    public RepositorioDeBloqueio(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * O bloqueio ativo deste ator, se houver.
     *
     * <p>Expirado não é ativo: a consulta compara com {@code now()} em vez de
     * exigir uma varredura que limpe os vencidos. Um bloqueio que depende de
     * faxina para acabar é um bloqueio que dura para sempre quando a faxina
     * falha — e a faxina é justamente um job, que é o componente cuja morte é
     * silenciosa (achados § 46).
     */
    public Optional<Bloqueio> ativo(String ator) {
        String sql = """
                SELECT ator, bloqueado_em, expira_em, negativas, motivo
                FROM   bloqueio_de_ator
                WHERE  ator = ? AND liberado_em IS NULL AND expira_em > now()
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, ator);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Bloqueio(rs.getString(1),
                        rs.getObject(2, OffsetDateTime.class),
                        rs.getObject(3, OffsetDateTime.class), rs.getInt(4), rs.getString(5)));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o bloqueio do ator", e);
        }
    }

    /**
     * Registra a negativa que acabou de acontecer e bloqueia se passou do limite.
     *
     * @return o bloqueio, quando esta negativa foi a que estourou
     */
    public Optional<Bloqueio> contarNegativaE(String ator, String papel, String recurso) {
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, new TrilhaDeAuditoria.Registro(
                    ator, papel, "ACESSO_NEGADO", "api", recurso, "NEGADO",
                    Map.of("recurso", List.of(recurso == null ? "" : recurso))));
            return null;
        });

        int negativas = negativasNaJanela(ator);
        if (negativas < PoliticaDeUso.NEGATIVAS_ANTES_DO_BLOQUEIO) {
            return Optional.empty();
        }
        return Optional.of(bloquear(ator, papel, negativas));
    }

    /** Quantas negativas este ator acumulou na janela da política. */
    public int negativasNaJanela(String ator) {
        String sql = """
                SELECT count(*) FROM log_auditoria
                WHERE  ator = ? AND resultado = 'NEGADO'
                  AND  ocorrido_em > now() - (? || ' minutes')::interval
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, ator);
            ps.setString(2, String.valueOf(PoliticaDeUso.JANELA_DE_NEGATIVAS.toMinutes()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao contar as negativas do ator", e);
        }
    }

    /**
     * Libera um bloqueio — ato humano e auditável.
     *
     * <p>Existe porque o bloqueio pode ser engano: um papel mal mapeado no
     * diretório produz negativas legítimas em série, e a pessoa fica de fora sem
     * ter feito nada de errado. Sem esta porta, a saída seria alguém mexer no
     * banco à mão.
     */
    public boolean liberar(String ator, String quem, String papel) {
        String sql = """
                UPDATE bloqueio_de_ator SET liberado_em = now(), liberado_por = ?
                WHERE  ator = ? AND liberado_em IS NULL
                """;
        return sgdf.emTransacao(conexao -> {
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setString(1, quem);
                ps.setString(2, ator);
                if (ps.executeUpdate() == 0) {
                    return false;
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao liberar o bloqueio", e);
            }
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    quem, papel, "BLOQUEIO_LIBERAR", "ator", ator,
                    Map.of("ator_liberado", List.of(ator))));
            return true;
        });
    }

    /** Os bloqueios ativos — a tela de quem opera. */
    public List<Bloqueio> ativos() {
        String sql = """
                SELECT ator, bloqueado_em, expira_em, negativas, motivo
                FROM   bloqueio_de_ator
                WHERE  liberado_em IS NULL AND expira_em > now()
                ORDER  BY bloqueado_em DESC
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Bloqueio> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new Bloqueio(rs.getString(1), rs.getObject(2, OffsetDateTime.class),
                        rs.getObject(3, OffsetDateTime.class), rs.getInt(4), rs.getString(5)));
            }
            return lista;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao listar os bloqueios", e);
        }
    }

    // -------------------------------------------------------------------------

    private Bloqueio bloquear(String ator, String papel, int negativas) {
        String motivo = negativas + " negativas de autorização em "
                + PoliticaDeUso.JANELA_DE_NEGATIVAS.toMinutes() + " minutos";
        String sql = """
                INSERT INTO bloqueio_de_ator (ator, expira_em, negativas, motivo)
                VALUES (?, now() + (? || ' minutes')::interval, ?, ?)
                ON CONFLICT (ator) WHERE liberado_em IS NULL DO NOTHING
                RETURNING ator, bloqueado_em, expira_em, negativas, motivo
                """;
        return sgdf.emTransacao(conexao -> {
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setString(1, ator);
                ps.setString(2, String.valueOf(PoliticaDeUso.DURACAO_DO_BLOQUEIO.toMinutes()));
                ps.setInt(3, negativas);
                ps.setString(4, motivo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // NÃO PROLONGA UM BLOQUEIO QUE JÁ EXISTE.
                        //
                        // O ON CONFLICT DO NOTHING é o que impede que cada
                        // tentativa durante o bloqueio empurre a expiração para
                        // frente. Sem ele, um cliente com retry automático se
                        // prenderia indefinidamente — o bloqueio pune o excesso,
                        // não a insistência.
                        Bloqueio b = new Bloqueio(rs.getString(1),
                                rs.getObject(2, OffsetDateTime.class),
                                rs.getObject(3, OffsetDateTime.class), rs.getInt(4),
                                rs.getString(5));
                        TrilhaDeAuditoria.registrar(conexao, new TrilhaDeAuditoria.Registro(
                                ator, papel, "ATOR_BLOQUEADO", "ator", ator, "NEGADO",
                                Map.of("motivo", List.of(motivo))));
                        return b;
                    }
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao bloquear o ator", e);
            }
            return ativo(ator).orElseThrow(() -> new Sgdf.FalhaDePersistencia(
                    "bloqueio do ator " + ator + " sumiu entre gravar e ler", null));
        });
    }

    /** @param expiraEm tem fim sempre — ver a restrição {@code bloqueio_expira_depois} */
    public record Bloqueio(String ator, OffsetDateTime bloqueadoEm, OffsetDateTime expiraEm,
                           int negativas, String motivo) {

        public long segundosRestantes(OffsetDateTime agora) {
            long s = java.time.Duration.between(agora, expiraEm).getSeconds();
            return Math.max(1, s);
        }
    }
}
