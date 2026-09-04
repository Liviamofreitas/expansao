package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.conciliacao.ResultadoDaConciliacao;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Grava o resultado das regras de conciliação.
 *
 * <p>Cap. 9: <i>"Resultado sempre gravado com os valores comparados e o
 * delta"</i> — <b>inclusive quando conforme</b>. Um "está tudo certo" sem os
 * números não permite a ninguém conferir se estava mesmo, e é o registro do
 * conforme que sustenta a defesa quando o cliente questiona meses depois.
 */
public final class RepositorioDeConciliacao {

    private final Sgdf sgdf;

    public RepositorioDeConciliacao(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    public UUID gravar(UUID cicloId, UUID regraId, ResultadoDaConciliacao resultado) {
        return sgdf.emTransacao(conexao -> {
            String sql = """
                    INSERT INTO conciliacao (regra_id, ciclo_id, itens, resultado, delta, detalhe)
                    VALUES (?, ?, ?::jsonb, ?, ?, ?::jsonb)
                    RETURNING id
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, regraId);
                ps.setObject(2, cicloId);
                ps.setString(3, Json.de(resultado.itens()));
                ps.setString(4, resultado.resultado().name());
                ps.setBigDecimal(5, resultado.delta());
                ps.setString(6, detalheEmJson(resultado));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao gravar a conciliação "
                        + resultado.codigo() + " do ciclo " + cicloId, e);
            }
        });
    }

    /** As conciliações que impedem publicar: divergentes em modo BLOQUEIO. */
    public List<String> bloqueiosDoCiclo(UUID cicloId) {
        String sql = """
                SELECT r.codigo, c.detalhe->>'mensagem'
                FROM conciliacao c
                JOIN regra_conciliacao r ON r.id = c.regra_id
                WHERE c.ciclo_id = ? AND c.resultado = 'DIVERGENTE' AND r.modo = 'BLOQUEIO'
                ORDER BY r.codigo, c.executada_em DESC
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> bloqueios = new ArrayList<>();
                while (rs.next()) {
                    bloqueios.add(rs.getString(1) + ": " + rs.getString(2));
                }
                return bloqueios;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os bloqueios do ciclo", e);
        }
    }

    private static String detalheEmJson(ResultadoDaConciliacao r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"codigo\":").append(Json.texto(r.codigo()));
        sb.append(",\"modo\":").append(Json.texto(r.modo().name()));
        sb.append(",\"mensagem\":").append(Json.texto(r.mensagem()));
        if (r.esperado() != null) {
            sb.append(",\"esperado\":").append(r.esperado());
        }
        if (r.obtido() != null) {
            sb.append(",\"obtido\":").append(r.obtido());
        }
        return sb.append('}').toString();
    }
}
