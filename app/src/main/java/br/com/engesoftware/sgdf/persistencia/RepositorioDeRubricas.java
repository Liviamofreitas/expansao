package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.folha.DeParaDeRubricas;
import br.com.engesoftware.sgdf.folha.Movimentacao;
import br.com.engesoftware.sgdf.folha.PapelDaRubrica;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carrega o de-para de rubricas e a movimentação — cap. 7.2.
 *
 * <p>Existe para que {@code DerivadorDeEventos} continue puro: ele recebe o
 * de-para já carregado e não sabe que existe banco. É o que permite exercitar as
 * cinco condições do cap. 7.2 com dezenas de combinações de rubrica sem subir
 * infraestrutura.
 */
public final class RepositorioDeRubricas {

    private final Sgdf sgdf;

    public RepositorioDeRubricas(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * O de-para vigente de um sistema de folha.
     *
     * <p>Carrega os dois mapas — por código e por descrição — porque a fonte
     * decide qual serve: a FOPAG imprime código, o contracheque não (A05).
     */
    public DeParaDeRubricas vigente(String sistema, LocalDate data) {
        String sql = """
                SELECT codigo, descricao_normalizada, papel
                FROM   rubrica_de_para
                WHERE  sistema = ?
                  AND  vigencia_ini <= ?
                  AND  (vigencia_fim IS NULL OR vigencia_fim >= ?)
                """;
        Map<String, PapelDaRubrica> porCodigo = new LinkedHashMap<>();
        Map<String, PapelDaRubrica> porDescricao = new LinkedHashMap<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, sistema);
            ps.setObject(2, data);
            ps.setObject(3, data);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PapelDaRubrica papel = PapelDaRubrica.de(rs.getString(3));
                    if (rs.getString(1) != null) {
                        porCodigo.put(rs.getString(1), papel);
                    }
                    if (rs.getString(2) != null) {
                        porDescricao.put(rs.getString(2), papel);
                    }
                }
            }
            return new DeParaDeRubricas(porCodigo, porDescricao);
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o de-para de rubricas", e);
        }
    }

    /** Admissão e desligamento dos alocados no contrato, por matrícula. */
    public Map<String, Movimentacao> movimentacoes(UUID contratoId) {
        String sql = """
                SELECT DISTINCT p.matricula, p.admissao, p.desligamento
                FROM   alocacao a JOIN profissional p ON p.id = a.profissional_id
                WHERE  a.contrato_servico_id = ?
                """;
        Map<String, Movimentacao> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, contratoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString(1), new Movimentacao(rs.getString(1),
                            rs.getObject(2, LocalDate.class),
                            rs.getObject(3, LocalDate.class)));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a movimentação", e);
        }
    }
}
