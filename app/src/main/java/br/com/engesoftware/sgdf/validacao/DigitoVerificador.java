package br.com.engesoftware.sgdf.validacao;

/**
 * Dígito verificador de CPF e CNPJ.
 *
 * <p><b>Por que é obrigatório.</b> O padrão {@code \d{3}\.\d{3}\.\d{3}-\d{2}}
 * sozinho é insuficiente: código de barras e número de autenticação produzem
 * sequências com o mesmo formato quando o texto é lido corrido. Na massa real
 * todos os 162 casamentos tinham DV válido, mas isso é sorte da amostra, não
 * garantia.
 *
 * <p>Um falso positivo de CPF num documento classificado como sem dado pessoal
 * é uma falha de privacidade silenciosa: o documento entra no book do cliente
 * sem tarjamento porque ninguém sabia que havia um CPF ali — ou, pior, é
 * tarjado num lugar onde não havia CPF nenhum e o número real passa.
 */
public final class DigitoVerificador {

    private DigitoVerificador() {}

    public static boolean cpfValido(String cpf) {
        String d = somenteDigitos(cpf);
        if (d.length() != 11 || todosIguais(d)) {
            return false;
        }
        return d.charAt(9) == digito(d, 9, 10) && d.charAt(10) == digito(d, 10, 11);
    }

    public static boolean cnpjValido(String cnpj) {
        String d = somenteDigitos(cnpj);
        if (d.length() != 14 || todosIguais(d)) {
            return false;
        }
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return d.charAt(12) == digitoComPesos(d, p1) && d.charAt(13) == digitoComPesos(d, p2);
    }

    /**
     * Peso decrescente a partir de {@code peso}, sobre os {@code ate} primeiros
     * dígitos — a regra do CPF.
     */
    private static char digito(String d, int ate, int peso) {
        int soma = 0;
        for (int i = 0; i < ate; i++) {
            soma += (d.charAt(i) - '0') * (peso - i);
        }
        int resto = soma % 11;
        return (char) ('0' + (resto < 2 ? 0 : 11 - resto));
    }

    private static char digitoComPesos(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return (char) ('0' + (resto < 2 ? 0 : 11 - resto));
    }

    /**
     * Sequências de dígito repetido têm DV formalmente correto — 111.111.111-11
     * passa na conta. São recusadas porque nenhuma é um CPF real.
     */
    private static boolean todosIguais(String d) {
        return d.chars().distinct().count() == 1;
    }

    public static String somenteDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
