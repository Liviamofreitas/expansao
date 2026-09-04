package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Relação de beneficiários de um benefício numa competência.
 *
 * <p>Serve às relações de VA/VR, plano de saúde e vale-transporte: todas são a
 * mesma forma — uma linha por colaborador, com matrícula e valor, e um total
 * declarado no próprio documento.
 *
 * @param beneficiarios  linhas lidas
 * @param totalDeclarado total impresso pelo emissor, ou nulo quando ausente
 */
public record RelacaoDeBeneficio(List<BeneficiarioDaRelacao> beneficiarios,
                                 BigDecimal totalDeclarado) {

    public RelacaoDeBeneficio {
        beneficiarios = List.copyOf(beneficiarios);
    }

    public BigDecimal somaLida() {
        return beneficiarios.stream().map(BeneficiarioDaRelacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * O que não fecha dentro da própria relação.
     *
     * <p>A ausência do total também é divergência: uma relação sem total não
     * confere — ela não pôde ser conferida, e as duas coisas não são a mesma.
     * É a mesma regra do comprovante em lote, e pelo mesmo motivo (achado A12).
     */
    public List<String> divergenciasComOTotal() {
        List<String> erros = new ArrayList<>();
        if (totalDeclarado == null) {
            erros.add("a relação não declara total: a leitura de " + beneficiarios.size()
                    + " beneficiário(s) não pôde ser verificada");
        } else if (totalDeclarado.compareTo(somaLida()) != 0) {
            erros.add("o documento declara " + totalDeclarado + " e a soma das linhas lidas é "
                    + somaLida());
        }
        return erros;
    }

    public boolean confere() {
        return divergenciasComOTotal().isEmpty();
    }
}
