package br.com.engesoftware.sgdf.folha;

import br.com.engesoftware.sgdf.documento.Rubrica;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * O de-para carregado do cadastro — {@code rubrica_de_para}, cap. 7.2.
 *
 * <p><b>Código vence descrição.</b> A FOPAG imprime o código; o contracheque não.
 * Quando os dois estão disponíveis, o código decide: a descrição varia de grafia
 * entre competências e entre sistemas ("Vale Alimentação", "VALE ALIMENTACAO",
 * "V. ALIMENTACAO") e o código não. Casar por descrição é o plano B que a
 * ausência do export do cap. 14.3 impõe (achado A05), não o mecanismo preferido.
 */
public final class DeParaDeRubricas {

    private final Map<String, PapelDaRubrica> porCodigo;
    private final Map<String, PapelDaRubrica> porDescricao;

    public DeParaDeRubricas(Map<String, PapelDaRubrica> porCodigo,
                            Map<String, PapelDaRubrica> porDescricao) {
        this.porCodigo = Map.copyOf(porCodigo);
        Map<String, PapelDaRubrica> normalizado = new LinkedHashMap<>();
        porDescricao.forEach((d, p) -> normalizado.put(normalizar(d), p));
        this.porDescricao = Map.copyOf(normalizado);
    }

    public static DeParaDeRubricas vazio() {
        return new DeParaDeRubricas(Map.of(), Map.of());
    }

    /**
     * O papel de uma rubrica.
     *
     * <p>Devolve {@link PapelDaRubrica#OUTRA} para o que não está no cadastro —
     * e não nulo. Rubrica desconhecida é o caso NORMAL: a folha tem dezenas de
     * linhas e o de-para só precisa das que alguma regra usa. Tratar o
     * desconhecido como erro faria a chegada de uma rubrica nova travar a
     * competência inteira, contra o princípio 1 do cap. 1.
     */
    public PapelDaRubrica papelDe(Rubrica rubrica) {
        if (rubrica.codigo() != null) {
            PapelDaRubrica porCodigoDaRubrica = porCodigo.get(rubrica.codigo());
            if (porCodigoDaRubrica != null) {
                return porCodigoDaRubrica;
            }
        }
        return porDescricao.getOrDefault(normalizar(rubrica.descricao()), PapelDaRubrica.OUTRA);
    }

    /** Mesma normalização de {@code rubrica_de_para.descricao_normalizada}. */
    public static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{Alnum}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
