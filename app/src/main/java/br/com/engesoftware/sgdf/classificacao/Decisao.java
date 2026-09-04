package br.com.engesoftware.sgdf.classificacao;

/** O que fazer com o resultado da classificação (cap. 8.3). */
public enum Decisao {
    /** Score ≥ limiar_auto e margem suficiente sobre o segundo: vínculo automático. */
    AUTOMATICA,

    /** Entre os limiares, ou empatado com outro tipo: fila de triagem. */
    TRIAGEM,

    /**
     * Abaixo do limiar de triagem: arquivo desconhecido.
     *
     * <p>Não conta como entrega e não reprova nada — aparece no painel de
     * organização. Cap. 8.3.
     */
    NAO_RECONHECIDO
}
