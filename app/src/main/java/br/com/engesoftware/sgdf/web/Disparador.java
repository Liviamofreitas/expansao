package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.orquestracao.Job;
import br.com.engesoftware.sgdf.persistencia.AberturaDeCiclos;
import br.com.engesoftware.sgdf.persistencia.Agendador;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeNotificacao;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chama os jobs no relógio — o que faltava para a RA-07 deixar de ser mecanismo.
 *
 * <p><b>Desligado por padrão, e a chave é de ligar.</b> {@code @ConditionalOnProperty}
 * sem {@code matchIfMissing} significa que este componente <b>não existe</b> a
 * menos que alguém escreva {@code sgdf.agendador.ativo=true}. A ausência de
 * configuração não liga nada — é a mesma decisão do {@code Autorizador} (negar é
 * o padrão) e da adesão por contrato da F3-02 (o que é perigoso não se herda).
 *
 * <p>Ligar continua gated na F3-01: o sistema só deve cobrar áreas depois de a
 * divergência estar medida (risco P01, cap. 22). Mas na **Onda 1** do plano de
 * implantação ele roda sem notificar ninguém — e aí o agendador é justamente o
 * que faz o painel ter o que mostrar.
 *
 * <p><b>Cada job numa conexão própria.</b> A {@code Sgdf} dos controladores é
 * ligada à requisição HTTP, e aqui não há requisição. Abrir e fechar por
 * execução também é o que faz o lock consultivo morrer com a sessão quando a
 * JVM cai (ver {@code Agendador}).
 */
@Component
@ConditionalOnProperty(name = "sgdf.agendador.ativo", havingValue = "true")
public class Disparador {

    private static final Logger LOG = LoggerFactory.getLogger(Disparador.class);

    private final DataSource dataSource;
    private final String instancia;

    public Disparador(DataSource dataSource,
                      @Value("${SGDF_INSTANCIA:desconhecida}") String instancia) {
        this.dataSource = dataSource;
        this.instancia = instancia;
        LOG.warn("Agendador LIGADO nesta instância ({}). Os jobs do cap. 7.6 passam a "
                + "executar sozinhos. Desligue com sgdf.agendador.ativo=false.", instancia);
    }

    /** Cap. 7.6: abertura de ciclos. Diário, e idempotente por competência. */
    @Scheduled(cron = "${sgdf.agendador.cron.abertura:0 10 3 * * *}",
               zone = "America/Sao_Paulo")
    public void abrirCiclos() {
        executar(Job.ABERTURA_DE_CICLOS, (sgdf, agendador) -> {
            AberturaDeCiclos.Abertura a = new AberturaDeCiclos(sgdf)
                    .abrir(YearMonth.now(), "svc-agendador");
            if (!a.completa()) {
                throw new Agendador.FalhaParcial(a.falhas().size()
                        + " contrato(s) não abriram: " + String.join("; ", a.falhas()),
                        a.abertos().size());
            }
            return a.abertos().size();
        });
    }

    /** Cap. 11.1: a régua do dia. O banco garante 1 e-mail por área por dia. */
    @Scheduled(cron = "${sgdf.agendador.cron.regua:0 0 8 * * *}", zone = "America/Sao_Paulo")
    public void regua() {
        executar(Job.REGUA_DE_NOTIFICACAO, (sgdf, agendador) -> {
            RepositorioDeNotificacao repo = new RepositorioDeNotificacao(sgdf);
            int avisos = 0;
            List<String> falhas = new java.util.ArrayList<>();
            for (UUID ciclo : ciclosAbertos(sgdf)) {
                try {
                    avisos += repo.executar(ciclo, LocalDate.now()).registrados().size();
                } catch (RuntimeException e) {
                    // Um ciclo sem template ou sem destinatário não pode calar a
                    // régua dos outros catorze.
                    falhas.add(ciclo + ": " + e.getClass().getSimpleName());
                }
            }
            if (!falhas.isEmpty()) {
                throw new Agendador.FalhaParcial(falhas.size() + " ciclo(s) sem régua: "
                        + String.join("; ", falhas), avisos);
            }
            return avisos;
        });
    }

    // -------------------------------------------------------------------------

    /**
     * Abre a conexão, executa sob o {@code Agendador}, e fecha sempre.
     *
     * <p><b>A exceção não sobe.</b> Uma exceção que escapa de um método
     * {@code @Scheduled} só é registrada no log — e, dependendo da configuração,
     * pode cancelar a próxima execução. Aqui o {@code Agendador} já gravou o
     * desfecho em {@code execucao_de_job}, que é onde o alerta de silêncio o
     * encontra; deixá-la subir não acrescentaria nada e arriscaria calar o job.
     */
    private void executar(Job job, Trabalho trabalho) {
        try (java.sql.Connection conexao = dataSource.getConnection()) {
            conexao.setAutoCommit(true);
            Sgdf sgdf = new Sgdf(conexao);
            Agendador agendador = new Agendador(sgdf, instancia);
            Agendador.Execucao e = agendador.executar(job,
                    () -> trabalho.executar(sgdf, agendador));
            LOG.info("{}: {} ({} item(ns))", job, e.resultado(), e.itens());
        } catch (Agendador.FalhaParcial e) {
            LOG.error("{}: falha parcial após {} item(ns) — {}", job, e.itens(),
                    e.getMessage());
        } catch (Exception e) {
            LOG.error("{}: falhou", job, e);
        }
    }

    private static List<UUID> ciclosAbertos(Sgdf sgdf) {
        String sql = """
                SELECT id FROM ciclo
                WHERE  status IN ('ABERTO', 'EM_COLETA', 'BLOQUEADO', 'REABERTO')
                ORDER  BY competencia DESC
                """;
        try (java.sql.PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            List<UUID> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getObject(1, UUID.class));
            }
            return ids;
        } catch (java.sql.SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao listar os ciclos abertos", e);
        }
    }

    @FunctionalInterface
    interface Trabalho {
        int executar(Sgdf sgdf, Agendador agendador);
    }
}
