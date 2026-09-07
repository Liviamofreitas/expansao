package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.notificacao.Ativacao;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lê e escreve {@code parametro}, e decide a ativação por contrato — F3-02.
 *
 * <p><b>A recusa acontece no momento de ligar, não no momento de enviar.</b> É
 * a decisão que dá a esta história o seu valor. Hoje o
 * {@code RepositorioDeNotificacao} já recusa executar sem transporte — mas
 * recusa <i>na execução</i>, que acontece um dia depois de alguém ter virado a
 * chave. Nesse intervalo, quem virou acredita ter ativado a cobrança, a área
 * acredita que será cobrada, e as duas crenças são falsas ao mesmo tempo.
 *
 * <p>Uma feature flag que falha no uso em vez de falhar na troca dá à
 * organização dias de confiança falsa. {@link #ativarEnvio} falha na troca.
 */
public final class RepositorioDeParametro {

    /**
     * Existe transporte de e-mail configurado?
     *
     * <p>Constante, e é deliberado: não há transporte no código (RA-06), e um
     * parâmetro de banco dizendo que há não o faria existir. Quando o
     * transporte chegar, isto vira a verificação real — e o dia em que alguém
     * trocar esta constante é o dia em que o compilador obriga a olhar para os
     * quatro pontos que dependem dela.
     */
    public static final boolean EXISTE_TRANSPORTE = false;

    private final Sgdf sgdf;

    public RepositorioDeParametro(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /** A situação de um contrato, com a origem da decisão. */
    public Ativacao ativacaoDe(UUID contratoId) {
        return Ativacao.decidir(modoSombraGlobal(), contratoAderiu(contratoId),
                EXISTE_TRANSPORTE);
    }

    /**
     * Liga o envio para um contrato — e recusa enquanto não houver transporte.
     *
     * <p>O motivo é obrigatório porque ativar a cobrança de uma área é decisão
     * de governança: alguém vai perguntar quem autorizou, e a trilha precisa
     * responder mais que "fulano ligou".
     */
    public void ativarEnvio(UUID contratoId, String motivo, String ator, String papel) {
        if (motivo == null || motivo.strip().length() < 20) {
            throw new AtivacaoInvalida("ativar o envio exige motivo de ao menos 20 "
                    + "caracteres: é a decisão que faz o sistema começar a cobrar pessoas");
        }
        if (!EXISTE_TRANSPORTE) {
            // Registra a TENTATIVA antes de recusar, e fora da transação que a
            // recusa desfaria — a mesma lição da F0-09 (achados § 35.4).
            registrarNegado(contratoId, ator, papel, "não há transporte configurado (RA-06)");
            throw new SemTransporte("não há transporte de e-mail configurado (RA-06): "
                    + "ligar a adesão agora criaria um contrato que se declara ativo e não "
                    + "envia nada — a área acreditaria estar sendo cobrada. A adesão fica "
                    + "disponível quando o transporte existir");
        }
        gravar(contratoId, Ativacao.CHAVE_ENVIO_ATIVO, "true",
                "Adesão do contrato ao envio de notificações (F3-02)", ator);
        registrar(contratoId, ator, papel, "NOTIFICACAO_ATIVAR", motivo.strip());
    }

    /**
     * Devolve o contrato ao modo sombra.
     *
     * <p>Sem motivo obrigatório e sem porta nenhuma: voltar para sombra é
     * sempre a direção segura, e exigir justificativa para parar de cobrar
     * criaria atrito onde ele não protege ninguém.
     */
    public void desativarEnvio(UUID contratoId, String ator, String papel) {
        gravar(contratoId, Ativacao.CHAVE_ENVIO_ATIVO, "false",
                "Contrato devolvido ao modo sombra (F3-02)", ator);
        registrar(contratoId, ator, papel, "NOTIFICACAO_DESATIVAR", "volta ao modo sombra");
    }

    /** Os contratos e a sua situação — a tela da F3-02. */
    public List<SituacaoDoContrato> situacaoDosContratos() {
        boolean sombra = modoSombraGlobal();
        String sql = """
                SELECT cs.id, cs.numero, cl.nome,
                       coalesce((p.valor #>> '{}') = 'true', false)
                FROM   contrato_servico cs
                JOIN   cliente cl ON cl.id = cs.cliente_id
                LEFT   JOIN parametro p ON p.contrato_id = cs.id
                                       AND p.escopo = 'CONTRATO' AND p.chave = ?
                ORDER  BY cl.nome, cs.numero
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, Ativacao.CHAVE_ENVIO_ATIVO);
            try (ResultSet rs = ps.executeQuery()) {
                List<SituacaoDoContrato> linhas = new ArrayList<>();
                while (rs.next()) {
                    linhas.add(new SituacaoDoContrato(rs.getObject(1, UUID.class),
                            rs.getString(2), rs.getString(3),
                            Ativacao.decidir(sombra, rs.getBoolean(4), EXISTE_TRANSPORTE)));
                }
                return linhas;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a situação dos contratos", e);
        }
    }

    // -------------------------------------------------------------------------

    /** Ausente = ligada. Um parâmetro que ninguém cadastrou não autoriza cobrar. */
    boolean modoSombraGlobal() {
        return !"false".equals(valor(Ativacao.CHAVE_MODO_SOMBRA, null));
    }

    /**
     * Só o escopo CONTRATO conta.
     *
     * <p>Deliberadamente <b>sem</b> a cascata contrato → cliente → global que os
     * outros parâmetros usam. Ver a nota do {@link Ativacao}: o que é perigoso
     * não se herda.
     */
    boolean contratoAderiu(UUID contratoId) {
        return "true".equals(valor(Ativacao.CHAVE_ENVIO_ATIVO, contratoId));
    }

    private String valor(String chave, UUID contratoId) {
        String sql = contratoId == null
                ? "SELECT valor #>> '{}' FROM parametro WHERE chave = ? AND escopo = 'GLOBAL'"
                : "SELECT valor #>> '{}' FROM parametro WHERE chave = ? AND escopo = 'CONTRATO'"
                        + " AND contrato_id = ?";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, chave);
            if (contratoId != null) {
                ps.setObject(2, contratoId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o parâmetro " + chave, e);
        }
    }

    private void gravar(UUID contratoId, String chave, String valor, String descricao,
                        String ator) {
        String sql = """
                INSERT INTO parametro (chave, escopo, contrato_id, valor, descricao, criado_por)
                VALUES (?, 'CONTRATO', ?, ?::jsonb, ?, ?)
                ON CONFLICT (chave, contrato_id) WHERE escopo = 'CONTRATO'
                DO UPDATE SET valor = EXCLUDED.valor, atualizado_em = now(),
                              atualizado_por = EXCLUDED.criado_por
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, chave);
            ps.setObject(2, contratoId);
            ps.setString(3, valor);
            ps.setString(4, descricao);
            ps.setString(5, ator);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao gravar o parâmetro " + chave, e);
        }
    }

    private void registrar(UUID contratoId, String ator, String papel, String acao,
                           String motivo) {
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, acao, "contrato_servico", contratoId.toString(),
                    Map.of("motivo", List.of(motivo))));
            return null;
        });
    }

    private void registrarNegado(UUID contratoId, String ator, String papel, String motivo) {
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, new TrilhaDeAuditoria.Registro(
                    ator, papel, "NOTIFICACAO_ATIVAR", "contrato_servico",
                    contratoId.toString(), "NEGADO", Map.of("motivo", List.of(motivo))));
            return null;
        });
    }

    /** Uma linha da tela de ativação. */
    public record SituacaoDoContrato(UUID contratoId, String numero, String cliente,
                                     Ativacao ativacao) {
    }

    /** Ligar sem transporte criaria um contrato que se declara ativo e não envia. */
    public static final class SemTransporte extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SemTransporte(String motivo) {
            super(motivo);
        }
    }

    public static final class AtivacaoInvalida extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AtivacaoInvalida(String motivo) {
            super(motivo);
        }
    }
}
