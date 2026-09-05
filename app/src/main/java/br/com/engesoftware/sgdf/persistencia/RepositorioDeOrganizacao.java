package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.coleta.PoliticaDeArquivos;
import br.com.engesoftware.sgdf.coleta.ResultadoVarredura;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O painel de organização — história F1-10, cap. 8.1.
 *
 * <p>Critério de aceite: <i>"cópia em conflito aparece sinalizada e nunca
 * vinculada"</i>. As duas metades têm mecanismos diferentes, e é deliberado:
 *
 * <ul>
 *   <li><b>Nunca vinculada</b> é estrutural — o achado não está em
 *       {@code documento}, e {@code vinculo_exigencia_documento} só referencia
 *       documento. Não há filtro de consulta para alguém esquecer.</li>
 *   <li><b>Aparece sinalizada</b> é esta classe: sem ela, "nunca vinculada"
 *       seria verdade por o arquivo não existir em lugar nenhum, e quem colocou
 *       a cópia na pasta nunca saberia por que ela não conta.</li>
 * </ul>
 */
public final class RepositorioDeOrganizacao {

    private final Sgdf sgdf;

    public RepositorioDeOrganizacao(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Registra o que a varredura encontrou e não ingeriu.
     *
     * <p>Idempotente por {@code achado_unico}: a varredura passa de 30 em 30
     * minutos, e sem isso o painel encheria de repetições do mesmo problema.
     */
    public Registro registrar(UUID contratoId, String competencia,
                              ResultadoVarredura resultado) {
        return sgdf.emTransacao(conexao -> {
            int conflitos = 0;
            int comConteudoProprio = 0;
            for (ResultadoVarredura.Conflito c : resultado.conflitos) {
                UUID original = documentoComHash(conexao, c.hashSha256());
                boolean inedito = original == null;
                if (inedito) {
                    comConteudoProprio++;
                }
                boolean novo = gravar(conexao, contratoId, competencia,
                        PoliticaDeArquivos.Motivo.COPIA_DE_CONFLITO.name(),
                        inedito ? "ATENCAO" : "INFORMATIVO", c.caminho(), c.hashSha256(),
                        original, c.tamanho(), c.versao(),
                        inedito
                                ? "cópia em conflito cujo conteúdo NÃO confere com nenhum "
                                        + "documento conhecido: pode ser a única versão que "
                                        + "sobrou da sincronização — conferir antes de apagar"
                                : "cópia redundante: o mesmo conteúdo já está no sistema");
                if (novo) {
                    conflitos++;
                }
            }

            int outros = 0;
            for (ResultadoVarredura.Ignorado i : resultado.ignorados) {
                // Sem hash: estes não são baixados, e o esquema recusa afirmar
                // duplicata sem ter comparado conteúdo.
                if (gravar(conexao, contratoId, competencia, i.motivo().name(), "INFORMATIVO",
                        i.caminho(), null, null, null, null, i.detalhe())) {
                    outros++;
                }
            }
            return new Registro(conflitos, comConteudoProprio, outros);
        });
    }

    /**
     * O painel da tela 5, recortado por contrato.
     *
     * <p>O recorte é possível aqui — ao contrário da fila de triagem antes da
     * F1-06 — porque o achado nasce de uma varredura, e a varredura é de um
     * contrato: o caminho de origem é {@code contrato_servico.pasta_origem}.
     */
    public List<Achado> abertos(java.util.Set<UUID> contratos, int limite) {
        boolean global = contratos == null;
        if (!global && contratos.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT a.id, a.nome_arquivo, a.caminho, a.motivo, a.severidade, a.detalhe,
                       a.competencia, a.detectado_em, d.nome_arquivo, cs.numero
                FROM   achado_de_organizacao a
                LEFT   JOIN documento d ON d.id = a.documento_original_id
                LEFT   JOIN contrato_servico cs ON cs.id = a.contrato_servico_id
                WHERE  a.resolvido_em IS NULL
                  AND  (?::boolean OR a.contrato_servico_id = ANY (?))
                ORDER  BY a.severidade, a.detectado_em DESC
                LIMIT  ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setBoolean(1, global);
            ps.setArray(2, sgdf.conexao().createArrayOf("uuid",
                    global ? new UUID[0] : contratos.toArray(new UUID[0])));
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<Achado> achados = new ArrayList<>();
                while (rs.next()) {
                    achados.add(new Achado(rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7), rs.getObject(8, java.time.OffsetDateTime.class),
                            rs.getString(9), rs.getString(10)));
                }
                return achados;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o painel de organização", e);
        }
    }

    /** Marca como resolvido — alguém arrumou a pasta. Não apaga o histórico. */
    public void resolver(UUID achadoId, String ator) {
        sgdf.emTransacao(conexao -> {
            try (PreparedStatement ps = conexao.prepareStatement("""
                    UPDATE achado_de_organizacao
                    SET    resolvido_em = now(), resolvido_por = ?
                    WHERE  id = ? AND resolvido_em IS NULL
                    """)) {
                ps.setString(1, ator);
                ps.setObject(2, achadoId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao resolver o achado", e);
            }
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, "ORGANIZACAO", "ACHADO_RESOLVER", "achado_de_organizacao",
                    achadoId.toString(), java.util.Map.of()));
            return null;
        });
    }

    // -------------------------------------------------------------------------

    /** @return true quando a linha é nova; false quando a varredura já a vira */
    private boolean gravar(Connection conexao, UUID contratoId, String competencia,
                           String motivo, String severidade, String caminho, String hash,
                           UUID original, Long tamanho, String versao, String detalhe) {
        String sql = """
                INSERT INTO achado_de_organizacao
                       (contrato_servico_id, competencia, origem, caminho, nome_arquivo,
                        motivo, severidade, hash_sha256, documento_original_id, tamanho,
                        versao_origem, detalhe)
                VALUES (?, ?, 'OWNCLOUD', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (origem, caminho, hash_sha256) DO NOTHING
                RETURNING id
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, contratoId);
            ps.setString(2, competencia);
            ps.setString(3, caminho);
            ps.setString(4, nomeDe(caminho));
            ps.setString(5, motivo);
            ps.setString(6, severidade);
            ps.setString(7, hash);
            ps.setObject(8, original);
            ps.setObject(9, tamanho);
            ps.setString(10, versao);
            ps.setString(11, detalhe);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao registrar o achado de organização", e);
        }
    }

    private UUID documentoComHash(Connection conexao, String hash) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT id FROM documento WHERE hash_sha256 = ? LIMIT 1")) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao procurar o documento original", e);
        }
    }

    static String nomeDe(String caminho) {
        int barra = caminho.lastIndexOf('/');
        return barra < 0 ? caminho : caminho.substring(barra + 1);
    }

    /**
     * @param conflitos          cópias de conflito novas nesta varredura
     * @param comConteudoProprio destas, quantas NÃO conferem com nada conhecido —
     *                           o número que alguém precisa olhar hoje
     */
    public record Registro(int conflitos, int comConteudoProprio, int outros) {}

    /** @param documentoOriginal nome do arquivo cujo conteúdo já está no sistema */
    public record Achado(UUID id, String nomeArquivo, String caminho, String motivo,
                         String severidade, String detalhe, String competencia,
                         java.time.OffsetDateTime detectadoEm, String documentoOriginal,
                         String contrato) {
    }
}
