package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.book.ArmazenamentoImutavel;
import br.com.engesoftware.sgdf.book.Book;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Registra a publicação do book.
 *
 * <p>A tabela {@code book} é append-only por desenho: {@code UNIQUE (ciclo_id,
 * versao)} e nenhuma atualização. Republicar grava {@code v{N+1}}; a linha da
 * versão anterior fica.
 *
 * <p>O registro no banco e o objeto no bucket são <b>duas gravações em sistemas
 * diferentes</b>, e não há transação distribuída. A ordem escolhida é: publicar
 * no bucket primeiro, registrar depois. Se a segunda falhar, existe um book no
 * bucket sem linha no banco — visível e recuperável. A ordem inversa produziria
 * uma linha afirmando um book que não existe, que é o erro que ninguém percebe.
 */
public final class RepositorioDeBook {

    private final Sgdf sgdf;

    public RepositorioDeBook(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    public UUID registrar(UUID cicloId, Book book, String manifestoJson,
                          ArmazenamentoImutavel.Retencao retencao) {
        return sgdf.emTransacao(conexao -> {
            String sql = """
                    INSERT INTO book (ciclo_id, versao, publicado_por, hash_conjunto,
                                      manifesto, caminho_bucket, pecas,
                                      retencao_modo, retencao_ate)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    RETURNING id
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, cicloId);
                ps.setInt(2, book.versao());
                ps.setString(3, book.publicadoPor());
                ps.setString(4, book.hashDoConjunto());
                ps.setString(5, manifestoJson);
                ps.setString(6, book.caminhoBucket());
                ps.setInt(7, book.pecas().size());
                ps.setString(8, retencao.modo().name());
                ps.setObject(9, retencao.ate());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao registrar o book v"
                        + book.versao() + " do ciclo " + cicloId, e);
            }
        });
    }

    /** A próxima versão a publicar — 1 quando o ciclo nunca publicou. */
    public int proximaVersao(UUID cicloId) {
        String sql = "SELECT coalesce(max(versao), 0) + 1 FROM book WHERE ciclo_id = ?";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a próxima versão do book", e);
        }
    }
}
