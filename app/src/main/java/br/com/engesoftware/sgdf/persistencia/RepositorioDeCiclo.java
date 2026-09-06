package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.ciclo.EstadoDoCiclo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Move o ciclo pelo cap. 6.2 e mede o D+3 — histórias F3-03 e F3-04.
 *
 * <p>Critérios de aceite: F3-03, <i>"painel exibe tempo ateste→NF por contrato
 * e o % ≥ 98% da política"</i>; F3-04, <i>"ciclo com divergência bloqueante não
 * publica; dispensa aprovada libera com trilha"</i>.
 *
 * <p><b>A pré-condição vive no WHERE, e isso não é redundância.</b> Cada
 * transição é checada duas vezes: em Java, onde existe a mensagem que explica o
 * que falta; e dentro do próprio {@code UPDATE}, repetida como cláusula. As
 * duas parecem a mesma verificação e não são — a de Java acontece num instante,
 * a escrita acontece noutro, e entre os dois cabe uma exceção sendo aprovada,
 * um book sendo publicado ou outra pessoa clicando no mesmo botão. O banco é o
 * único lugar onde ler a condição e gravar o efeito acontecem sem intervalo.
 * Um {@code executeUpdate()} que devolve zero é, portanto, informação: alguém
 * mudou o estado no meio do caminho.
 *
 * <p><b>E a suíte NÃO mede essa cláusula — dito aqui para não ser confundido
 * com garantia.</b> Removê-la deliberadamente não derruba nenhuma das 57
 * asserções (achados § 41.2). Não porque ela seja inútil, mas porque o que ela
 * cobre é a janela entre a leitura em Java e a gravação, e reproduzir essa
 * janela exigiria pausar o código <i>dentro</i> da transação — o que este
 * código não expõe, e expor só para o teste seria pior que não medir. A guarda
 * em Java, essa sim, está medida: sem ela, duas asserções caem e duas nem
 * chegam a rodar, porque a recusa muda de tipo e perde a lista do que falta.
 * A cláusula fica pelo que o banco garante, não pelo que o teste prova.
 *
 * <p><b>Registrar não é transitar.</b> {@link #registrarAteste} grava o ateste
 * e deixa o ciclo onde está; mover para ATESTADO é um segundo ato. Fundir os
 * dois pareceria conveniente e apagaria a diferença entre "o cliente atestou" e
 * "nós consideramos o ciclo atestado" — e é do primeiro fato, não do segundo,
 * que o relógio do D+3 conta.
 */
public final class RepositorioDeCiclo {

    private final Sgdf sgdf;

    public RepositorioDeCiclo(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Move o ciclo, com motivo, e registra na trilha.
     *
     * <p>Cap. 6.2: <i>"transições registradas em log_auditoria com ator e
     * motivo"</i>. O motivo é obrigatório porque a transição interessante é a
     * que sai do caminho feliz — BLOQUEADO e REABERTO — e um BLOQUEADO sem
     * motivo é um ciclo travado sem ninguém saber por quê.
     */
    public Transicao mover(UUID cicloId, EstadoDoCiclo destino, String ator, String papel,
                           String motivo) {
        if (destino == null) {
            throw new TransicaoInvalida("destino não informado");
        }
        if (motivo == null || motivo.strip().length() < 10) {
            throw new TransicaoInvalida("toda transição de ciclo exige motivo de ao menos 10 "
                    + "caracteres (cap. 6.2): sem ele a trilha diz quem mexeu e não diz por quê");
        }
        String razao = motivo.strip();

        return sgdf.emTransacao(conexao -> {
            EstadoDoCiclo atual = estadoDe(conexao, cicloId);
            if (!atual.podeIrPara(destino)) {
                throw new TransicaoInvalida("o cap. 6.2 não permite " + atual + " → " + destino
                        + "; de " + atual + " sai-se para " + atual.destinos());
            }
            if (destino.exigeBloqueantesResolvidas()) {
                List<String> abertas = bloqueantesEmAberto(conexao, cicloId);
                if (!abertas.isEmpty()) {
                    throw new BloqueantesEmAberto("o ciclo não vai a PRONTO com exigência "
                            + "bloqueante em aberto (cap. 6.2): " + String.join("; ", abertas),
                            abertas);
                }
            }

            // A MESMA CONDIÇÃO, AGORA SEM INTERVALO ENTRE LER E ESCREVER.
            // A CONCATENACAO COMECA COM ESPACO, E ISSO NAO E ESTILO.
            //
            // Um bloco de texto Java remove a indentacao comum de todas as
            // linhas — inclusive da primeira. Escrever o trecho como bloco
            // produzia "c.status = ?AND NOT EXISTS", que o Postgres recusa com
            // "trailing junk after parameter". O defeito atingia SO a transicao
            // para PRONTO; as outras duas ja eram literais com espaco. Um teste
            // que so exercitasse ATESTADO e FATURADO nunca o veria.
            String extra = switch (destino) {
                case PRONTO -> " AND NOT EXISTS ("
                        + " SELECT 1 FROM exigencia x"
                        + " WHERE x.ciclo_id = c.id AND x.criticidade = 'BLOQUEANTE'"
                        + " AND x.status NOT IN ('PUBLICADO', 'DISPENSADO'))";
                case ATESTADO -> " AND c.ateste_em IS NOT NULL AND c.ateste_forma IS NOT NULL";
                case FATURADO -> " AND c.nf_emitida_em IS NOT NULL";
                default -> "";
            };
            String sql = "UPDATE ciclo c SET status = ?, atualizado_em = now(),"
                    + " atualizado_por = ? WHERE c.id = ? AND c.status = ?"
                    + extra;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setString(1, destino.name());
                ps.setString(2, ator);
                ps.setObject(3, cicloId);
                ps.setString(4, atual.name());
                if (ps.executeUpdate() != 1) {
                    throw new TransicaoInvalida("o ciclo " + cicloId + " mudou entre a "
                            + "verificação e a gravação, ou a pré-condição de " + destino
                            + " deixou de valer: nada foi movido");
                }
            } catch (SQLException e) {
                // A V016 RECUSANDO NÃO É "O BANCO FALHOU".
                //
                // Medido: removida a guarda em Java, as restrições da V016
                // continuam impedindo ATESTADO sem ateste e FATURADO sem NF — o
                // ciclo fica protegido. Mas a recusa chegava à camada web como
                // FalhaDePersistencia, e viraria 500 onde o certo é 422: o
                // pedido é inválido, não o sistema. Traduzir aqui é o que faz a
                // rede do banco produzir a mesma resposta que a guarda de Java.
                if ("23514".equals(e.getSQLState())) {
                    throw new TransicaoInvalida("o banco recusou " + destino + " no ciclo "
                            + cicloId + ": os marcos que o estado afirma não estão gravados "
                            + "(restrições da V016)");
                }
                throw new Sgdf.FalhaDePersistencia("falha ao mover o ciclo", e);
            }

            if (destino == EstadoDoCiclo.FECHADO) {
                marcar(conexao, "UPDATE ciclo SET fechado_em = now() WHERE id = ?", cicloId);
            }

            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, "CICLO_" + destino, "ciclo", cicloId.toString(),
                    Map.of("de", List.of(atual.name()), "para", List.of(destino.name()),
                            "motivo", List.of(razao))));
            return new Transicao(cicloId, atual, destino);
        });
    }

    /**
     * Registra o ateste do cliente — o marco do D+3 (cap. 21).
     *
     * <p>Não move o ciclo: ver a nota da classe. E não aceita ateste no futuro,
     * porque o D+3 conta a partir daqui e uma data adiante inventaria folga.
     *
     * @param forma como o cliente atestou (e-mail, portal, ofício) — a evidência
     *              de que houve ateste, quando não há documento anexo
     * @param evidenciaDocumentoId documento do ateste, quando existe
     */
    public void registrarAteste(UUID cicloId, OffsetDateTime quando, String forma,
                                UUID evidenciaDocumentoId, String ator, String papel) {
        if (quando == null) {
            throw new TransicaoInvalida("ateste sem data não é marco de nada (cap. 21)");
        }
        if (forma == null || forma.isBlank()) {
            throw new TransicaoInvalida("o ateste exige a forma — como o cliente atestou "
                    + "(cap. 13): sem ela não há o que auditar quando o ateste for contestado");
        }
        if (quando.isAfter(OffsetDateTime.now())) {
            throw new TransicaoInvalida("ateste em data futura: o relógio do D+3 conta a "
                    + "partir dele, e antecipá-lo inventa prazo que não existe");
        }

        sgdf.emTransacao(conexao -> {
            EstadoDoCiclo atual = estadoDe(conexao, cicloId);
            String sql = """
                    UPDATE ciclo SET ateste_em = ?, ateste_forma = ?,
                                     ateste_evidencia_doc_id = ?,
                                     atualizado_em = now(), atualizado_por = ?
                    WHERE id = ? AND ateste_em IS NULL
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, quando);
                ps.setString(2, forma.strip());
                if (evidenciaDocumentoId == null) {
                    ps.setNull(3, Types.OTHER);
                } else {
                    ps.setObject(3, evidenciaDocumentoId);
                }
                ps.setString(4, ator);
                ps.setObject(5, cicloId);
                if (ps.executeUpdate() != 1) {
                    // O ateste é fato do cliente, não campo editável. Reescrevê-lo
                    // moveria o marco do D+3 depois de o relógio ter começado —
                    // que é a forma mais silenciosa de o indicador ficar bonito.
                    throw new TransicaoInvalida("o ciclo " + cicloId + " já tem ateste "
                            + "registrado; corrigir a data é reabrir o ciclo, não sobrescrever "
                            + "o marco do D+3");
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao registrar o ateste", e);
            }

            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, "CICLO_ATESTE", "ciclo", cicloId.toString(),
                    Map.of("quando", List.of(quando.toString()), "forma", List.of(forma.strip()),
                            "estado", List.of(atual.name()))));
            return null;
        });
    }

    /** Registra a emissão da NF — o outro extremo do D+3. */
    public void registrarNotaFiscal(UUID cicloId, OffsetDateTime quando, String ator,
                                    String papel) {
        if (quando == null) {
            throw new TransicaoInvalida("emissão de NF sem data não fecha o D+3 (cap. 21)");
        }
        if (quando.isAfter(OffsetDateTime.now())) {
            throw new TransicaoInvalida("NF emitida em data futura: o indicador mediria um "
                    + "intervalo que ainda não passou");
        }

        sgdf.emTransacao(conexao -> {
            String sql = """
                    UPDATE ciclo SET nf_emitida_em = ?, atualizado_em = now(),
                                     atualizado_por = ?
                    WHERE id = ? AND nf_emitida_em IS NULL AND ateste_em IS NOT NULL
                      AND ateste_em <= ?
                    """;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, quando);
                ps.setString(2, ator);
                ps.setObject(3, cicloId);
                ps.setObject(4, quando);
                if (ps.executeUpdate() != 1) {
                    // Três recusas numa: NF já registrada, ciclo sem ateste, ou NF
                    // anterior ao ateste. As três produziriam a MESMA distorção no
                    // indicador — um intervalo negativo ou inexistente contado como
                    // "dentro do prazo" —, e por isso a mensagem cita as três.
                    throw new TransicaoInvalida("não foi possível registrar a NF do ciclo "
                            + cicloId + ": ou ela já está registrada, ou o ciclo ainda não tem "
                            + "ateste, ou a emissão é anterior ao ateste — e um intervalo "
                            + "ateste→NF negativo entraria no D+3 como se fosse cumprimento");
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao registrar a NF", e);
            }

            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, "CICLO_NF", "ciclo", cicloId.toString(),
                    Map.of("quando", List.of(quando.toString()))));
            return null;
        });
    }

    /** O que impede o ciclo de ir a PRONTO — o portão rígido do cap. 6.2. */
    public List<String> bloqueantesEmAberto(UUID cicloId) {
        return bloqueantesEmAberto(sgdf.conexao(), cicloId);
    }

    /**
     * O indicador do cap. 21 — "NF em D+3", meta ≥ 98%, e o tempo médio.
     *
     * <p><b>O denominador são os ciclos FATURADOS, e é o que torna o número
     * honesto.</b> Contar sobre todos os ciclos da competência premiaria o
     * atraso: um ciclo que ainda não faturou não está fora do prazo, está fora
     * da conta — e incluí-lo no denominador faria o percentual cair quando o
     * mês começa e subir sozinho quando ele termina, medindo o calendário em vez
     * do processo. O oposto — contar só os que faturaram <i>dentro</i> do prazo
     * — daria 100% sempre. É a mesma armadilha de comparar populações
     * diferentes que aparece na conciliação: o resultado sai bonito e falso.
     *
     * <p>Percentual nulo quando não há ciclo faturado: zero de zero não é 0%
     * nem 100%, e devolver qualquer um dos dois faria o painel afirmar sobre uma
     * competência da qual nada se sabe ainda.
     */
    public IndicadorD3 indicadorD3(String competencia) {
        String sql = """
                SELECT count(*) FILTER (WHERE nf_emitida_em IS NOT NULL),
                       count(*) FILTER (WHERE nf_emitida_em IS NOT NULL
                                          AND nf_emitida_em <= ateste_em + INTERVAL '3 days'),
                       avg(EXTRACT(EPOCH FROM (nf_emitida_em - ateste_em)) / 86400.0)
                         FILTER (WHERE nf_emitida_em IS NOT NULL)
                FROM   ciclo
                WHERE  competencia = ? AND ateste_em IS NOT NULL
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, competencia);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                java.math.BigDecimal media = rs.getBigDecimal(3);
                return new IndicadorD3(competencia, rs.getInt(1), rs.getInt(2),
                        media == null ? null : media.setScale(2,
                                java.math.RoundingMode.HALF_UP));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao calcular o indicador D+3", e);
        }
    }

    /** O tempo ateste→NF de cada contrato — o recorte que a F3-03 pede no painel. */
    public List<TempoPorContrato> tempoPorContrato(String competencia) {
        String sql = """
                SELECT cs.numero, c.id, c.status, c.ateste_em, c.nf_emitida_em,
                       EXTRACT(EPOCH FROM (c.nf_emitida_em - c.ateste_em)) / 86400.0
                FROM   ciclo c
                JOIN   contrato_servico cs ON cs.id = c.contrato_servico_id
                WHERE  c.competencia = ? AND c.ateste_em IS NOT NULL
                ORDER  BY cs.numero
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, competencia);
            try (ResultSet rs = ps.executeQuery()) {
                List<TempoPorContrato> linhas = new ArrayList<>();
                while (rs.next()) {
                    java.math.BigDecimal dias = rs.getBigDecimal(6);
                    linhas.add(new TempoPorContrato(rs.getString(1), rs.getObject(2, UUID.class),
                            rs.getString(3), rs.getObject(4, OffsetDateTime.class),
                            rs.getObject(5, OffsetDateTime.class),
                            dias == null ? null
                                    : dias.setScale(2, java.math.RoundingMode.HALF_UP)));
                }
                return linhas;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o tempo ateste→NF", e);
        }
    }

    /** O estado atual — para a tela oferecer só as ações que existem. */
    public EstadoDoCiclo estadoDe(UUID cicloId) {
        return estadoDe(sgdf.conexao(), cicloId);
    }

    // -------------------------------------------------------------------------

    static EstadoDoCiclo estadoDe(Connection conexao, UUID cicloId) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT status FROM ciclo WHERE id = ?")) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new TransicaoInvalida("ciclo " + cicloId + " não existe");
                }
                EstadoDoCiclo estado = EstadoDoCiclo.de(rs.getString(1));
                if (estado == null) {
                    // O CHECK do V001 impede isto. Se acontecer, alguém alterou o
                    // esquema sem alterar o enum, e seguir adiante escolheria um
                    // estado arbitrário para um ciclo real.
                    throw new Sgdf.FalhaDePersistencia("o ciclo " + cicloId + " está num "
                            + "status que o cap. 6.2 não conhece: " + rs.getString(1), null);
                }
                return estado;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o estado do ciclo", e);
        }
    }

    /**
     * As bloqueantes fora de PUBLICADO/DISPENSADO.
     *
     * <p>Note a diferença para {@code ConsultaDoPainel.motivosDeBloqueio}, que
     * também aceita CONCILIADO: aquele responde "posso publicar o book?", este
     * responde "posso declarar o ciclo pronto?". Ver o javadoc de
     * {@code EstadoDoCiclo}.
     */
    private static List<String> bloqueantesEmAberto(Connection conexao, UUID cicloId) {
        String sql = """
                SELECT t.codigo, e.status, count(*)
                FROM   exigencia e
                JOIN   tipo_documental t ON t.id = e.tipo_id
                WHERE  e.ciclo_id = ?
                  AND  e.criticidade = 'BLOQUEANTE'
                  AND  e.status NOT IN ('PUBLICADO', 'DISPENSADO')
                GROUP  BY t.codigo, e.status
                ORDER  BY t.codigo, e.status
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> abertas = new ArrayList<>();
                while (rs.next()) {
                    abertas.add(rs.getString(1) + ": " + rs.getInt(3) + " em " + rs.getString(2));
                }
                return abertas;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as exigências bloqueantes", e);
        }
    }

    private static void marcar(Connection conexao, String sql, UUID cicloId) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao marcar o ciclo", e);
        }
    }

    /** O movimento que aconteceu — de onde e para onde. */
    public record Transicao(UUID cicloId, EstadoDoCiclo de, EstadoDoCiclo para) {}

    /**
     * O indicador do cap. 21.
     *
     * @param mediaEmDias nulo quando não há ciclo faturado — ver {@link #percentual()}
     */
    public record IndicadorD3(String competencia, int faturados, int dentroDoPrazo,
                              java.math.BigDecimal mediaEmDias) {

        /** Nulo quando não há faturado: zero de zero não é 0% nem 100%. */
        public java.math.BigDecimal percentual() {
            if (faturados == 0) {
                return null;
            }
            return java.math.BigDecimal.valueOf(dentroDoPrazo * 100L)
                    .divide(java.math.BigDecimal.valueOf(faturados), 2,
                            java.math.RoundingMode.HALF_UP);
        }

        /** A meta do cap. 21. Sem faturado não se atinge nem se descumpre. */
        public boolean atingeAMeta() {
            java.math.BigDecimal p = percentual();
            return p != null && p.compareTo(java.math.BigDecimal.valueOf(98)) >= 0;
        }
    }

    /** Uma linha do painel da F3-03. {@code dias} nulo enquanto a NF não sai. */
    public record TempoPorContrato(String contrato, UUID cicloId, String status,
                                   OffsetDateTime atesteEm, OffsetDateTime nfEmitidaEm,
                                   java.math.BigDecimal dias) {
    }

    /** O cap. 6.2 não permite este movimento, ou a pré-condição não vale. */
    public static final class TransicaoInvalida extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TransicaoInvalida(String motivo) {
            super(motivo);
        }
    }

    /** F3-04: ciclo com bloqueante em aberto não vai a PRONTO. */
    public static final class BloqueantesEmAberto extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient List<String> abertas;

        public BloqueantesEmAberto(String motivo, List<String> abertas) {
            super(motivo);
            this.abertas = List.copyOf(abertas);
        }

        public List<String> abertas() {
            return abertas;
        }
    }
}
