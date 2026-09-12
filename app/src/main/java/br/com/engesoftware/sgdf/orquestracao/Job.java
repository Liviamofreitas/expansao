package br.com.engesoftware.sgdf.orquestracao;

import java.time.Duration;

/**
 * O que o sistema executa sozinho — e o que ele deliberadamente não executa.
 *
 * <p><b>A derivação de eventos está aqui, e está marcada como não agendável.</b>
 * O cap. 7.2 é explícito: <i>"Executada quando a folha estruturada da competência
 * chega (integração 14.3). <b>Nunca por calendário.</b>"</i> Deixá-la fora do
 * enum faria a regra sobreviver só na cabeça de quem leu o capítulo; pô-la
 * dentro com {@link Disparo#EVENTO} faz o {@code Agendador} recusá-la em tempo
 * de execução e o leitor do enum ver a proibição junto do que ela proíbe.
 *
 * <p>A razão da regra não é estilo: derivar eventos por calendário
 * materializaria exigências de férias, 13º e rescisão para uma competência cuja
 * folha ainda não chegou — cobrando as áreas por documentos de eventos que
 * talvez não tenham acontecido, e contaminando a completude com denominador
 * inventado.
 *
 * <p><b>A cadência é declarada e não vira cron aqui.</b> O cron vive no
 * ambiente, que é onde ele pode ser mudado sem recompilar. O que o enum declara
 * é a <i>expectativa</i>: de quanto em quanto tempo este job deveria ter
 * rodado. É disso que o {@code Agendador} monta o alerta de job silencioso —
 * comparar o que se esperava com o que aconteceu é a única forma de notar um
 * agendador morto.
 */
public enum Job {

    /**
     * Cap. 7.1: materializa as exigências da competência.
     *
     * <p>Mensal, com folga larga: um atraso de horas não muda nada, e um ciclo
     * aberto duas vezes é barrado pela unicidade {@code ciclo_unico}.
     */
    ABERTURA_DE_CICLOS(Disparo.CALENDARIO, Duration.ofDays(31)),

    /** Cap. 8.1: varredura completa diária, de madrugada. */
    VARREDURA_COMPLETA(Disparo.CALENDARIO, Duration.ofHours(25)),

    /** Cap. 8.1: incremental a cada 30 min no horário comercial. */
    VARREDURA_INCREMENTAL(Disparo.CALENDARIO, Duration.ofHours(2)),

    /** Cap. 9: reprocessa as regras dos ciclos que mudaram. */
    CONCILIACAO(Disparo.CALENDARIO, Duration.ofHours(25)),

    /** Cap. 11.1: a régua do dia. Uma vez por dia, e o banco garante 1/área/dia. */
    REGUA_DE_NOTIFICACAO(Disparo.CALENDARIO, Duration.ofHours(25)),

    /**
     * Cap. 7.2: <b>nunca por calendário</b>.
     *
     * <p>Sem expectativa de cadência: não há "deveria ter rodado" para um job
     * que só roda quando a folha chega. Um alerta de silêncio sobre ele
     * dispararia todo mês em que não houve folha nova, que é a maioria.
     */
    DERIVACAO_DE_EVENTOS(Disparo.EVENTO, null);

    /** Quem puxa o gatilho. */
    public enum Disparo {
        /** O agendador. */
        CALENDARIO,
        /** Um fato externo — hoje, só a chegada da folha (cap. 14.3). */
        EVENTO
    }

    private final Disparo disparo;
    private final Duration cadenciaEsperada;

    Job(Disparo disparo, Duration cadenciaEsperada) {
        this.disparo = disparo;
        this.cadenciaEsperada = cadenciaEsperada;
    }

    public Disparo disparo() {
        return disparo;
    }

    /** Nulo em job por evento: não há cadência que se espere dele. */
    public Duration cadenciaEsperada() {
        return cadenciaEsperada;
    }

    public boolean agendavel() {
        return disparo == Disparo.CALENDARIO;
    }

    /** Aceita o texto do banco; desconhecido devolve nulo, nunca um job. */
    public static Job de(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return valueOf(texto.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
