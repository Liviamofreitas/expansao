package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;

/**
 * Uma linha do comprovante de pagamento em lote.
 *
 * @param numeroPagamento identificador do pagamento no banco
 * @param numeroCliente   identificador do favorecido no convênio — matrícula
 *                        seguida da data de pagamento (achado A14)
 * @param favorecido      nome do funcionário
 * @param data            data do pagamento, como impressa
 * @param tipo            tipo de crédito (CC, PP, TED...)
 * @param valor           valor pago
 */
public record PagamentoDoLote(String numeroPagamento, String numeroCliente, String favorecido,
                              String data, String tipo, BigDecimal valor) {

    /**
     * A matrícula embutida no número do cliente, sem os zeros à esquerda.
     *
     * <p>Achado A14: o número do cliente é a matrícula concatenada com a data
     * no formato ddMMyyyy. A junção com a folha é por matrícula, nunca por nome
     * — nome tem homônimo, grafia variante e quebra de linha.
     */
    public String matricula() {
        if (numeroCliente == null || numeroCliente.length() <= 8) {
            return null;
        }
        String semData = numeroCliente.substring(0, numeroCliente.length() - 8);
        String semZeros = semData.replaceFirst("^0+", "");
        return semZeros.isEmpty() ? null : semZeros;
    }
}
