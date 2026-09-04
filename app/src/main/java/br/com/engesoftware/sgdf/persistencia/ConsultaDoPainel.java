package br.com.engesoftware.sgdf.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As consultas que as telas do Anexo 2 precisam.
 *
 * <p>Separadas dos repositórios de escrita de propósito: o que a tela pede é
 * agregado e recortado, e forçar isso pelos mesmos objetos que gravam produziria
 * ou consultas ineficientes ou objetos de domínio deformados para servir a uma
 * tela.
 */
public final class ConsultaDoPainel {

    private final Sgdf sgdf;

    public ConsultaDoPainel(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * De que contrato-serviço é o ciclo — nulo se o ciclo não existe.
     *
     * <p>Existe para que a autorização não dependa de o cliente <b>declarar</b>
     * o contrato. Um recorte que só é aplicado quando quem chama informa o alvo
     * não é um recorte: basta omitir o parâmetro para escapar dele. O alvo da
     * decisão tem de vir do dado, e o dado é a coluna {@code contrato_servico_id}
     * do próprio ciclo.
     *
     * <p>Ler esta coluna antes de autorizar não revela nada a quem não pode: o
     * identificador do contrato nunca chega à resposta, só ao {@code Autorizador}.
     */
    public UUID contratoDoCiclo(UUID cicloId) {
        String sql = "SELECT contrato_servico_id FROM ciclo WHERE id = ?";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o contrato do ciclo", e);
        }
    }

    /**
     * A barra segmentada da tela 1: quantas exigências em cada estado.
     *
     * <p>Critério de aceite da F1-07: <i>"a barra segmentada reflete os estados
     * reais"</i> — por isso a contagem vem do banco e não de um cálculo em
     * memória que poderia divergir do que está gravado.
     */
    public Map<String, Integer> estadosDoCiclo(UUID cicloId) {
        String sql = """
                SELECT status, count(*)
                FROM exigencia
                WHERE ciclo_id = ?
                GROUP BY status
                ORDER BY status
                """;
        Map<String, Integer> contagem = new LinkedHashMap<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contagem.put(rs.getString(1), rs.getInt(2));
                }
            }
            return contagem;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao contar os estados do ciclo", e);
        }
    }

    /**
     * Por que o ciclo não pode publicar.
     *
     * <p>Critério de aceite da F1-07: <i>"bloqueio de publicação exibe
     * motivo"</i>. São duas fontes — exigência bloqueante que não chegou ao fim,
     * e conciliação divergente em modo BLOQUEIO — e a tela precisa das duas,
     * porque quem resolve cada uma é uma pessoa diferente.
     */
    public List<String> motivosDeBloqueio(UUID cicloId) {
        List<String> motivos = new ArrayList<>();
        String porExigencia = """
                SELECT t.codigo, e.status, count(*)
                FROM exigencia e
                JOIN tipo_documental t ON t.id = e.tipo_id
                WHERE e.ciclo_id = ?
                  AND e.criticidade = 'BLOQUEANTE'
                  AND e.status NOT IN ('PUBLICADO', 'DISPENSADO', 'CONCILIADO')
                GROUP BY t.codigo, e.status
                ORDER BY t.codigo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(porExigencia)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    motivos.add(rs.getString(1) + ": " + rs.getInt(3)
                            + " exigência(s) bloqueante(s) em " + rs.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as exigências bloqueantes", e);
        }
        motivos.addAll(new RepositorioDeConciliacao(sgdf).bloqueiosDoCiclo(cicloId));
        return motivos;
    }

    /**
     * A fila de triagem (tela 3): candidatos entre os dois limiares.
     *
     * <p>Cap. 8.3: {@code limiar_triagem ≤ score < limiar_auto} vai para a fila.
     * O documento aparece com o tipo que o motor propôs e com a confiança, para
     * que quem confirma veja em que o sistema se apoiou.
     */
    public List<Candidato> filaDeTriagem(int limite) {
        String sql = """
                SELECT d.id, d.nome_arquivo, d.caminho, coalesce(t.codigo, '?'), d.confianca,
                       coalesce(t.sigilo, 'INTERNO')
                FROM documento d
                LEFT JOIN tipo_documental t ON t.id = d.tipo_id
                WHERE d.status_triagem = 'PENDENTE' AND d.confianca IS NOT NULL
                ORDER BY d.confianca DESC, d.criado_em
                LIMIT ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<Candidato> fila = new ArrayList<>();
                while (rs.next()) {
                    fila.add(new Candidato(rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getString(3), rs.getString(4),
                            rs.getBigDecimal(5) == null ? 0 : rs.getBigDecimal(5).doubleValue(),
                            rs.getString(6)));
                }
                return fila;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a fila de triagem", e);
        }
    }

    /**
     * O painel de arquivos desconhecidos (tela 5, história F1-10).
     *
     * <p>Cap. 8.3: abaixo do limiar de triagem o arquivo é desconhecido — não
     * conta como entrega e aparece no painel de organização. Nunca é vinculado.
     */
    public List<Candidato> desconhecidos(int limite) {
        String sql = """
                SELECT d.id, d.nome_arquivo, d.caminho, '-', coalesce(d.confianca, 0), '-'
                FROM documento d
                WHERE d.tipo_id IS NULL
                ORDER BY d.criado_em DESC
                LIMIT ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<Candidato> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new Candidato(rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getString(3), rs.getString(4),
                            rs.getBigDecimal(5).doubleValue(), rs.getString(6)));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os arquivos desconhecidos", e);
        }
    }

    /**
     * Um arquivo à espera de decisão humana.
     *
     * @param sigilo do tipo proposto — decide se quem olha pode ver o conteúdo
     */
    public record Candidato(UUID documentoId, String nomeArquivo, String caminho,
                            String tipoProposto, double confianca, String sigilo) {
    }
}
