package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.notificacao.Aviso;
import br.com.engesoftware.sgdf.notificacao.Conteudo;
import br.com.engesoftware.sgdf.notificacao.Destinatario;
import br.com.engesoftware.sgdf.notificacao.Momento;
import br.com.engesoftware.sgdf.notificacao.PapelNoAviso;
import br.com.engesoftware.sgdf.notificacao.PendenciaAberta;
import br.com.engesoftware.sgdf.notificacao.Regua;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Roda a régua de um ciclo e registra o que seria enviado — história F1-09.
 *
 * <p><b>Não há envio aqui, e não há como acrescentá-lo por engano.</b> Esta
 * classe lê, decide e grava; a linha em {@code notificacao} nasce com
 * {@code enviada_em} nulo porque nada foi enviado, não porque um sinalizador
 * disse para não enviar. Quando o transporte existir (fase 3), ele será um
 * colaborador separado que lê estas linhas — e a régua continuará sem saber
 * enviar.
 *
 * <p>Consequência disso, e é deliberada: se alguém desligar
 * {@code notificacao.modo_sombra} antes de existir transporte, a execução
 * <b>falha</b> em vez de gravar linhas dizendo "enviado". Fingir envio é pior
 * que não enviar: a área não recebe, o sistema jura que mandou, e a discussão
 * seguinte não tem como ser resolvida.
 */
public final class RepositorioDeNotificacao {

    /** Cap. 11.2 / F1-09. Ausente = ligado: sistema não configurado não cobra ninguém. */
    public static final String CHAVE_MODO_SOMBRA = "notificacao.modo_sombra";

    private final Sgdf sgdf;

    public RepositorioDeNotificacao(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Executa a régua do dia e grava os avisos.
     *
     * @return o que seria enviado e o que ficou sem destinatário
     */
    public Execucao executar(UUID cicloId, LocalDate hoje) {
        if (!modoSombra()) {
            throw new SemTransporte("o modo sombra está desligado e não há transporte "
                    + "configurado (história F1-09 entrega a régua, não o envio). "
                    + "Gravar as linhas como enviadas seria registrar um envio que "
                    + "não aconteceu — religue " + CHAVE_MODO_SOMBRA);
        }
        Cadastro cadastro = cadastroDoCiclo(cicloId, hoje);
        Regua.Resultado resultado = Regua.avisos(cicloId, hoje, pendencias(cicloId),
                cicloPronto(cicloId), cadastro);
        Map<Momento, Conteudo.Template> templates = templatesVigentes();

        return sgdf.emTransacao(conexao -> {
            List<Registrado> registrados = new ArrayList<>();
            for (Aviso aviso : resultado.avisos()) {
                Conteudo.Template template = templates.get(aviso.momento());
                if (template == null) {
                    throw new SemTemplate("não há template vigente para "
                            + aviso.momento() + ". Um aviso sem template não tem conteúdo "
                            + "identificável, e o hash gravado não provaria nada");
                }
                Conteudo.Montado montado = Conteudo.montar(aviso, template, null);
                gravar(conexao, aviso, montado);
                registrados.add(new Registrado(aviso, montado));
            }
            TrilhaDeAuditoria.registrar(conexao, new TrilhaDeAuditoria.Registro(
                    "sistema", "SVC_REGUA", "NOTIFICACAO_MODO_SOMBRA", "ciclo",
                    cicloId.toString(), "SUCESSO",
                    Map.of("avisos", List.of(String.valueOf(registrados.size())),
                            "sem_destinatario", resultado.semDestinatario())));
            return new Execucao(List.copyOf(registrados), resultado.semDestinatario(), true);
        });
    }

    /**
     * Grava o que teria sido enviado.
     *
     * <p>{@code ON CONFLICT DO NOTHING} sobre {@code notificacao_diaria_unica}:
     * rodar a régua duas vezes no mesmo dia não produz o segundo e-mail que o
     * cap. 11.2 proíbe. A consolidação é imposta pelo banco, não confiada ao
     * agendador — que pode ser disparado duas vezes por qualquer motivo.
     */
    private void gravar(Connection conexao, Aviso aviso, Conteudo.Montado montado) {
        String sql = """
                INSERT INTO notificacao (ciclo_id, tipo, destinatario, conteudo_hash,
                                         template_versao, modo_sombra, data_referencia)
                VALUES (?, ?, ?, ?, ?, true, ?)
                ON CONFLICT ON CONSTRAINT notificacao_diaria_unica DO NOTHING
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, aviso.cicloId());
            ps.setString(2, aviso.momento().codigoNoBanco());
            ps.setString(3, aviso.destinatario().email());
            ps.setString(4, montado.hash());
            ps.setString(5, montado.templateVersao());
            ps.setObject(6, aviso.dataReferencia());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao registrar o aviso", e);
        }
    }

    // --- leituras ------------------------------------------------------------

    boolean modoSombra() {
        String sql = "SELECT valor::text FROM parametro WHERE chave = ? AND escopo = 'GLOBAL'";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, CHAVE_MODO_SOMBRA);
            try (ResultSet rs = ps.executeQuery()) {
                // Ausente = ligado. Um parâmetro que ninguém cadastrou não
                // autoriza cobrar as áreas.
                return !rs.next() || !"false".equals(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o modo sombra", e);
        }
    }

    /** As pendências do ciclo — abertas, e as resolvidas que ainda cabem no aviso. */
    List<PendenciaAberta> pendencias(UUID cicloId) {
        String sql = """
                SELECT p.exigencia_id, t.codigo, t.familia, p.prazo, p.resolvida_em::date
                FROM   pendencia p
                JOIN   exigencia e ON e.id = p.exigencia_id
                JOIN   tipo_documental t ON t.id = e.tipo_id
                WHERE  e.ciclo_id = ?
                ORDER  BY p.prazo, t.codigo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PendenciaAberta> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new PendenciaAberta(rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getString(3), rs.getObject(4, LocalDate.class),
                            rs.getObject(5, LocalDate.class)));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as pendências do ciclo", e);
        }
    }

    boolean cicloPronto(UUID cicloId) {
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(
                "SELECT status FROM ciclo WHERE id = ?")) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "PRONTO".equals(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o estado do ciclo", e);
        }
    }

    /**
     * Carrega o cadastro de destinatários vigente na data.
     *
     * <p>Carrega tudo de uma vez em vez de consultar por (família, papel): a
     * régua faz uma pergunta por pendência por papel, e uma ida ao banco por
     * pergunta transformaria um ciclo de 176 exigências em centenas de
     * consultas idênticas.
     */
    Cadastro cadastroDoCiclo(UUID cicloId, LocalDate data) {
        String sql = """
                SELECT d.familia, d.papel, d.nome, d.email
                FROM   destinatario d
                JOIN   ciclo c ON c.contrato_servico_id = d.contrato_servico_id
                WHERE  c.id = ?
                  AND  d.vigencia_ini <= ?
                  AND  (d.vigencia_fim IS NULL OR d.vigencia_fim >= ?)
                """;
        Map<String, Destinatario> porChave = new LinkedHashMap<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            ps.setObject(2, data);
            ps.setObject(3, data);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String familia = rs.getString(1);
                    PapelNoAviso papel = PapelNoAviso.valueOf(rs.getString(2));
                    porChave.put(chave(familia, papel),
                            new Destinatario(rs.getString(3), rs.getString(4), papel, familia));
                }
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os destinatários", e);
        }
        return new Cadastro(porChave);
    }

    Map<Momento, Conteudo.Template> templatesVigentes() {
        String sql = """
                SELECT momento, versao, assunto, corpo
                FROM   template_notificacao
                WHERE  vigente
                """;
        Map<Momento, Conteudo.Template> templates = new EnumMap<>(Momento.class);
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // Os dois níveis de escalonamento compartilham o template, como
                // compartilham o tipo no banco: o nível está no conteúdo.
                for (Momento m : Momento.values()) {
                    if (m.codigoNoBanco().equals(rs.getString(1))) {
                        templates.put(m, new Conteudo.Template(m, rs.getString(2),
                                rs.getString(3), rs.getString(4)));
                    }
                }
            }
            return templates;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os templates", e);
        }
    }

    private static String chave(String familia, PapelNoAviso papel) {
        return (familia == null ? "*" : familia) + "|" + papel;
    }

    /**
     * O cadastro já carregado, com a queda para a linha genérica.
     *
     * <p>Cap. 11.2: o destinatário é por (contrato × família). GESTOR e DAF não
     * variam por família e ficam cadastrados com família nula; a resolução
     * prefere o específico e cai no genérico.
     */
    static final class Cadastro implements Regua.Cadastro {
        private final Map<String, Destinatario> porChave;

        Cadastro(Map<String, Destinatario> porChave) {
            this.porChave = porChave;
        }

        @Override
        public Destinatario quem(String familia, PapelNoAviso papel) {
            Destinatario especifico = porChave.get(chave(familia, papel));
            return especifico != null ? especifico : porChave.get(chave(null, papel));
        }
    }

    /** Um aviso registrado, com o conteúdo que teria sido enviado. */
    public record Registrado(Aviso aviso, Conteudo.Montado conteudo) {}

    /**
     * O resultado da execução.
     *
     * @param modoSombra sempre true hoje — e o campo existe para que o dia em
     *                   que deixar de ser apareça no registro, e não passe
     *                   despercebido porque ninguém olhava
     */
    public record Execucao(List<Registrado> registrados, List<String> semDestinatario,
                           boolean modoSombra) {

        public boolean completa() {
            return semDestinatario.isEmpty();
        }
    }

    /** O modo sombra foi desligado sem que exista transporte. */
    public static final class SemTransporte extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SemTransporte(String motivo) {
            super(motivo);
        }
    }

    /** Falta o template vigente do momento. */
    public static final class SemTemplate extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SemTemplate(String motivo) {
            super(motivo);
        }
    }
}
