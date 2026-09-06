package br.com.engesoftware.sgdf.persistencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A condicional A-ou-B do cap. 7.5 — história F2-05.
 *
 * <p>Critério de aceite: <i>"termo de não adesão satisfaz a exigência de VT do
 * profissional"</i>.
 *
 * <p><b>Contar certo e cobrar errado é pior que os dois errados juntos.</b> A
 * F2-03 já fazia o painel parar de dizer "faltam 12 relações de VT" com os 12
 * termos entregues ao lado — mas fazia isso <b>na consulta</b>, e a exigência de
 * VT continuava PENDENTE no banco. Esse estado é o que a régua de cobrança lê,
 * o que bloqueia a publicação do book e o que a pendência mantém aberta: a tela
 * dizia que estava tudo bem e o e-mail saía assim mesmo.
 *
 * <p>Corrigir isso em cada consulta seria repetir a mesma regra em três lugares
 * que ninguém lembra de manter juntos. A satisfação do grupo passou a ser um
 * <b>fato gravado</b>: a irmã vai para DISPENSADO com
 * {@code dispensa_motivo = 'CONDICIONAL'} e a pendência dela fecha.
 *
 * <p><b>O grupo é por profissional.</b> Um termo de fulano não satisfaz o VT de
 * sicrano; para escopo de contrato, o grupo é do ciclo. A cláusula
 * {@code IS NOT DISTINCT FROM} faz os dois casos com a mesma consulta — e
 * comparar com {@code =} silenciosamente não casaria nada quando o profissional
 * é nulo, deixando a condicional de contrato sem efeito.
 */
public final class GrupoCondicional {

    private GrupoCondicional() {}

    /**
     * Satisfaz as irmãs da exigência recém-atendida.
     *
     * <p>Chamada de dentro da transação que vinculou o documento: se a
     * vinculação for desfeita, a satisfação da irmã vai junto. Fora dela, um
     * rollback deixaria a irmã dispensada por um documento que não existe.
     *
     * @return as irmãs que passaram a DISPENSADO
     */
    public static List<UUID> satisfazerIrmas(Connection conexao, UUID exigenciaId,
                                             String ator) {
        String sql = """
                UPDATE exigencia irma
                SET    status = 'DISPENSADO', dispensa_motivo = 'CONDICIONAL',
                       atualizado_em = now(), atualizado_por = ?
                FROM   exigencia atendida
                WHERE  atendida.id = ?
                  AND  atendida.condicional_grupo IS NOT NULL
                  AND  irma.id <> atendida.id
                  AND  irma.ciclo_id IS NOT DISTINCT FROM atendida.ciclo_id
                  AND  irma.condicional_grupo = atendida.condicional_grupo
                  AND  irma.profissional_id IS NOT DISTINCT FROM atendida.profissional_id
                  AND  irma.status IN ('PENDENTE', 'EM_TRIAGEM', 'REJEITADO')
                RETURNING irma.id
                """;
        List<UUID> satisfeitas = new ArrayList<>();
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, ator);
            ps.setObject(2, exigenciaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    satisfeitas.add(rs.getObject(1, UUID.class));
                }
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao satisfazer o grupo condicional", e);
        }

        for (UUID irma : satisfeitas) {
            // A pendência fecha como ENTREGA, não como CANCELAMENTO: alguma
            // coisa FOI entregue — a alternativa. Registrar cancelamento diria
            // que a obrigação deixou de existir, e ela foi cumprida.
            fecharPendencia(conexao, irma);
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, "SISTEMA", "CONDICIONAL_SATISFEITA", "exigencia", irma.toString(),
                    java.util.Map.of("satisfeita_por", List.of(exigenciaId.toString()))));
        }
        return satisfeitas;
    }

    private static void fecharPendencia(Connection conexao, UUID exigenciaId) {
        try (PreparedStatement ps = conexao.prepareStatement("""
                UPDATE pendencia SET resolvida_em = now(), resolucao = 'ENTREGA'
                WHERE exigencia_id = ? AND resolvida_em IS NULL
                """)) {
            ps.setObject(1, exigenciaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao fechar a pendência da irmã", e);
        }
    }
}
