package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que a empresa pagou, como o banco atesta.
 *
 * <p>A <b>família</b> é o que a classificação por conteúdo consegue determinar
 * (cap. 8.5: <i>"sem âncora fixa — identificado pelo pareamento"</i>). Qual
 * obrigação este comprovante paga é o que o pareamento decide — e é por isso
 * que o tipo documental não vem preenchido daqui.
 *
 * @param familia       CMP.DARF, CMP.TRANSFERENCIA, CMP.BOLETO, CMP.LOTE_SALARIOS
 * @param valor         quanto foi pago
 * @param data          quando
 * @param identificador linha digitável, nosso número, ID da transação — quando há
 * @param favorecido    a quem, como impresso
 */
public record ComprovanteDePagamento(String familia, BigDecimal valor, LocalDate data,
                                     String identificador, String favorecido) {
}
