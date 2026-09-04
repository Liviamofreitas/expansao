package br.com.engesoftware.sgdf.coleta;

import java.time.Instant;

/**
 * Uma entrada devolvida pelo PROPFIND: arquivo ou coleção (pasta).
 *
 * <p>{@code etag} é o que sustenta o delta do cap. 8.1. Servidores WebDAV podem
 * omiti-lo; quando isso acontece o campo vem nulo e o delta cai para
 * {@code modificadoEm}, que é menos preciso mas melhor que rebaixar tudo.
 *
 * @param caminho      caminho absoluto no servidor, já decodificado e canonicalizado
 * @param etag         validador de versão, sem aspas; nulo quando o servidor não informa
 * @param tamanho      bytes; -1 quando desconhecido
 * @param modificadoEm última modificação; nulo quando o servidor não informa
 * @param colecao      true para pasta
 */
public record EntradaRemota(
        String caminho,
        String etag,
        long tamanho,
        Instant modificadoEm,
        boolean colecao) {

    public String nome() {
        int corte = caminho.lastIndexOf('/');
        return corte < 0 ? caminho : caminho.substring(corte + 1);
    }

    /** Versão para o delta: ETag quando houver, senão a data de modificação. */
    public String versao() {
        if (etag != null && !etag.isBlank()) {
            return etag;
        }
        return modificadoEm == null ? null : "mtime:" + modificadoEm.toEpochMilli();
    }
}
