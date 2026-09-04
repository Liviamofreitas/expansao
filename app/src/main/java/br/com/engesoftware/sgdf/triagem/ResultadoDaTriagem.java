package br.com.engesoftware.sgdf.triagem;

import java.util.UUID;

/**
 * O que a decisão produziu — a resposta que a tela mostra.
 *
 * <p>Cap. 12: <i>"Confirmar registra alias <b>e informa</b>"</i>. O campo
 * {@link #aliasAprendido()} é essa informação, e {@link #avisoSobreAlias()} é a
 * outra metade dela: por que nenhum alias foi gravado, quando não foi. Sem esse
 * aviso, quem confirma sai acreditando que ensinou o sistema e reencontra o
 * mesmo arquivo na fila no mês seguinte — o critério de aceite da F1-06 falhando
 * em silêncio.
 *
 * @param candidaturaId    a candidatura decidida
 * @param situacao         como ela ficou
 * @param statusExigencia  para onde a exigência foi (cap. 6.1)
 * @param aliasAprendido   o padrão gravado, ou nulo
 * @param avisoSobreAlias  por que não gravou, ou nulo quando gravou
 */
public record ResultadoDaTriagem(UUID candidaturaId, String situacao, String statusExigencia,
                                 String aliasAprendido, String avisoSobreAlias) {

    public boolean aprendeu() {
        return aliasAprendido != null;
    }
}
