package br.com.engesoftware.sgdf.matriz;

import java.time.LocalDate;

/**
 * Um profissional alocado no contrato.
 *
 * @param fim nulo quando a alocação segue aberta
 */
public record Alocacao(String matricula, LocalDate inicio, LocalDate fim) {

    /**
     * Ativo na competência — <b>interseção</b>, não cobertura integral.
     *
     * <p>O cap. 7.1 diz "profissional alocado ativo na competência" sem definir o
     * critério. Basta um dia de sobreposição: quem foi desligado no dia 3
     * trabalhou 3 dias, tem contracheque e encargos, e não exigir esses
     * documentos deixaria passar exatamente o caso que a responsabilidade
     * subsidiária alcança. Ver ERRATA E-10.
     */
    public boolean ativoEm(LocalDate inicio, LocalDate fim) {
        return !this.inicio.isAfter(fim) && (this.fim == null || !this.fim.isBefore(inicio));
    }
}
