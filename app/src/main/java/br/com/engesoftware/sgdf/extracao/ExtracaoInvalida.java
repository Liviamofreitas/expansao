package br.com.engesoftware.sgdf.extracao;

import java.io.Serial;

/** Documento que não pode ser extraído, com código estável para a tela e a trilha. */
public class ExtracaoInvalida extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public final String codigo;

    public ExtracaoInvalida(String codigo, String detalhe) {
        super(codigo + ": " + detalhe);
        this.codigo = codigo;
    }
}
