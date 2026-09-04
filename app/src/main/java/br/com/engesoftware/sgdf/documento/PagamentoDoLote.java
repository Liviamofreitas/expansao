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

    /** Largura da data ddMMyyyy no fim do identificador. */
    private static final int LARGURA_DA_DATA = 8;

    /** Largura da matrícula na folha — nove dígitos, com zeros à esquerda. */
    private static final int LARGURA_DA_MATRICULA = 9;

    /**
     * A matrícula embutida no identificador do pagamento, sem zeros à esquerda.
     *
     * <p><b>Achado A14, corrigido pela massa do segundo contrato.</b> O
     * identificador tem três partes:
     *
     * <pre>
     *   001 | 000101963 | 03072026
     *    ^        ^          ^
     *    |        |          data do pagamento (ddMMyyyy)
     *    |        matrícula na folha (9 dígitos)
     *    código da empresa
     * </pre>
     *
     * <p>A primeira versão tirava a data e removia os zeros à esquerda do que
     * sobrava. Funcionou nos comprovantes do Santander por acaso: ali o código
     * da empresa é {@code 000} e some junto com os zeros da matrícula. No
     * comprovante do Itaú o código é {@code 001} — o mesmo "001-Engesoftware
     * Tecnologia S/A" que encabeça a FOPAG — e o resultado era {@code
     * 1000101963}, uma matrícula que não existe. A conciliação por matrícula
     * acusaria ausência do colaborador com os dois documentos corretos.
     *
     * <p>A junção com a folha é por matrícula, nunca por nome — nome tem
     * homônimo, grafia variante e quebra de linha.
     */
    public String matricula() {
        if (numeroCliente == null
                || numeroCliente.length() < LARGURA_DA_DATA + LARGURA_DA_MATRICULA) {
            return null;
        }
        int fim = numeroCliente.length() - LARGURA_DA_DATA;
        String matricula = numeroCliente.substring(fim - LARGURA_DA_MATRICULA, fim);
        String semZeros = matricula.replaceFirst("^0+", "");
        return semZeros.isEmpty() ? null : semZeros;
    }

    /** O código da empresa que precede a matrícula, ou nulo quando não há sobra. */
    public String codigoDaEmpresa() {
        if (numeroCliente == null
                || numeroCliente.length() <= LARGURA_DA_DATA + LARGURA_DA_MATRICULA) {
            return null;
        }
        return numeroCliente.substring(0,
                numeroCliente.length() - LARGURA_DA_DATA - LARGURA_DA_MATRICULA);
    }

    /** A data embutida no identificador, no formato dd/MM/yyyy. */
    public String dataDoIdentificador() {
        if (numeroCliente == null || numeroCliente.length() < LARGURA_DA_DATA) {
            return null;
        }
        String d = numeroCliente.substring(numeroCliente.length() - LARGURA_DA_DATA);
        return d.substring(0, 2) + "/" + d.substring(2, 4) + "/" + d.substring(4);
    }
}
