package br.com.engesoftware.sgdf.matriz;

/**
 * Cadastro de prazo malformado ou insatisfazível.
 *
 * <p>Carrega um <b>código estável</b>, não só uma mensagem: quem chama precisa
 * distinguir {@code ANCORA_SEM_EVENTO} — que não é erro, é ausência de gatilho
 * (cap. 7.3) — de {@code ANCORA_DESCONHECIDA}, que é cadastro errado. Casar por
 * texto de mensagem quebraria na primeira revisão de redação.
 */
public final class PrazoInvalido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String codigo;

    public PrazoInvalido(String codigo, String detalhe) {
        super(detalhe == null || detalhe.isBlank() ? codigo : codigo + ": " + detalhe);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
