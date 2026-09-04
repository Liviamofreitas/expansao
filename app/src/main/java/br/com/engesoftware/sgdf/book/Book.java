package br.com.engesoftware.sgdf.book;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Um book publicado.
 *
 * @param contrato       identificador do contrato-serviço
 * @param competencia    AAAA-MM
 * @param versao         republicação incrementa; nenhuma versão é alterada
 * @param publicadoEm    quando
 * @param publicadoPor   quem — serviço ou curador
 * @param versaoMatriz   versão da matriz vigente no ciclo, para reprodutibilidade
 * @param hashDoConjunto o que o cliente confere
 * @param pecas          na ordem em que foram numeradas
 * @param caminhoBucket  prefixo onde o book foi gravado
 */
public record Book(String contrato, String competencia, int versao, OffsetDateTime publicadoEm,
                   String publicadoPor, String versaoMatriz, String hashDoConjunto,
                   List<Peca> pecas, String caminhoBucket) {

    public Book {
        if (versao < 1) {
            throw new IllegalArgumentException("a primeira versão é 1: " + versao);
        }
        if (pecas.isEmpty()) {
            throw new IllegalArgumentException(
                    "book sem peça nenhuma não é book: publicar um conjunto vazio daria "
                            + "ao cliente a impressão de que a competência foi conferida");
        }
        pecas = List.copyOf(pecas);
    }

    /** {@code books/{contrato}/{AAAA-MM}/v{N}/} — cap. 10. */
    public static String prefixo(String contrato, String competencia, int versao) {
        return "books/" + contrato + "/" + competencia + "/v" + versao + "/";
    }
}
