package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.indicadores.Csv;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitura e exportação da trilha — cap. 13 e 16, papel AUDITORIA.
 *
 * <p><b>A permissão AUDITAR existia e nenhum endpoint a usava.</b> Uma
 * permissão sem uso é pior que uma permissão faltando: ela aparece na matriz de
 * acesso, passa na recertificação (F3-06) e não dá acesso a nada — a
 * organização acredita ter um controle que não tem.
 *
 * <p><b>O filtro é obrigatório e o limite também.</b> A trilha cresce sem
 * limite por construção (append-only, V002), e um {@code GET /auditoria} sem
 * recorte devolveria a tabela inteira. Não é só carga: é que uma exportação
 * completa da trilha, num sistema que registra CPF em detalhe de decisão, é o
 * pior arquivo possível para sair sem justificativa.
 */
public final class ConsultaDeAuditoria {

    /** Teto por consulta. Quem precisa de mais paginа com {@code desde}. */
    public static final int LIMITE_MAXIMO = 5000;

    private final Sgdf sgdf;

    public ConsultaDeAuditoria(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Filtra por objeto, ator e período — os três parâmetros do cap. 13.
     *
     * <p>Pelo menos um precisa vir preenchido: ver a nota da classe.
     */
    public List<Registro> consultar(Filtro filtro, int limite) {
        if (filtro.vazio()) {
            throw new FiltroObrigatorio("a consulta à trilha exige ao menos um filtro — "
                    + "objeto, ator ou período (cap. 13). Sem recorte, a exportação é a "
                    + "trilha inteira, que é o pior arquivo possível para sair sem motivo");
        }
        int teto = Math.min(Math.max(limite, 1), LIMITE_MAXIMO);
        String sql = """
                SELECT ocorrido_em, ator, papel, acao, objeto_tipo, objeto_id, resultado,
                       detalhe::text
                FROM   log_auditoria
                WHERE  (?::text IS NULL OR objeto_tipo = ?)
                  AND  (?::text IS NULL OR objeto_id = ?)
                  AND  (?::text IS NULL OR ator = ?)
                  AND  (?::timestamptz IS NULL OR ocorrido_em >= ?::timestamptz)
                  AND  (?::timestamptz IS NULL OR ocorrido_em <= ?::timestamptz)
                ORDER  BY ocorrido_em DESC
                LIMIT  ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            par(ps, 1, filtro.objetoTipo());
            par(ps, 3, filtro.objetoId());
            par(ps, 5, filtro.ator());
            par(ps, 7, filtro.desde() == null ? null : filtro.desde().toString());
            par(ps, 9, filtro.ate() == null ? null : filtro.ate().toString());
            ps.setInt(11, teto);
            try (ResultSet rs = ps.executeQuery()) {
                List<Registro> registros = new ArrayList<>();
                while (rs.next()) {
                    registros.add(new Registro(rs.getObject(1, OffsetDateTime.class),
                            rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getString(6), rs.getString(7), rs.getString(8)));
                }
                return registros;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao consultar a trilha", e);
        }
    }

    /**
     * A exportação em CSV do cap. 13.
     *
     * <p>O {@code detalhe} carrega texto que uma pessoa escreveu — motivo de
     * exceção, motivo de rejeição de triagem. É por aí que uma fórmula entraria
     * na planilha de quem audita, e é o {@link Csv} que a neutraliza.
     *
     * <p><b>Exportar a trilha entra na própria trilha.</b> Sem isto, a única
     * operação do sistema que produz um arquivo com o histórico inteiro seria a
     * única que não deixa registro — e quem investigasse um vazamento não teria
     * como saber quem baixou o quê. O registro grava o FILTRO, não o conteúdo:
     * é o que permite reconstruir o recorte sem duplicar o dado pessoal na
     * própria trilha.
     */
    public String csv(Filtro filtro, int limite, String ator, String papel) {
        List<Registro> registros = consultar(filtro, limite);
        Csv csv = new Csv("ocorrido_em", "ator", "papel", "acao", "objeto_tipo", "objeto_id",
                "resultado", "detalhe");
        for (Registro r : registros) {
            csv.linha(r.ocorridoEm(), r.ator(), r.papel(), r.acao(), r.objetoTipo(),
                    r.objetoId(), r.resultado(), r.detalhe());
        }
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, "AUDITORIA_EXPORTAR", "log_auditoria", null,
                    java.util.Map.of("filtro", List.of(String.valueOf(filtro),
                            registros.size() + " registro(s)"))));
            return null;
        });
        return csv.texto();
    }

    private static void par(PreparedStatement ps, int posicao, String valor)
            throws SQLException {
        ps.setString(posicao, valor);
        ps.setString(posicao + 1, valor);
    }

    /** Os três recortes do cap. 13. */
    public record Filtro(String objetoTipo, String objetoId, String ator,
                         OffsetDateTime desde, OffsetDateTime ate) {

        public boolean vazio() {
            return branco(objetoTipo) && branco(objetoId) && branco(ator)
                    && desde == null && ate == null;
        }

        private static boolean branco(String s) {
            return s == null || s.isBlank();
        }
    }

    /** Uma linha da trilha, como o cap. 16 a exige: quem, quando, o quê, com que resultado. */
    public record Registro(OffsetDateTime ocorridoEm, String ator, String papel, String acao,
                           String objetoTipo, String objetoId, String resultado,
                           String detalhe) {
    }

    /** Consultar a trilha inteira não é consultar: é exportar. */
    public static final class FiltroObrigatorio extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FiltroObrigatorio(String motivo) {
            super(motivo);
        }
    }
}
