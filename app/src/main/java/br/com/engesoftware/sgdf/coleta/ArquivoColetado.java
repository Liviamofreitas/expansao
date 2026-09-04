package br.com.engesoftware.sgdf.coleta;

/**
 * Arquivo baixado, verificado e pronto para a fila de extração.
 *
 * @param caminho   caminho canonicalizado na origem
 * @param versao    ETag (ou mtime) que produziu esta coleta
 * @param sha256    hash do conteúdo — cap. 8.1: "hash decide se é conteúdo novo"
 * @param tamanho   bytes efetivamente recebidos
 * @param conteudo  bytes; a aplicação os envia ao bucket de trabalho e descarta
 */
public record ArquivoColetado(
        String caminho, String versao, String sha256, long tamanho, byte[] conteudo) {}
