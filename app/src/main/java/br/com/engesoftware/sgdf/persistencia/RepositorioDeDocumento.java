package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.extracao.CampoExtraido;
import br.com.engesoftware.sgdf.extracao.Retangulo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Grava e lê documentos e os campos extraídos deles.
 *
 * <p><b>Por que o hash decide.</b> A tabela tem
 * {@code UNIQUE (origem, caminho, hash_sha256)}, e a varredura é recursiva a
 * partir da raiz do mês (cap. 8.1): o mesmo arquivo reaparece a cada passagem.
 * Gravar de novo criaria linhas duplicadas com hashes iguais, e a validação V7 —
 * que existe justamente para não contar duas vezes o mesmo documento — passaria
 * a ver dois registros onde há um arquivo.
 */
public final class RepositorioDeDocumento {

    private final Sgdf sgdf;

    public RepositorioDeDocumento(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Grava o documento, ou devolve o que já existe com o mesmo hash.
     *
     * <p>Idempotente por construção: reprocessar uma varredura não duplica nada,
     * e o chamador sabe pelo {@code inedito} se havia algo novo.
     */
    public DocumentoGravado gravar(Documento documento) {
        return sgdf.emTransacao(conexao -> {
            Optional<UUID> existente = idPor(conexao, documento.origem(),
                    documento.caminho(), documento.hashSha256());
            if (existente.isPresent()) {
                return new DocumentoGravado(existente.get(), false);
            }
            String sql = """
                    INSERT INTO documento (origem, caminho, nome_arquivo, hash_sha256, tamanho,
                                           mime_real, formato, status_triagem, ocr,
                                           competencia_extraida, validade_extraida,
                                           cnpj_extraido, natureza, versao_origem, criado_por)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setString(1, documento.origem());
                ps.setString(2, documento.caminho());
                ps.setString(3, documento.nomeArquivo());
                ps.setString(4, documento.hashSha256());
                ps.setLong(5, documento.tamanho());
                ps.setString(6, documento.mimeReal());
                ps.setString(7, documento.formato());
                ps.setString(8, documento.statusTriagem());
                ps.setBoolean(9, documento.ocr());
                ps.setString(10, documento.competenciaExtraida());
                ps.setObject(11, documento.validadeExtraida());
                ps.setString(12, documento.cnpjExtraido());
                ps.setString(13, documento.natureza());
                ps.setString(14, documento.versaoOrigem());
                ps.setString(15, documento.criadoPor());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new DocumentoGravado(rs.getObject(1, UUID.class), true);
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia(
                        "falha ao gravar o documento " + documento.nomeArquivo(), e);
            }
        });
    }

    private static Optional<UUID> idPor(Connection conexao, String origem, String caminho,
                                        String hash) {
        String sql = "SELECT id FROM documento WHERE origem = ? AND caminho = ? "
                + "AND hash_sha256 = ?";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, origem);
            ps.setString(2, caminho);
            ps.setString(3, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao procurar o documento por hash", e);
        }
    }

    /** Os hashes já vinculados a uma exigência — o que V7 precisa saber. */
    public java.util.Set<String> hashesDaExigencia(UUID exigenciaId) {
        String sql = """
                SELECT d.hash_sha256
                FROM vinculo_exigencia_documento v
                JOIN documento d ON d.id = v.documento_id
                WHERE v.exigencia_id = ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, exigenciaId);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<String> hashes = new java.util.LinkedHashSet<>();
                while (rs.next()) {
                    hashes.add(rs.getString(1));
                }
                return hashes;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os hashes da exigência", e);
        }
    }

    /**
     * Grava os campos extraídos com a região de origem.
     *
     * <p>A posição é o que a história F1-02 exige e o cap. 16 sustenta: um valor
     * sem origem obriga o auditor a reler o documento inteiro para conferir um
     * número. Regravar o mesmo campo substitui — reprocessar com regra nova deve
     * dar o valor novo, não dois valores.
     */
    public int gravarCampos(UUID documentoId, List<CampoExtraido> campos) {
        return sgdf.emTransacao(conexao -> {
            String sql = """
                    INSERT INTO campo_extraido (documento_id, campo, valor, confianca, posicao)
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (documento_id, campo)
                    DO UPDATE SET valor = EXCLUDED.valor,
                                  confianca = EXCLUDED.confianca,
                                  posicao = EXCLUDED.posicao
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                for (CampoExtraido c : campos) {
                    ps.setObject(1, documentoId);
                    ps.setString(2, c.campo());
                    ps.setString(3, c.valor());
                    ps.setBigDecimal(4, java.math.BigDecimal.valueOf(c.confianca()));
                    ps.setString(5, posicaoEmJson(c));
                    ps.addBatch();
                }
                int[] afetados = ps.executeBatch();
                return afetados.length;
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao gravar campos extraídos", e);
            }
        });
    }

    public Map<String, String> camposDe(UUID documentoId) {
        String sql = "SELECT campo, valor FROM campo_extraido WHERE documento_id = ? "
                + "ORDER BY campo";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, String> campos = new LinkedHashMap<>();
                while (rs.next()) {
                    campos.put(rs.getString(1), rs.getString(2));
                }
                return campos;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler campos extraídos", e);
        }
    }

    static String posicaoEmJson(CampoExtraido campo) {
        List<String> retangulos = new ArrayList<>();
        for (Retangulo r : campo.posicao().retangulos()) {
            retangulos.add(String.format(java.util.Locale.ROOT,
                    "{\"x\":%.2f,\"y\":%.2f,\"largura\":%.2f,\"altura\":%.2f}",
                    r.x(), r.y(), r.largura(), r.altura()));
        }
        return "{\"pagina\":" + campo.posicao().pagina()
                + ",\"retangulos\":[" + String.join(",", retangulos) + "]}";
    }
}
