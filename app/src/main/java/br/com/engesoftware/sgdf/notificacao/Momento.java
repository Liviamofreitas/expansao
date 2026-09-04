package br.com.engesoftware.sgdf.notificacao;

/**
 * Os momentos da régua do cap. 11.1.
 *
 * <p>O deslocamento é <b>exato</b>, não "a partir de": a régua roda uma vez por
 * dia e cada pendência dispara uma vez em cada marco. Fosse "a partir de", uma
 * pendência vencida há dez dias produziria dez cobranças por dia acumuladas, e a
 * consolidação do cap. 11.2 existiria para conter um problema que a própria
 * régua criou.
 *
 * <p><b>O que isso deixa em aberto.</b> Depois do D+5 a régua fica em silêncio:
 * o capítulo não define um marco seguinte. Uma pendência que sobrevive ao
 * escalonamento para a DAF deixa de gerar aviso — continua visível no painel,
 * mas ninguém é lembrado. Está registrado em PENDENCIAS.md; inventar aqui um
 * "repete a cada N dias" seria criar régua que ninguém aprovou.
 */
public enum Momento {

    /** D-2: o que vence em 48 h. Vai para o titular. */
    PREVENTIVA(2, 1, "PREVENTIVA"),

    /** D+0: venceu hoje. Titular e substituto. */
    COBRANCA(0, 3, "COBRANCA"),

    /** D+2: escalonamento nível 1 — gestor da área, cópia ao titular. */
    ESCALONAMENTO_N1(-2, 4, "ESCALONAMENTO"),

    /** D+5: escalonamento nível 2 — DAF, cópia ao gestor. */
    ESCALONAMENTO_N2(-5, 5, "ESCALONAMENTO"),

    /** Entrega detectada: fecha a pendência com registro. */
    RESOLVIDA(null, 0, "RESOLVIDA"),

    /** Ciclo PRONTO: fechamento com desempenho e o rodapé de não-substituição. */
    FECHAMENTO(null, 2, "FECHAMENTO");

    private final Integer diasAteOPrazo;
    private final int gravidade;
    private final String codigoNoBanco;

    Momento(Integer diasAteOPrazo, int gravidade, String codigoNoBanco) {
        this.diasAteOPrazo = diasAteOPrazo;
        this.gravidade = gravidade;
        this.codigoNoBanco = codigoNoBanco;
    }

    /**
     * Quantos dias faltam para o prazo quando este momento dispara.
     *
     * <p>Positivo antes do prazo, negativo depois. Nulo nos momentos que não
     * são disparados por prazo.
     */
    public Integer diasAteOPrazo() {
        return diasAteOPrazo;
    }

    /**
     * O tipo que a tabela {@code notificacao} aceita.
     *
     * <p>Os dois níveis de escalonamento gravam o mesmo código: para o banco é
     * um escalonamento, e o nível está no conteúdo. Achatar aqui, e não no
     * domínio, mantém a régua fiel ao capítulo sem inventar um valor que o
     * esquema não conhece.
     */
    public String codigoNoBanco() {
        return codigoNoBanco;
    }

    /** O mais grave define o tipo do aviso consolidado do dia. */
    public boolean maisGraveQue(Momento outro) {
        return gravidade > outro.gravidade;
    }
}
