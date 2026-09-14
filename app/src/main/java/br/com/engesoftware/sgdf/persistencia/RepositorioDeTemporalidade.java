package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.retencao.Temporalidade;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Leitura e aprovação da tabela de temporalidade (A08 / LGPD-02). */
public final class RepositorioDeTemporalidade {

    private final Sgdf sgdf;

    public RepositorioDeTemporalidade(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Todas as classes, aprovadas ou não.
     *
     * <p><b>Não filtra pelas aprovadas.</b> Filtrar aqui faria o expurgo ver só
     * o que pode fazer e nunca o que deixou de fazer — e é a segunda lista que
     * o DPO precisa ler. Uma classe proposta e ignorada em silêncio é a
     * pendência A08 sobrevivendo sem sintoma.
     */
    public List<Temporalidade> todas() {
        String sql = """
                SELECT id::text, classe, alvo, marco, prazo_meses, acao, fundamento,
                       aprovado_em, aprovado_por
                FROM   temporalidade
                ORDER  BY alvo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Temporalidade> classes = new ArrayList<>();
            while (rs.next()) {
                classes.add(new Temporalidade(
                        rs.getString(1),
                        rs.getString(2),
                        Temporalidade.Alvo.de(rs.getString(3)),
                        Temporalidade.Marco.de(rs.getString(4)),
                        rs.getInt(5),
                        Temporalidade.Acao.de(rs.getString(6)),
                        rs.getString(7),
                        rs.getObject(8, OffsetDateTime.class),
                        rs.getString(9)));
            }
            return List.copyOf(classes);
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a tabela de temporalidade", e);
        }
    }

    /**
     * O último expurgo registrado de cada alvo — a evidência de que a política vive.
     *
     * <p>Alvo <b>sem</b> linha nenhuma aparece assim mesmo, com nulos. É o ponto:
     * "nunca executou" e "executou e não achou nada" são estados diferentes, e é
     * só a linha com {@code itens = 0} que distingue os dois. Uma consulta que
     * filtrasse os alvos sem execução devolveria a lista curta e verde de sempre.
     */
    public List<UltimoExpurgo> ultimoPorAlvo() {
        String sql = """
                SELECT t.alvo, t.acao, e.executado_em, e.corte, e.itens, e.executado_por
                FROM   temporalidade t
                LEFT   JOIN LATERAL (
                    SELECT x.executado_em, x.corte, x.itens, x.executado_por
                    FROM   expurgo x
                    WHERE  x.temporalidade_id = t.id
                    ORDER  BY x.executado_em DESC
                    LIMIT  1
                ) e ON true
                ORDER  BY t.alvo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<UltimoExpurgo> ultimos = new ArrayList<>();
            while (rs.next()) {
                ultimos.add(new UltimoExpurgo(
                        Temporalidade.Alvo.de(rs.getString(1)),
                        Temporalidade.Acao.de(rs.getString(2)),
                        rs.getObject(3, OffsetDateTime.class),
                        rs.getObject(4, LocalDate.class),
                        rs.getObject(5, Integer.class),
                        rs.getString(6)));
            }
            return List.copyOf(ultimos);
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os expurgos registrados", e);
        }
    }

    /** @param executadoEm nulo quando a política nunca foi executada */
    public record UltimoExpurgo(Temporalidade.Alvo alvo, Temporalidade.Acao acao,
                                OffsetDateTime executadoEm, LocalDate corte,
                                Integer itens, String executadoPor) {

        public boolean nuncaExecutou() {
            return executadoEm == null;
        }
    }

    /**
     * Aprova uma classe — o ato que liga o expurgo daquele alvo.
     *
     * <p>Exige nome. O banco também exige (CHECK {@code
     * temporalidade_aprovacao_completa}), e a redundância é deliberada: esta é a
     * decisão que autoriza destruir dado, e "quem autorizou?" é a primeira
     * pergunta de qualquer auditoria que venha depois.
     *
     * @return falso se a classe não existe ou já estava aprovada — reaprovar
     *         sobrescreveria a data e o nome de quem decidiu de verdade
     */
    public boolean aprovar(Temporalidade.Alvo alvo, String aprovadoPor, OffsetDateTime quando) {
        if (alvo == null || aprovadoPor == null || aprovadoPor.isBlank() || quando == null) {
            throw new IllegalArgumentException(
                    "aprovar temporalidade exige alvo, nome de quem aprova e data");
        }
        String sql = """
                UPDATE temporalidade
                SET    aprovado_em = ?, aprovado_por = ?
                WHERE  alvo = ? AND aprovado_em IS NULL
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, quando);
            ps.setString(2, aprovadoPor);
            ps.setString(3, alvo.name());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao aprovar a temporalidade", e);
        }
    }
}
