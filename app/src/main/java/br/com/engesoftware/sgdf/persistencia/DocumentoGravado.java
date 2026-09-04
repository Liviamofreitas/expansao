package br.com.engesoftware.sgdf.persistencia;

import java.util.UUID;

/**
 * O que o repositório devolve ao gravar um documento.
 *
 * @param id     identidade no banco
 * @param inedito falso quando o mesmo (origem, caminho, hash) já estava lá
 */
public record DocumentoGravado(UUID id, boolean inedito) {
}
