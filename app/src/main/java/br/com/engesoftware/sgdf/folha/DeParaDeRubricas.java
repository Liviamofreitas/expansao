package br.com.engesoftware.sgdf.folha;

import br.com.engesoftware.sgdf.documento.Rubrica;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * O caminho inverso: dado o papel, qual código o representa.
     *
     * <p>Existe para a pendência RA-10. O {@code LeitorDeFopag} precisa achar o
     * total de proventos dentro da coluna RESULTADOS, que vem indexada por
     * código — e até aqui ele o fazia por <b>constante Java</b>, embora os
     * mesmos nove códigos já vivessem em {@code rubrica_de_para}. Duas verdades
     * para a mesma regra: mudar o plano de contas no cadastro deixava o leitor
     * lendo o código antigo, e ninguém notaria, porque o leitor simplesmente
     * não acharia o total e devolveria nulo.
     *
     * <p><b>Ambíguo levanta, não escolhe.</b> Se dois códigos declaram o mesmo
     * papel, "qual é o total de proventos?" tem duas respostas e a iteração do
     * mapa daria uma delas — o resultado bonito e falso. É o mesmo tratamento
     * que o {@code Resolvedor} dá a duas regras para o mesmo par.
     *
     * @return o código, ou nulo quando o cadastro não declara esse papel
     */
    public String codigoUnicoDe(PapelDaRubrica papel) {
        List<String> codigos = new ArrayList<>();
        porCodigo.forEach((codigo, p) -> {
            if (p == papel) {
                codigos.add(codigo);
            }
        });
        if (codigos.size() > 1) {
            codigos.sort(null);
            throw new RubricaAmbigua(papel, codigos);
        }
        return codigos.isEmpty() ? null : codigos.get(0);
    }

    /**
     * Exige que o cadastro declare cada um destes papéis, por código.
     *
     * <p><b>Recusar é a metade que importa.</b> Sem isto, um de-para a que
     * faltasse {@code BASE_FGTS} produziria uma folha com a base do FGTS nula em
     * todos os colaboradores — e a conciliação do cap. 9 compararia a guia
     * contra nada, passando. Uma ausência que se parece com conformidade é o
     * defeito que esta base já encontrou seis vezes; aqui ela é impossível de
     * produzir em silêncio.
     */
    public Map<PapelDaRubrica, String> exigir(PapelDaRubrica... papeis) {
        Map<PapelDaRubrica, String> encontrados = new EnumMap<>(PapelDaRubrica.class);
        List<String> faltando = new ArrayList<>();
        for (PapelDaRubrica papel : papeis) {
            String codigo = codigoUnicoDe(papel);
            if (codigo == null) {
                faltando.add(papel.name());
            } else {
                encontrados.put(papel, codigo);
            }
        }
        if (!faltando.isEmpty()) {
            throw new DeParaIncompleto(faltando);
        }
        return encontrados;
    }

    /** Dois códigos para o mesmo papel: a pergunta passa a ter duas respostas. */
    public static final class RubricaAmbigua extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient PapelDaRubrica papel;
        private final transient List<String> codigos;

        RubricaAmbigua(PapelDaRubrica papel, List<String> codigos) {
            super("o papel " + papel + " é declarado por " + codigos.size()
                    + " códigos no cadastro (" + String.join(", ", codigos)
                    + "): escolher um deles em silêncio poria um valor que ninguém "
                    + "decidiu dentro de uma conciliação");
            this.papel = papel;
            this.codigos = List.copyOf(codigos);
        }

        public PapelDaRubrica papel() {
            return papel;
        }

        public List<String> codigos() {
            return codigos;
        }
    }

    /** O cadastro não tem o que a leitura precisa. Ler assim mesmo daria nulos. */
    public static final class DeParaIncompleto extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient List<String> faltando;

        DeParaIncompleto(List<String> faltando) {
            super("o de-para de rubricas não declara, por código, o(s) papel(éis): "
                    + String.join(", ", faltando)
                    + ". Ler a folha assim produziria esses valores nulos em TODOS os "
                    + "colaboradores, e a conciliação compararia contra nada");
            this.faltando = List.copyOf(faltando);
        }

        public List<String> faltando() {
            return faltando;
        }
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
