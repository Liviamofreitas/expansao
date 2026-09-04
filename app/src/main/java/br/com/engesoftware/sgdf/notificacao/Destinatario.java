package br.com.engesoftware.sgdf.notificacao;

/**
 * Quem recebe — resolvido por (contrato × família), cap. 11.2.
 *
 * <p>O e-mail é a identidade do aviso consolidado: duas linhas de cadastro que
 * apontem para a mesma caixa são a mesma pessoa recebendo, e consolidar por nome
 * deixaria "J. Silva" e "João Silva" receberem dois e-mails no mesmo dia.
 */
public record Destinatario(String nome, String email, PapelNoAviso papel, String familia) {

    public Destinatario {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("destinatário sem e-mail não é destinatário");
        }
        email = email.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
