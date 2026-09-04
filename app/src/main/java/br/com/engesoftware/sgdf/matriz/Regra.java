package br.com.engesoftware.sgdf.matriz;

import java.time.LocalDate;

/**
 * Uma regra de exigibilidade vigente — {@code regra_exigibilidade}, cap. 7.1.
 *
 * @param alvo        MODALIDADE ou CONTRATO; contrato sobrepõe modalidade
 * @param alvoId      o código da modalidade ou o número do contrato
 * @param criticidade nula herda a do tipo documental; preenchida sobrepõe
 */
public record Regra(String tipo, String evento, String alvo, String alvoId,
                    String obrigatoriedade, String criticidade, Prazo.Cadastrado prazo,
                    String responsavel, LocalDate vigenciaIni, LocalDate vigenciaFim) {

    /**
     * Vigente na competência.
     *
     * <p>A vigência é comparada contra o MÊS inteiro, não contra o dia 1: uma
     * regra que passa a valer no meio do mês vale para aquela competência.
     * Comparar com o dia 1 dispensaria silenciosamente a exigência do mês em que
     * a regra entrou em vigor — justamente o mês em que alguém decidiu que ela
     * passaria a valer.
     */
    public boolean vigenteEm(LocalDate inicio, LocalDate fim) {
        return !vigenciaIni.isAfter(fim) && (vigenciaFim == null || !vigenciaFim.isBefore(inicio));
    }

    /** A chave do desempate: tipo × evento, não tipo. */
    String chaveDeConflito() {
        return tipo + "|" + (evento == null ? "*" : evento);
    }
}
