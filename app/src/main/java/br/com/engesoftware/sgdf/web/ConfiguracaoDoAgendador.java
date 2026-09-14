package br.com.engesoftware.sgdf.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * O pool do agendador — e o defeito que o pool padrão do Spring produziria.
 *
 * <p><b>O {@code @Scheduled} do Spring usa UMA thread por padrão.</b> Com dois
 * jobs agendados, se a varredura completa levar vinte minutos, a régua marcada
 * para dentro dessa janela simplesmente <b>não roda</b> — e não roda em silêncio:
 * nenhum erro, nenhum log, nenhuma linha em {@code execucao_de_job}, porque o
 * job nunca começou.
 *
 * <p>É exatamente o modo de falha que o § 46 existe para pegar, e o alerta de
 * silêncio o pegaria — mas um dia depois, e depois de uma competência inteira
 * sem cobrança. O pool custa nada e remove a causa.
 *
 * <p><b>Tamanho declarado.</b> Um por job de calendário, mais um de folga para
 * que uma execução longa não bloqueie a que vem a seguir. Acrescentar job ao
 * enum sem mexer aqui reduz a folga; por isso o número sai do próprio enum em
 * vez de ser constante.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sgdf.agendador.ativo", havingValue = "true")
public class ConfiguracaoDoAgendador {

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        int agendaveis = (int) java.util.Arrays
                .stream(br.com.engesoftware.sgdf.orquestracao.Job.values())
                .filter(br.com.engesoftware.sgdf.orquestracao.Job::agendavel)
                .count();
        ThreadPoolTaskScheduler pool = new ThreadPoolTaskScheduler();
        pool.setPoolSize(agendaveis + 1);
        pool.setThreadNamePrefix("sgdf-job-");
        // Não deixa o desligamento cortar um job no meio: uma varredura
        // interrompida deixa linha aberta em execucao_de_job, e a linha aberta
        // vira alerta de "job travado" sobre algo que só foi reiniciado.
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(60);
        return pool;
    }
}
