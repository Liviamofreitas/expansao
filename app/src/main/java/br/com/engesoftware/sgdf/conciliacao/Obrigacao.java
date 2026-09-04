package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que a empresa deve — uma guia, um DARF, uma fatura de benefício.
 *
 * @param tipo          tipo documental da obrigação (FGT.GUIA, INS.DARF...)
 * @param competencia   competência a que se refere, no formato MM/AAAA
 * @param valor         quanto é devido
 * @param vencimento    até quando
 * @param identificador código de barras, identificador da guia, nosso número
 */
public record Obrigacao(String tipo, String competencia, BigDecimal valor,
                        LocalDate vencimento, String identificador) {
}
