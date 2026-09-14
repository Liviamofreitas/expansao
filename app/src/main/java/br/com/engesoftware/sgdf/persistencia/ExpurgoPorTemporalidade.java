package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.retencao.Temporalidade;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplica a tabela de temporalidade — pendência A08, requisito LGPD-02.
 *
 * <p><b>A trava de autorização não é um {@code if} desta classe.</b> O INSERT em
 * {@code expurgo} copia {@code temporalidade.aprovado_em} para
 * {@code expurgo.autorizado_em}, que é NOT NULL, e acontece no <i>mesmo comando</i>
 * que o DELETE. Uma classe sem aprovação viola o NOT NULL, o comando inteiro
 * aborta, e o DELETE não acontece. Se alguém apagar a verificação em Java, o
 * banco continua recusando; se alguém apagar a verificação do banco, sobra a de
 * Java. É a mesma escolha da V002 para a trilha: duas primitivas, não uma.
 *
 * <p><b>Por que um comando só, com CTEs, em vez de três em sequência.</b> Em
 * READ COMMITTED cada comando enxerga um instantâneo novo. Contar num comando,
 * registrar noutro e apagar num terceiro deixaria a contagem registrada e a
 * quantidade apagada poderem divergir — e o registro do expurgo é a única prova
 * que sobra depois que o dado some. Num comando só, as três partes compartilham
 * o mesmo instantâneo e o mesmo destino: ou tudo vale, ou nada.
 *
 * <p><b>O modo de falha que esta classe existe para não ter.</b> Hoje nenhuma
 * classe está aprovada, então o expurgo apaga zero. Um job que roda todo mês,
 * apaga zero e sai como SUCESSO faria a não conformidade da A08 sobreviver anos
 * sem sintoma — o mesmo padrão da F0-05, da completude na primeira conferência,
 * do agendador morto e do ciclo vazio. Por isso <b>"apagou zero porque nada
 * venceu" e "apagou zero porque ninguém aprovou" são resultados diferentes</b>:
 * o primeiro vira linha em {@code expurgo} com {@code itens = 0}; o segundo não
 * vira linha nenhuma — não pode — e sai como recusa nomeada.
 */
public final class ExpurgoPorTemporalidade {

    private final Sgdf sgdf;

    public ExpurgoPorTemporalidade(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Percorre a tabela de temporalidade e aplica o que estiver autorizado.
     *
     * @param execucaoJobId a execução que produziu este expurgo, ou nulo quando
     *                      rodado à mão
     */
    public Resultado executar(LocalDate hoje, String ator, Long execucaoJobId) {
        if (hoje == null || ator == null || ator.isBlank()) {
            throw new IllegalArgumentException("expurgo exige a data de referência e o ator");
        }
        List<Temporalidade> classes = new RepositorioDeTemporalidade(sgdf).todas();

        List<Lote> lotes = new ArrayList<>();
        List<Revisao> revisoes = new ArrayList<>();
        List<Recusa> recusas = new ArrayList<>();

        for (Temporalidade t : classes) {
            String impedimento = t.impedimento();
            if (impedimento != null) {
                recusas.add(new Recusa(t.alvo(), t.acao(), impedimento));
                continue;
            }
            LocalDate corte = t.corte(hoje);
            if (t.acao() == Temporalidade.Acao.REVISAR) {
                revisoes.add(revisar(t, corte));
            } else {
                lotes.add(eliminar(t, corte, ator, execucaoJobId));
            }
        }
        return new Resultado(hoje, List.copyOf(lotes), List.copyOf(revisoes),
                List.copyOf(recusas));
    }

    // -------------------------------------------------------------------------

    /**
     * Registra e apaga, num comando só.
     *
     * <p>O {@code SELECT} final devolve três números que <b>têm</b> de coincidir:
     * o que o registro diz que apagou e o que o DELETE apagou de fato. Hoje eles
     * coincidem por construção — o DELETE lê a mesma CTE que a contagem. A
     * verificação existe para o dia em que alguém "otimizar" o DELETE repetindo
     * o predicado em vez de reler a CTE: aí as duas contagens passam a ser
     * populações diferentes, e um registro de eliminação que diz um número
     * diferente do que aconteceu é pior que registro nenhum.
     */
    private Lote eliminar(Temporalidade t, LocalDate corte, String ator, Long execucaoJobId) {
        Fonte fonte = Fonte.de(t.alvo());
        String sql = """
                WITH vencidos AS (
                    SELECT id, %2$s AS marco_em
                    FROM   %1$s
                    WHERE  %2$s < ?
                ), lote AS (
                    INSERT INTO expurgo (temporalidade_id, autorizado_em, alvo, acao,
                                         corte, itens, executado_por, execucao_job_id)
                    SELECT t.id, t.aprovado_em, t.alvo, t.acao,
                           ?, (SELECT count(*) FROM vencidos), ?, ?
                    FROM   temporalidade t
                    WHERE  t.id = ?::uuid
                    RETURNING id, itens
                ), registrados AS (
                    INSERT INTO expurgo_item (expurgo_id, identificador, marco_em)
                    SELECT (SELECT id FROM lote), v.id::text, v.marco_em
                    FROM   vencidos v
                    RETURNING 1
                ), apagados AS (
                    DELETE FROM %1$s WHERE id IN (SELECT id FROM vencidos)
                    RETURNING 1
                )
                SELECT (SELECT id FROM lote),
                       (SELECT itens FROM lote),
                       (SELECT count(*) FROM apagados),
                       (SELECT count(*) FROM registrados)
                """.formatted(fonte.tabela(), fonte.marco());

        return sgdf.emTransacao(conexao -> {
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, corte);
                ps.setObject(2, corte);
                ps.setString(3, ator);
                if (execucaoJobId == null) {
                    ps.setNull(4, Types.BIGINT);
                } else {
                    ps.setLong(4, execucaoJobId);
                }
                ps.setString(5, t.id());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long loteId = rs.getLong(1);
                    int registradoComoItens = rs.getInt(2);
                    long apagados = rs.getLong(3);
                    long itensGravados = rs.getLong(4);
                    if (registradoComoItens != apagados || itensGravados != apagados) {
                        throw new Sgdf.FalhaDePersistencia(
                                "expurgo de " + t.alvo() + " incoerente: o registro diz "
                                + registradoComoItens + " item(ns), foram gravados "
                                + itensGravados + " e apagados " + apagados
                                + " — a transação foi desfeita", null);
                    }
                    return new Lote(loteId, t.alvo(), t.acao(), corte, (int) apagados);
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia(
                        "falha ao expurgar " + t.alvo() + " com corte em " + corte, e);
            }
        });
    }

    /**
     * Lista o que venceu, sem apagar nada — a ação REVISAR.
     *
     * <p><b>Não grava em {@code expurgo}, e a ausência é a decisão.</b> Aquela
     * tabela significa "algo deixou de existir"; uma revisão não eliminou coisa
     * alguma. Registrá-la ali faria {@code itens} querer dizer duas coisas —
     * apagados numa linha, listados na outra —, e o CHECK
     * {@code expurgo_acao_elimina} recusa a tentativa.
     *
     * <p>O documento entra na lista só quando <b>todos</b> os vínculos que tem
     * apontam para profissionais desligados antes do corte. Documento de escopo
     * CONTRATO ou CORPORATIVO não tem profissional, logo não tem de onde contar
     * o prazo, logo fica — e documento de alguém ainda na casa fica também. O
     * marco registrado é o desligamento <i>mais recente</i> entre os vinculados:
     * o documento sobrevive enquanto durar o prazo do último que saiu.
     */
    private Revisao revisar(Temporalidade t, LocalDate corte) {
        String sql = """
                SELECT d.id::text, max(p.desligamento)
                FROM   documento d
                JOIN   vinculo_exigencia_documento v ON v.documento_id = d.id
                JOIN   exigencia e ON e.id = v.exigencia_id
                LEFT   JOIN profissional p ON p.id = e.profissional_id
                GROUP  BY d.id
                HAVING count(*) FILTER (
                           WHERE p.desligamento IS NULL OR p.desligamento >= ?) = 0
                ORDER  BY 2
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, corte);
            try (ResultSet rs = ps.executeQuery()) {
                List<Vencido> vencidos = new ArrayList<>();
                while (rs.next()) {
                    vencidos.add(new Vencido(rs.getString(1),
                            rs.getObject(2, LocalDate.class)));
                }
                return new Revisao(t.alvo(), corte, List.copyOf(vencidos));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia(
                    "falha ao listar " + t.alvo() + " vencido em " + corte, e);
        }
    }

    /** De onde sai a data que conta o prazo, por alvo. */
    private record Fonte(String tabela, String marco) {

        static Fonte de(Temporalidade.Alvo alvo) {
            return switch (alvo) {
                case ACESSO_OBSERVADO -> new Fonte("acesso_observado", "dia");
                case NOTIFICACAO -> new Fonte("notificacao", "criado_em::date");
                // Inalcançável por Temporalidade.impedimento(), que já recusa o
                // par sem executor. Lançar em vez de devolver nulo faz a
                // divergência entre as duas listas aparecer como erro, e não
                // como um alvo que some do resultado sem ninguém notar.
                default -> throw new IllegalStateException(
                        "sem fonte de marco para o alvo " + alvo
                        + " — Temporalidade.Alvo diz que o SGDF sabe eliminá-lo e "
                        + "o ExpurgoPorTemporalidade não sabe de onde contar");
            };
        }
    }

    // --- resultados -----------------------------------------------------------

    /** Uma eliminação registrada. {@code itens} zero é resultado, não ausência. */
    public record Lote(long id, Temporalidade.Alvo alvo, Temporalidade.Acao acao,
                       LocalDate corte, int itens) {
    }

    /** O que venceu e espera decisão humana. Nada foi apagado. */
    public record Revisao(Temporalidade.Alvo alvo, LocalDate corte, List<Vencido> vencidos) {

        public int quantidade() {
            return vencidos.size();
        }
    }

    /** Um item vencido, pela chave. Nunca pelo conteúdo. */
    public record Vencido(String identificador, LocalDate marcoEm) {
    }

    /** Uma política que existe e não foi cumprida — e por quê. */
    public record Recusa(Temporalidade.Alvo alvo, Temporalidade.Acao acao, String motivo) {
    }

    /**
     * O desfecho da passada inteira.
     *
     * <p>{@link #itens()} conta <b>só o que foi eliminado</b>. Somar as revisões
     * aqui faria o número que o job registra significar duas coisas ao mesmo
     * tempo, e um dia alguém leria "1.200 itens tratados" num mês em que nada
     * foi apagado. O que foi listado e o que foi recusado vive no
     * {@link #resumo()}, que é o texto que vai para o detalhe da execução.
     */
    public record Resultado(LocalDate referencia, List<Lote> lotes, List<Revisao> revisoes,
                            List<Recusa> recusas) {

        /** Quantos itens foram efetivamente eliminados. */
        public int itens() {
            return lotes.stream().mapToInt(Lote::itens).sum();
        }

        /** Quantos aguardam decisão humana. */
        public int aRevisar() {
            return revisoes.stream().mapToInt(Revisao::quantidade).sum();
        }

        /** Há política escrita que o sistema não cumpriu. */
        public boolean temNaoConformidade() {
            return !recusas.isEmpty();
        }

        /**
         * A frase que vai para {@code execucao_de_job.detalhe}.
         *
         * <p>Sempre presente, inclusive quando tudo correu bem. Um SUCESSO sem
         * detalhe num job de expurgo é indistinguível de um SUCESSO que não
         * aplicou política nenhuma, e é exatamente essa confusão que a A08
         * precisa não ter.
         */
        public String resumo() {
            StringBuilder texto = new StringBuilder();
            texto.append("corte em ").append(referencia).append("; ")
                 .append(itens()).append(" item(ns) eliminado(s) em ")
                 .append(lotes.size()).append(" classe(s) autorizada(s)");
            if (!revisoes.isEmpty()) {
                texto.append("; ").append(aRevisar())
                     .append(" item(ns) vencido(s) aguardando revisão humana");
            }
            if (recusas.isEmpty()) {
                return texto.toString();
            }
            texto.append("; NÃO CONFORMIDADE (A08): ").append(recusas.size())
                 .append(" classe(s) não aplicada(s)");
            for (Recusa r : recusas) {
                texto.append(" · ").append(r.alvo()).append('/').append(r.acao())
                     .append(": ").append(r.motivo());
            }
            return texto.toString();
        }
    }
}
