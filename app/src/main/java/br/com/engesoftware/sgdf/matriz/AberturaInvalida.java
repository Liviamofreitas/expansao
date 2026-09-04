package br.com.engesoftware.sgdf.matriz;

/** Entrada de abertura de ciclo malformada, com código estável. */
public final class AberturaInvalida extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String codigo;

    public AberturaInvalida(String codigo, String detalhe) {
        super(detalhe == null || detalhe.isBlank() ? codigo : codigo + ": " + detalhe);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
