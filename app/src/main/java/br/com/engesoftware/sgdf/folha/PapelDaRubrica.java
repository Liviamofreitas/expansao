package br.com.engesoftware.sgdf.folha;

/**
 * O que uma rubrica significa — {@code rubrica_de_para.papel}, cap. 7.2.
 *
 * <p>É este valor que a derivação de eventos lê, <b>nunca o código</b>. O código
 * é do sistema de folha e muda quando o RH muda o plano de contas; o papel é do
 * negócio e só muda quando o negócio muda.
 */
public enum PapelDaRubrica {

    TOTAL_PROVENTOS, TOTAL_DESCONTOS, LIQUIDO,
    BASE_INSS, BASE_IRRF, BASE_FGTS, FGTS_MES,
    INSS, IRRF,
    VALE_TRANSPORTE, VALE_ALIMENTACAO, PLANO_DE_SAUDE,

    /** Gatilhos de evento do cap. 7.2. */
    ADIANTAMENTO_13, FERIAS, RESCISAO,

    /** Mapeada, mas sem significado para nenhuma regra. */
    OUTRA;

    public static PapelDaRubrica de(String nome) {
        try {
            return valueOf(nome);
        } catch (IllegalArgumentException e) {
            return OUTRA;
        }
    }
}
