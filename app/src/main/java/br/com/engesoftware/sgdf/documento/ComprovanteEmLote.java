package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprovante de pagamento em lote, com o rodapé que o próprio documento
 * declara.
 *
 * <p>O rodapé é o que torna esta extração verificável sem gabarito externo: o
 * banco imprime quantos compromissos pagou e por quanto. Se a leitura não
 * reproduzir os dois números, ela está errada — e é melhor dizer isso do que
 * entregar uma lista plausível e incompleta.
 *
 * @param pagamentos      linhas lidas
 * @param totalDeclarado  "Total Compromissos" impresso no documento, ou -1 se ausente
 * @param valorDeclarado  "Valor Total" impresso no documento, ou nulo se ausente
 * @param problemasDeLeitura o que impediu a leitura de uma página, quando impediu
 */
public record ComprovanteEmLote(List<PagamentoDoLote> pagamentos,
                                int totalDeclarado, BigDecimal valorDeclarado,
                                List<String> problemasDeLeitura) {

    public ComprovanteEmLote {
        pagamentos = List.copyOf(pagamentos);
        problemasDeLeitura = List.copyOf(problemasDeLeitura);
    }

    public BigDecimal somaLida() {
        return pagamentos.stream().map(PagamentoDoLote::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Vazio quando a leitura reproduz o rodapé; senão, o que não bateu.
     *
     * <p>A AUSÊNCIA do rodapé também é divergência. Um comprovante sem totais
     * não confere: ele não pôde ser conferido, e as duas coisas não são a mesma.
     * Tratá-las como iguais faria um documento vazio — o achado A12, em que o
     * Itaú imprime só os rótulos — passar como leitura bem-sucedida de zero
     * pagamentos.
     */
    public List<String> divergenciasComORodape() {
        List<String> erros = new java.util.ArrayList<>(problemasDeLeitura);
        if (totalDeclarado < 0 && valorDeclarado == null) {
            erros.add("o documento não declara total de compromissos nem valor total: "
                    + "a leitura de " + pagamentos.size() + " pagamento(s) não pôde ser verificada");
            return erros;
        }
        if (totalDeclarado >= 0 && totalDeclarado != pagamentos.size()) {
            erros.add("o documento declara " + totalDeclarado + " compromisso(s) e foram lidos "
                    + pagamentos.size());
        }
        if (valorDeclarado != null && valorDeclarado.compareTo(somaLida()) != 0) {
            erros.add("o documento declara o total de " + valorDeclarado
                    + " e a soma das linhas lidas é " + somaLida());
        }
        return erros;
    }

    public boolean confere() {
        return divergenciasComORodape().isEmpty();
    }
}
