package br.com.engesoftware.sgdf.validacao;

/** Resultado possível de uma validação unitária do cap. 8.4. */
public enum Veredito {
    APROVADO,
    REPROVADO,

    /**
     * A validação não se aplica a este documento.
     *
     * <p>É um resultado, não a ausência de um. O cap. 1, princípio 1, proíbe
     * travar faturamento por falta de configuração própria: uma validação sem
     * cadastro não reprova. Mas ela também não pode desaparecer — gravar
     * {@code NAO_APLICAVEL} com motivo é o que permite descobrir que um tipo
     * documental está passando sem ser conferido.
     */
    NAO_APLICAVEL
}
