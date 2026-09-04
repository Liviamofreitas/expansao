package br.com.engesoftware.sgdf.book;

import java.time.OffsetDateTime;

/**
 * Um documento dentro do book, já numerado.
 *
 * @param sequencia   posição no book, sequencial e sem lacunas (cap. 10)
 * @param tipo        tipo documental
 * @param sigilo      classificação do tipo — decide se precisa de tarjamento
 * @param arquivo     nome no bucket: {@code NN_TIPO_COMPETENCIA.pdf}
 * @param hashSha256  do conteúdo publicado
 * @param origem      de onde o documento veio, para auditoria
 * @param validadoEm  quando as validações unitárias o aprovaram
 * @param tarjado     se o conteúdo publicado passou pelo redator
 */
public record Peca(int sequencia, String tipo, Sigilo sigilo, String arquivo,
                   String hashSha256, String origem, OffsetDateTime validadoEm,
                   boolean tarjado) {

    public Peca {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequência começa em 1: " + sequencia);
        }
        if (hashSha256 == null || !hashSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "peça '" + tipo + "' sem SHA-256: o hash é o que permite ao cliente "
                            + "conferir que o arquivo que ele tem é o que foi publicado");
        }
    }
}
