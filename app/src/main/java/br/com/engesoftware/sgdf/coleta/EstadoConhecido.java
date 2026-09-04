package br.com.engesoftware.sgdf.coleta;

/**
 * O que a varredura anterior já viu — base do delta do cap. 8.1.
 *
 * <p>Na aplicação, a implementação consulta {@code documento.versao_origem} por
 * {@code (origem, caminho)}. Aqui é uma porta para que a varredura seja testável
 * sem banco.
 */
public interface EstadoConhecido {

    /** Versão registrada para o caminho, ou {@code null} se nunca foi visto. */
    String versaoDe(String caminho);
}
