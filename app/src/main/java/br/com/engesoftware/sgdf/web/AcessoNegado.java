package br.com.engesoftware.sgdf.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * O 403 do critério de aceite da F0-01.
 *
 * <p>Carrega o motivo que o {@code Autorizador} produziu. O motivo vai para o
 * log e para a trilha — <b>não para o corpo da resposta</b>: dizer ao cliente
 * "o contrato X está fora do seu escopo" confirma que o contrato X existe, e
 * quem não pode vê-lo também não deveria saber disso.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AcessoNegado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AcessoNegado(String motivo) {
        super(motivo);
    }
}
