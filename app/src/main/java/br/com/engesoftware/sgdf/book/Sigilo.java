package br.com.engesoftware.sgdf.book;

/** Classificação de sigilo do tipo documental (cap. 5.1). */
public enum Sigilo {
    PUBLICO_CLIENTE,
    INTERNO,
    PESSOAL,
    PESSOAL_SENSIVEL;

    /**
     * Se o tipo precisa passar pelo redator antes de ir para o book do cliente.
     *
     * <p>Cap. 10: tipos com sigilo {@code PESSOAL} ou {@code PESSOAL_SENSIVEL}
     * passam pelo redator (máscara de CPF e dados não exigíveis) antes da cópia;
     * o original íntegro permanece no repositório interno.
     */
    public boolean exigeTarjamento() {
        return this == PESSOAL || this == PESSOAL_SENSIVEL;
    }
}
