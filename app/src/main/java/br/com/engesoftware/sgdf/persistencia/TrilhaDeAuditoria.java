package br.com.engesoftware.sgdf.persistencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

/**
 * Escreve em {@code log_auditoria} — cap. 5.2 e 16.
 *
 * <p><b>Por que só agora.</b> Até a F1-06 o sistema não tinha ação humana de
 * escrita: varria, extraía, classificava e conciliava, tudo reproduzível a
 * partir do documento e da versão da regra. A triagem é a primeira decisão que
 * uma pessoa toma e que muda o estado — e uma decisão humana que muda estado sem
 * deixar quem, quando e por quê é exatamente o que o cap. 16 existe para
 * impedir.
 *
 * <p><b>Grava na mesma transação do efeito.</b> Registrar fora dela produziria
 * as duas metades erradas: trilha de uma decisão que foi desfeita, ou decisão
 * gravada sem trilha. A tabela é append-only por RULE (V002), então o rollback
 * é a única forma de a linha não existir — e é a forma certa.
 */
public final class TrilhaDeAuditoria {

    private TrilhaDeAuditoria() {}

    /**
     * @param resultado SUCESSO, NEGADO ou ERRO — negar também é fato auditável,
     *                  e é o que permite ver tentativa repetida de acesso
     */
    public static void registrar(Connection conexao, Registro registro) {
        String sql = """
                INSERT INTO log_auditoria (ator, papel, acao, objeto_tipo, objeto_id,
                                           resultado, detalhe)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, registro.ator());
            ps.setString(2, registro.papel());
            ps.setString(3, registro.acao());
            ps.setString(4, registro.objetoTipo());
            ps.setString(5, registro.objetoId());
            ps.setString(6, registro.resultado());
            if (registro.detalhe() == null || registro.detalhe().isEmpty()) {
                ps.setNull(7, Types.OTHER);
            } else {
                ps.setString(7, Json.de(registro.detalhe()));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao registrar na trilha de auditoria", e);
        }
    }

    /** Um fato da trilha. O detalhe é o que permite reconstruir a decisão depois. */
    public record Registro(String ator, String papel, String acao, String objetoTipo,
                           String objetoId, String resultado,
                           Map<String, List<String>> detalhe) {

        public Registro {
            if (ator == null || ator.isBlank() || papel == null || papel.isBlank()) {
                throw new IllegalArgumentException(
                        "trilha sem ator ou sem papel não responde 'quem' (cap. 16)");
            }
        }

        public static Registro sucesso(String ator, String papel, String acao,
                                       String objetoTipo, String objetoId,
                                       Map<String, List<String>> detalhe) {
            return new Registro(ator, papel, acao, objetoTipo, objetoId, "SUCESSO", detalhe);
        }
    }
}
