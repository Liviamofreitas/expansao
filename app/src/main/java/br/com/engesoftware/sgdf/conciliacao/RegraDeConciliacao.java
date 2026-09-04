package br.com.engesoftware.sgdf.conciliacao;

/**
 * Uma regra de conciliação do cap. 9.
 *
 * <p>A <b>lógica</b> é código; a <b>tolerância</b> e o <b>modo</b> são cadastro.
 * A separação é o que permite a área demandante afrouxar ou apertar uma regra
 * sem nova versão do sistema — e o que impede que alguém mude o que a regra
 * compara sem passar por revisão de código.
 */
public interface RegraDeConciliacao {

    /** R01..R12. */
    String codigo();

    /**
     * @param ciclo      o que o ciclo tem
     * @param tolerancia do cadastro; {@link Tolerancia#NENHUMA} força o modo ALERTA
     */
    ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia);

    /**
     * O modo que o cadastro define, corrigido pela regra do cap. 9.
     *
     * <p>Regra sem tolerância cadastrada opera em ALERTA e nunca bloqueia,
     * qualquer que seja o modo pedido. É o princípio 1 do cap. 1: o sistema não
     * trava faturamento por falta de configuração própria.
     */
    static ResultadoDaConciliacao.Modo modoEfetivo(ResultadoDaConciliacao.Modo cadastrado,
                                                   Tolerancia tolerancia) {
        return tolerancia.cadastrada() ? cadastrado : ResultadoDaConciliacao.Modo.ALERTA;
    }
}
