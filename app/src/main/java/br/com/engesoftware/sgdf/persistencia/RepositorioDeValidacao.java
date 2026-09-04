package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import br.com.engesoftware.sgdf.validacao.Veredito;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grava o veredito das validações unitárias.
 *
 * <p>É o que torna uma reprovação <b>defensável</b>. O cap. 16 exige que toda
 * decisão automática seja reproduzível a partir de documento (hash) + versão da
 * regra; sem este registro, ninguém consegue dizer, seis meses depois, por que
 * um documento foi recusado — e essa é exatamente a pergunta que um cliente faz
 * quando questiona uma pendência.
 *
 * <p><b>Nunca substitui, sempre acrescenta.</b> Reprocessar com regra nova não
 * apaga por que a regra antiga recusou. A view {@code validacao_vigente} devolve
 * a execução mais recente de cada código.
 */
public final class RepositorioDeValidacao {

    private final Sgdf sgdf;

    public RepositorioDeValidacao(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * @param regraReconhecimentoId versão da regra que produziu a decisão; nulo
     *                              quando a validação não depende de regra
     */
    public UUID gravar(UUID documentoId, ResultadoDeValidacao resultado,
                       UUID regraReconhecimentoId) {
        return sgdf.emTransacao(conexao -> {
            String sql = """
                    INSERT INTO validacao_documento
                        (documento_id, codigo, resultado, motivo, detalhe, regra_recon_id)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?)
                    RETURNING id
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, documentoId);
                ps.setString(2, resultado.codigo());
                ps.setString(3, resultado.veredito().name());
                ps.setString(4, resultado.motivo());
                ps.setString(5, Json.de(resultado.detalhe()));
                ps.setObject(6, regraReconhecimentoId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao gravar o veredito "
                        + resultado.codigo() + " do documento " + documentoId, e);
            }
        });
    }

    public int gravarTodas(UUID documentoId, List<ResultadoDeValidacao> resultados,
                           UUID regraReconhecimentoId) {
        for (ResultadoDeValidacao r : resultados) {
            gravar(documentoId, r, regraReconhecimentoId);
        }
        return resultados.size();
    }

    /** O veredito vigente de cada validação, por código. */
    public Map<String, Veredito> vigentesDe(UUID documentoId) {
        String sql = "SELECT codigo, resultado FROM validacao_vigente "
                + "WHERE documento_id = ? ORDER BY codigo";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Veredito> vigentes = new LinkedHashMap<>();
                while (rs.next()) {
                    vigentes.put(rs.getString(1), Veredito.valueOf(rs.getString(2)));
                }
                return vigentes;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os vereditos vigentes", e);
        }
    }

    /**
     * Se o documento passou por todas as validações sem reprovação.
     *
     * <p>{@code NAO_APLICAVEL} não reprova — é o princípio 1 do cap. 1. Mas
     * também não é aprovação, e por isso está aqui: quem pergunta "posso
     * publicar?" precisa saber que uma validação não rodou.
     */
    public boolean semReprovacao(UUID documentoId) {
        String sql = "SELECT count(*) FROM validacao_vigente "
                + "WHERE documento_id = ? AND resultado = 'REPROVADO'";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao contar reprovações", e);
        }
    }

    /** Quantas execuções o documento tem de um código — o histórico do cap. 16. */
    public int execucoesDe(UUID documentoId, String codigo) {
        String sql = "SELECT count(*) FROM validacao_documento "
                + "WHERE documento_id = ? AND codigo = ?";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, documentoId);
            ps.setString(2, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao contar execuções", e);
        }
    }
}
