package br.com.engesoftware.sgdf.extracao;

import java.util.regex.Pattern;

/**
 * Padrão de extração de um campo, vindo de {@code regra_reconhecimento.campos}.
 *
 * <p>O padrão é aplicado sobre o texto NORMALIZADO (sem acento, minúsculas),
 * então deve ser escrito nessa forma. É cadastro, não código — cap. 1,
 * princípio 2.
 *
 * @param nome   nome do campo
 * @param padrao expressão regular; o grupo {@code grupo} é o valor
 * @param grupo  grupo de captura que contém o valor (0 = casamento inteiro)
 */
public record PadraoDeCampo(String nome, Pattern padrao, int grupo) {

    public static PadraoDeCampo de(String nome, String expressao, int grupo) {
        return new PadraoDeCampo(nome, Pattern.compile(expressao), grupo);
    }

    public static PadraoDeCampo de(String nome, String expressao) {
        return de(nome, expressao, 0);
    }
}
