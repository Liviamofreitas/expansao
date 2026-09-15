package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.coleta.EstadoConhecido;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * O que a varredura precisa saber do banco antes de sair varrendo.
 *
 * <p>Duas perguntas, e nenhuma delas o cliente HTTP pode responder:
 *
 * <ul>
 *   <li><b>Onde varrer.</b> A raiz é {@code contrato_servico.pasta_origem} do
 *       contrato DO CICLO, derivada do próprio ciclo. Receber a pasta por
 *       parâmetro seria deixar quem chama escolher onde o sistema vai ler — o
 *       mesmo defeito que o painel já registrou (achados § 30.1): um recorte
 *       que o cliente escolhe não é restrição.
 *   <li><b>O que já foi visto.</b> O delta do cap. 8.1 compara a versão da
 *       origem com a última registrada em {@code documento.versao_origem}. Sem
 *       isso, toda varredura baixaria e escanearia o repositório inteiro de
 *       novo — e o antivírus, que é o passo caro, pagaria a conta.
 * </ul>
 */
public class RepositorioDeColeta {

    /** De onde varrer, e para qual ciclo entra o que for coletado. */
    public record Alvo(UUID contratoId, String competencia, String pastaOrigem) {}

    private final Sgdf sgdf;

    public RepositorioDeColeta(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * O alvo da varredura, derivado do ciclo.
     *
     * @return {@code null} se o ciclo não existe — quem chama decide se isso é
     *         404 ou negação, e a diferença importa para quem não podia ver o
     *         ciclo de qualquer jeito
     */
    public Alvo alvoDe(UUID cicloId) {
        return sgdf.emTransacao(conexao -> {
            String sql = """
                    SELECT c.contrato_servico_id, c.competencia, cs.pasta_origem
                      FROM ciclo c
                      JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
                     WHERE c.id = ?
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, cicloId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new Alvo(rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getString(3));
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("falha ao ler o alvo do ciclo " + cicloId, e);
            }
        });
    }

    /**
     * O estado conhecido de uma origem, para o delta.
     *
     * <p>Devolve a versão do registro MAIS RECENTE do caminho. A tabela guarda
     * uma linha por versão ingerida — comparar com qualquer outra que não a
     * última faria a varredura rebaixar um documento novo a "inalterado".
     *
     * <p>Uma consulta por caminho, e não um mapa carregado de uma vez: a
     * varredura visita um caminho de cada vez e a maioria nunca chega aqui,
     * porque a política de arquivos já os descartou por extensão ou tamanho.
     */
    public EstadoConhecido estadoDe(String origem) {
        return caminho -> sgdf.emTransacao(conexao -> {
            String sql = """
                    SELECT versao_origem
                      FROM documento
                     WHERE origem = ? AND caminho = ?
                     ORDER BY criado_em DESC
                     LIMIT 1
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setString(1, origem);
                ps.setString(2, caminho);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("falha ao consultar a versão de " + caminho, e);
            }
        });
    }
}
