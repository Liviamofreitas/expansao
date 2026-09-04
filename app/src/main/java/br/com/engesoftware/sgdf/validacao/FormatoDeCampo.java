package br.com.engesoftware.sgdf.validacao;

import java.util.regex.Pattern;

/**
 * Formato que o valor de um campo precisa ter para ser aceito.
 *
 * <p>Separado do padrão de EXTRAÇÃO ({@code PadraoDeCampo}) de propósito: um
 * localiza o valor no documento, o outro decide se o que foi localizado é
 * plausível. Um recorte de coluna que invade a coluna vizinha devolve um valor
 * — só o formato revela que é lixo.
 *
 * <p>O casamento é sobre o valor INTEIRO, não sobre um trecho dele: aceitar
 * "casa em algum lugar" transformaria "06/07/2026 CC" numa data válida.
 *
 * @param nome      nome do campo, como aparece na regra de reconhecimento
 * @param expressao forma aceita
 * @param descricao o que o formato exige, em português, para a mensagem de recusa
 */
public record FormatoDeCampo(String nome, Pattern expressao, String descricao) {

    public static FormatoDeCampo de(String nome, String expressao, String descricao) {
        return new FormatoDeCampo(nome, Pattern.compile(expressao), descricao);
    }

    public boolean aceita(String valor) {
        return valor != null && expressao.matcher(valor.trim()).matches();
    }

    // Formatos recorrentes na massa real. São cadastro por natureza — ficam aqui
    // como ponto de partida verificável, não como a lista definitiva.

    public static final FormatoDeCampo CPF =
            de("cpf", "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}", "CPF no formato ###.###.###-##");

    public static final FormatoDeCampo CNPJ =
            de("cnpj", "\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}", "CNPJ no formato ##.###.###/####-##");

    public static final FormatoDeCampo DATA =
            de("data", "\\d{2}/\\d{2}/\\d{4}", "data no formato dd/mm/aaaa");

    public static final FormatoDeCampo COMPETENCIA =
            de("competencia", "\\d{2}/\\d{4}", "competência no formato mm/aaaa");

    /** Valor em reais na notação brasileira, com ou sem separador de milhar. */
    public static final FormatoDeCampo VALOR =
            de("valor", "\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2}", "valor decimal com centavos");

    public static final FormatoDeCampo NOME_DE_PESSOA =
            de("nome", "[A-ZÁÂÃÀÉÊÍÓÔÕÚÜÇ][A-ZÁÂÃÀÉÊÍÓÔÕÚÜÇ \'.]{4,}",
                    "nome em maiúsculas com ao menos cinco caracteres");

    /**
     * Natureza da certidão, na forma em que sai do texto normalizado.
     *
     * <p>V5 só aceita estas duas. "Positiva" pura não passa, e é por isso que a
     * forma completa "positiva com efeitos de negativa" precisa estar aqui: a
     * certidão real da Receita Federal desta empresa é dessa espécie, e uma
     * lista que só aceitasse "negativa" recusaria o documento válido.
     */
    public static final FormatoDeCampo NATUREZA =
            de("natureza", "negativa|positiva com efeitos de negativa",
                    "negativa ou positiva com efeitos de negativa");

    /**
     * Data escrita por extenso — {@code 27 de agosto de 2026}.
     *
     * <p>Achado A25: a certidão negativa do GDF não imprime a validade em
     * dd/mm/aaaa. Um formato que só aceitasse a forma numérica reprovaria uma
     * certidão válida.
     */
    public static final FormatoDeCampo DATA_POR_EXTENSO =
            de("data_por_extenso",
                    "\\d{1,2} de (janeiro|fevereiro|mar\u00e7o|abril|maio|junho|julho"
                            + "|agosto|setembro|outubro|novembro|dezembro) de \\d{4}",
                    "data por extenso, como \"27 de agosto de 2026\"");

    public static final FormatoDeCampo INTEIRO =
            de("inteiro", "\\d+", "número inteiro");

    /**
     * Qualquer texto não vazio. Só confere presença — usar quando não há forma.
     *
     * <p>Casa através de quebra de linha ({@code [\s\S]}, não {@code .}): um
     * nome que o documento imprime em duas linhas é um valor legítimo, e
     * recusá-lo por causa do {@code \n} seria reprovar documento bom.
     */
    public static final FormatoDeCampo TEXTO =
            de("texto", "\\S[\\s\\S]*", "texto não vazio");

    /**
     * Resolve pelo nome usado no cadastro ({@code campos_essenciais.formato}).
     *
     * <p>Um formato desconhecido é erro, nunca "aceita qualquer coisa": um
     * cadastro com erro de digitação passaria a aprovar tudo em silêncio, que é
     * o oposto do que V8 existe para fazer.
     */
    public static FormatoDeCampo porNome(String nome) {
        for (FormatoDeCampo f : CATALOGO) {
            if (f.nome().equals(nome)) {
                return f;
            }
        }
        throw new IllegalArgumentException("formato desconhecido no cadastro: '" + nome
                + "'. Conhecidos: " + nomesConhecidos());
    }

    public static java.util.List<String> nomesConhecidos() {
        return CATALOGO.stream().map(FormatoDeCampo::nome).toList();
    }

    private static final java.util.List<FormatoDeCampo> CATALOGO = java.util.List.of(
            CPF, CNPJ, DATA, DATA_POR_EXTENSO, COMPETENCIA, VALOR, NOME_DE_PESSOA,
            NATUREZA, INTEIRO, TEXTO);
}
