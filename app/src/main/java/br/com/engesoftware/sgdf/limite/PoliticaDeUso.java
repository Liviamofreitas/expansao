package br.com.engesoftware.sgdf.limite;

import java.time.Duration;

/**
 * Os limites do SEC-06, e por que cada número é o que é.
 *
 * <p><b>O SGDF não autentica, e isso muda o que "lockout" significa.</b> Ele é
 * <i>resource server</i>: quem valida senha e aplica MFA é o IdP (cap. 14.4).
 * Bloquear login depois de N senhas erradas é lá, não aqui — e construí-lo aqui
 * daria a impressão de um controle que o SGDF não exerce.
 *
 * <p>O que é dele: barrar um ator <b>já autenticado</b> que insiste em pedir o
 * que não pode. A trilha registra {@code NEGADO} desde a F1-06 <i>"para permitir
 * ver tentativa repetida de acesso"</i> — e ninguém lê. Isto é a resposta ao
 * padrão que já está gravado.
 *
 * <p><b>Por ATOR, e não por IP.</b> Num sistema interno atrás de proxy
 * corporativo o IP é o mesmo para todo mundo: bloquear por IP tiraria o
 * escritório inteiro do ar por causa de uma pessoa. E aqui a identidade é forte
 * — vem assinada pelo IdP —, ao contrário de um formulário público onde o IP é
 * tudo o que se tem.
 */
public final class PoliticaDeUso {

    /**
     * Volume por ator, por minuto.
     *
     * <p>Folgado de propósito: um painel que atualiza sozinho e um operador
     * navegando fazem muitos pedidos legítimos, e um limite apertado
     * transformaria uso normal em incidente. O que este número barra é laço
     * automatizado, não pessoa.
     */
    public static final int PEDIDOS_POR_MINUTO = 120;

    /**
     * Escrita por ator, por minuto — mais apertado, e por motivo diferente.
     *
     * <p>Não existe fluxo humano que decida trinta triagens em um minuto. Um
     * número acima disto é script, e script de escrita contra esta API é ou
     * automação não autorizada ou defeito de front em laço.
     */
    public static final int ESCRITAS_POR_MINUTO = 30;

    /**
     * Negativas de autorização toleradas por ator antes do bloqueio.
     *
     * <p>Dez, e não três. Um 403 legítimo é comum: alguém abre o link de um
     * ciclo que não é do seu contrato, o front pede um endpoint que o papel não
     * concede. Bloquear na terceira faria o sistema punir o uso desatento com a
     * mesma severidade da sondagem — e o efeito prático seria a operação pedir
     * para desligar o controle.
     */
    public static final int NEGATIVAS_ANTES_DO_BLOQUEIO = 10;

    /** A janela em que as negativas contam. */
    public static final Duration JANELA_DE_NEGATIVAS = Duration.ofMinutes(15);

    /**
     * Quanto dura o bloqueio.
     *
     * <p><b>Tem fim, e é essencial que tenha.</b> Um bloqueio permanente vira
     * chamado de suporte, e chamado de suporte repetido vira um bypass que
     * alguém cria e ninguém remove. Quinze minutos custam pouco a quem errou e
     * quebram o ritmo de quem sonda.
     */
    public static final Duration DURACAO_DO_BLOQUEIO = Duration.ofMinutes(15);

    /**
     * O que o limite de volume NÃO garante, dito aqui.
     *
     * <p>A contagem de volume vive na memória de cada instância. Com N réplicas,
     * o limite efetivo é <b>N × {@link #PEDIDOS_POR_MINUTO}</b>. Compartilhá-la
     * custaria uma escrita por requisição, e para o que ela protege — laço
     * automatizado, não exfiltração fina — o custo não se paga.
     *
     * <p>O bloqueio por negativas é diferente e <b>vive no banco</b>: ele é
     * resposta a comportamento suspeito, e um bloqueio que zera no reinício da
     * réplica é um bloqueio que o atacante dispara esperando — ou trocando de
     * réplica.
     */
    public static final String LIMITACAO_CONHECIDA =
            "o limite de volume é por instância: com N réplicas, o teto efetivo é N × "
            + PEDIDOS_POR_MINUTO + " por minuto. O bloqueio por negativas é compartilhado.";

    private PoliticaDeUso() {}

    /** Escrita é POST/PUT/PATCH/DELETE — o resto é leitura. */
    public static boolean escrita(String metodoHttp) {
        if (metodoHttp == null) {
            return true;   // desconhecido conta como escrita: o lado seguro
        }
        return switch (metodoHttp.toUpperCase(java.util.Locale.ROOT)) {
            case "GET", "HEAD", "OPTIONS" -> false;
            default -> true;
        };
    }
}
