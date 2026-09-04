package br.com.engesoftware.sgdf.book;

import java.time.OffsetDateTime;

/**
 * Um documento aprovado, candidato a entrar no book.
 *
 * <p>Ainda sem número de sequência: quem numera é o montador, depois de ordenar
 * por família — a numeração é do book, não do documento.
 *
 * @param tipo       tipo documental
 * @param familia    família a que pertence, que define a ordem no book
 * @param sigilo     classificação, que decide se precisa de tarjamento
 * @param conteudo   bytes do arquivo, íntegros
 * @param origem     de onde veio, para o manifesto
 * @param validadoEm quando as validações unitárias o aprovaram
 * @param tarjado    se estes bytes já passaram pelo redator
 */
public record DocumentoPublicavel(String tipo, String familia, Sigilo sigilo, byte[] conteudo,
                                  String origem, OffsetDateTime validadoEm, boolean tarjado) {

    public DocumentoPublicavel {
        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalArgumentException("documento '" + tipo + "' sem conteúdo");
        }
    }
}
