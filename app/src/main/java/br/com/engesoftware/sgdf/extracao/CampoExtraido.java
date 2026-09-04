package br.com.engesoftware.sgdf.extracao;

/**
 * Valor lido de um documento, com a região de onde veio.
 *
 * <p>Corresponde à tabela {@code campo_extraido}. A {@code posicao} é o que
 * torna a extração auditável: o cap. 16 exige que toda decisão automática seja
 * reproduzível, e um valor sem origem obriga o auditor a reler o documento
 * inteiro para conferir um número.
 *
 * @param campo     nome do campo na regra de reconhecimento
 * @param valor     texto extraído, já recortado do grupo do padrão
 * @param confianca 1,0 para texto nativo; menor para OCR
 * @param posicao   onde o valor foi lido
 */
public record CampoExtraido(String campo, String valor, double confianca,
                            RegiaoNoDocumento posicao) {

    public CampoExtraido {
        if (posicao == null) {
            throw new IllegalArgumentException(
                    "campo '" + campo + "' sem posição não satisfaz a história F1-02");
        }
    }
}
