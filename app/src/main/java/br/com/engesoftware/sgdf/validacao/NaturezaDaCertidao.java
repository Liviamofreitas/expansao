package br.com.engesoftware.sgdf.validacao;

import java.util.Locale;

/**
 * A natureza de uma certidão, com os TRÊS valores que a massa real tem.
 *
 * <p><b>Achado A10.</b> O cap. 8.4 escreve V5 aceitando
 * {@code natureza ∈ {negativa, positiva c/ efeito de negativa}} — duas
 * categorias. A certidão cível e criminal real do TJDFT é
 * {@code CERTIDÃO POSITIVA DE DISTRIBUIÇÃO}, com uma execução de título
 * extrajudicial constando. Ela está no repositório e está sendo usada no
 * faturamento.
 *
 * <p>Sob V5 como escrita, essa certidão é reprovada e trava o faturamento de um
 * contrato regular. Aprovada em silêncio, esconde um fato que alguém deveria
 * ver. O terceiro valor existe para que nenhuma das duas coisas aconteça: o
 * cadastro decide, por tipo documental, se {@code POSITIVA} é aceitável, e o
 * resultado sai com ressalva registrada.
 *
 * <p>É o princípio 7 do cap. 1: o sistema confere completude e coerência
 * documental; julgar se uma execução judicial impede faturar é juízo jurídico.
 */
public enum NaturezaDaCertidao {

    NEGATIVA,
    POSITIVA_COM_EFEITO_NEGATIVA,
    POSITIVA;

    /**
     * Lê a natureza do título da certidão.
     *
     * <p>A ordem importa: "positiva com efeitos de negativa" contém "positiva",
     * e testar POSITIVA primeiro classificaria a certidão da Receita Federal —
     * que é regular — como o caso que precisa de decisão humana.
     */
    public static NaturezaDaCertidao ler(String texto) {
        if (texto == null) {
            return null;
        }
        String t = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        if (t.contains("positiva com efeito")) {
            return POSITIVA_COM_EFEITO_NEGATIVA;
        }
        if (t.contains("negativa")) {
            return NEGATIVA;
        }
        if (t.contains("positiva")) {
            return POSITIVA;
        }
        return null;
    }

    /** Regular sem ressalva — premissa R-01 do cap. 3. */
    public boolean regular() {
        return this != POSITIVA;
    }
}
