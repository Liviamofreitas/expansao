package br.com.engesoftware.sgdf.notificacao;

/**
 * Se a notificação de um contrato pode sair do modo sombra — história F3-02.
 *
 * <p>Critério de aceite: <i>"régua completa D-2/D+0/D+2/D+5; máx. 1 e-mail/área
 * /dia"</i>. A régua e a consolidação vieram na F1-09; o que falta é
 * <b>quem decide, e por contrato</b>.
 *
 * <p><b>Duas chaves, e não uma com escopo.</b> A tentação é resolver
 * {@code notificacao.modo_sombra} por escopo — contrato, senão cliente, senão
 * global — como qualquer outro parâmetro. Seria errado, e de um jeito
 * silencioso: alguém desligando o modo sombra <i>globalmente</i> para testar um
 * contrato ativaria a cobrança de <b>todos</b>, e o sintoma apareceria como
 * e-mails saindo para áreas que nunca foram avisadas de que o sistema começou a
 * cobrá-las.
 *
 * <ul>
 *   <li>{@link #CHAVE_MODO_SOMBRA} é <b>global</b> e é uma chave de desligar:
 *       enquanto ligada, nada sai, de contrato nenhum.</li>
 *   <li>{@link #CHAVE_ENVIO_ATIVO} é <b>por contrato</b> e é uma adesão
 *       explícita: só o contrato que tem o parâmetro cadastrado envia.</li>
 * </ul>
 *
 * <p><b>A regra que faz isso valer: a permissão não se herda.</b> O modo sombra
 * — que é seguro — herda do escopo mais amplo; o envio ativo — que é perigoso —
 * <b>não herda de lugar nenhum</b>. Um contrato sem parâmetro próprio está em
 * sombra, e nenhuma configuração de cliente ou global o tira de lá. É a mesma
 * decisão do {@code Autorizador}: negar é o padrão, e o padrão não se alcança
 * por omissão de quem configurou.
 *
 * <p>Ativar exige, portanto, <b>duas decisões independentes</b>: desligar a
 * chave global e aderir contrato a contrato.
 */
public record Ativacao(boolean envioAtivo, Origem origem, String motivo) {

    /** Chave global de desligar. Ausente = ligada: quem não cadastrou não autorizou. */
    public static final String CHAVE_MODO_SOMBRA = "notificacao.modo_sombra";

    /** Adesão por contrato. Só vale no escopo CONTRATO — ver a nota da classe. */
    public static final String CHAVE_ENVIO_ATIVO = "notificacao.envio_ativo";

    /** De onde veio a decisão — o que a tela mostra a quem pergunta "por quê?". */
    public enum Origem {
        /** O modo sombra global está ligado: nada sai, de contrato nenhum. */
        SOMBRA_GLOBAL,
        /**
         * A chave global foi desligada e não há transporte.
         *
         * <p>É o único estado que a régua trata como <b>erro</b>, e não como
         * sombra: alguém declarou a intenção de enviar e o sistema não tem com
         * o quê. Ver a nota da ordem em {@link #decidir}.
         */
        SEM_TRANSPORTE,
        /** O contrato não aderiu. Sombra por omissão, e é o padrão. */
        SEM_ADESAO_DO_CONTRATO,
        /** O contrato aderiu explicitamente e a chave global está desligada. */
        ADESAO_DO_CONTRATO
    }

    /**
     * Decide, na ordem em que as duas chaves se sobrepõem.
     *
     * @param modoSombraGlobal a chave global; ausente conta como ligada
     * @param contratoAderiu   existe parâmetro de contrato dizendo {@code true}
     * @param existeTransporte se há transporte configurado — hoje, nunca (RA-06)
     */
    public static Ativacao decidir(boolean modoSombraGlobal, boolean contratoAderiu,
                                   boolean existeTransporte) {
        if (modoSombraGlobal) {
            return new Ativacao(false, Origem.SOMBRA_GLOBAL,
                    "o modo sombra global está ligado: nenhum contrato envia, e a régua "
                            + "grava o que enviaria (cap. 11.2)");
        }
        if (!existeTransporte) {
            // ESTE PORTÃO VEM ANTES DA ADESÃO, E A ORDEM É O PONTO.
            //
            // Desligar a chave global é uma DECLARAÇÃO DE INTENÇÃO DE ENVIAR.
            // Checar a adesão primeiro faria o caso "global desligada, nenhum
            // contrato aderido" cair em SEM_ADESAO — a régua gravaria em sombra
            // e ninguém diria nada, e quem desligou a chave passaria a esperar
            // e-mails que nunca sairiam. Silenciar uma intenção explícita é a
            // mesma confiança falsa que a F1-09 recusou; ela continua recusada,
            // e agora também no caminho por contrato.
            return new Ativacao(false, Origem.SEM_TRANSPORTE,
                    "a chave global foi desligada e não há transporte configurado (RA-06). "
                            + "Nenhum contrato pode enviar, e declarar-se ativo faria a "
                            + "área acreditar que está sendo cobrada");
        }
        if (!contratoAderiu) {
            return new Ativacao(false, Origem.SEM_ADESAO_DO_CONTRATO,
                    "este contrato não aderiu ao envio. A adesão é explícita e não se "
                            + "herda de cliente nem do global — um contrato sem parâmetro "
                            + "próprio fica em sombra");
        }
        return new Ativacao(true, Origem.ADESAO_DO_CONTRATO,
                "adesão do contrato, com a chave global desligada e transporte configurado");
    }

    /** A régua grava em vez de enviar — o modo sombra do cap. 11.2. */
    public boolean gravaSemEnviar() {
        return !envioAtivo && origem != Origem.SEM_TRANSPORTE;
    }

    /** Não dá para gravar nem enviar: alguém quis enviar e não há com o quê. */
    public boolean incoerente() {
        return origem == Origem.SEM_TRANSPORTE;
    }
}
