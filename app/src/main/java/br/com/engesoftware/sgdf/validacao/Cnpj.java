package br.com.engesoftware.sgdf.validacao;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CNPJ como ele aparece nos documentos reais — não como se gostaria que
 * aparecesse.
 *
 * <p>A massa de 06 e 07/2026 trouxe as três formas que quebram a comparação por
 * igualdade de string:
 *
 * <table>
 *   <caption>Formas encontradas</caption>
 *   <tr><th>Documento</th><th>Como aparece</th><th>Achado</th></tr>
 *   <tr><td>Guia do FGTS Digital</td><td>{@code 00.681.946}</td><td>A6 — truncado na raiz</td></tr>
 *   <tr><td>Comprovante SICOOB</td><td>{@code **.681.946/0001-**}</td><td>A7 — mascarado</td></tr>
 *   <tr><td>Rodapé da Flash</td><td>{@code 32.223.020. 0001-18}</td><td>A16 — separador quebrado</td></tr>
 * </table>
 *
 * <p>Comparar por igualdade reprovaria os três — e são documentos legítimos da
 * empresa certa. É o falso positivo do risco P01: o sistema bloqueia
 * faturamento por defeito próprio.
 */
public final class Cnpj {

    /** Caractere que o emissor usa para esconder um dígito. */
    private static final String CORINGAS = "*xX#·•";

    /** Só o que interessa: dígitos e coringas, na ordem em que aparecem. */
    private static final Pattern RUIDO = Pattern.compile("[^0-9" + Pattern.quote(CORINGAS) + "]");

    /**
     * CNPJ em qualquer das formas reais, inclusive com espaço no meio.
     *
     * <p>Duas tolerâncias vêm do achado A16, e nenhuma é zelo. O rodapé da
     * Flash imprime {@code 32.223.020. 0001-18}: onde deveria haver uma BARRA
     * há um terceiro PONTO, e depois dele um espaço. Sem aceitar as duas
     * coisas, o padrão não acha nada nesse documento.
     */
    public static final Pattern PADRAO = Pattern.compile(
            "([0-9" + Pattern.quote(CORINGAS) + "]{2})\\.\\s*"
                    + "([0-9" + Pattern.quote(CORINGAS) + "]{3})\\.\\s*"
                    + "([0-9" + Pattern.quote(CORINGAS) + "]{3})"
                    + "(?:[/.]\\s*([0-9" + Pattern.quote(CORINGAS) + "]{4})"
                    + "-\\s*([0-9" + Pattern.quote(CORINGAS) + "]{2}))?");

    private Cnpj() {}

    /**
     * Normaliza para dígitos e coringas, sem separadores.
     *
     * <p>Devolve 8 caracteres quando o documento traz só a raiz, 14 quando traz
     * o CNPJ inteiro. Comprimento é informação: a raiz sozinha identifica a
     * empresa, não o estabelecimento.
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return RUIDO.matcher(texto).replaceAll("");
    }

    /** A raiz — os oito primeiros dígitos, que identificam a empresa. */
    public static String raiz(String cnpj) {
        String n = normalizar(cnpj);
        return n.length() >= 8 ? n.substring(0, 8) : n;
    }

    /**
     * Se o CNPJ lido no documento pode ser o da empresa esperada.
     *
     * <p>Compara <b>posição a posição</b>, e um coringa casa com qualquer
     * dígito. Quando o documento traz só a raiz, compara só a raiz — e diz
     * verdadeiro, porque a raiz é o que o documento afirma: exigir mais seria
     * inventar informação que ele não dá.
     *
     * <p><b>O que isto NÃO faz:</b> não decide que o documento é do
     * estabelecimento certo quando o documento não diz qual é. Uma guia que
     * traz só {@code 00.681.946} prova a empresa, não a filial. Se a exigência
     * for por estabelecimento, o cadastro precisa marcar o tipo como tal e a
     * V3 exigir o CNPJ completo — decisão que fica no cadastro, não aqui.
     */
    public static boolean podeSer(String lidoNoDocumento, String esperado) {
        String lido = normalizar(lidoNoDocumento);
        String alvo = normalizar(esperado);
        if (lido.isEmpty() || alvo.length() < 8) {
            return false;
        }
        if (lido.length() != 8 && lido.length() != 14) {
            return false;
        }
        int n = Math.min(lido.length(), alvo.length());
        for (int i = 0; i < n; i++) {
            char c = lido.charAt(i);
            if (CORINGAS.indexOf(c) >= 0) {
                continue;   // o emissor escondeu o dígito: não contradiz nada
            }
            if (c != alvo.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Verdadeiro quando o documento traz o CNPJ inteiro, e não só a raiz. */
    public static boolean completo(String cnpj) {
        return normalizar(cnpj).length() == 14;
    }

    /**
     * Verdadeiro quando o valor tem dígito escondido — e por isso o DV não pode
     * ser conferido.
     */
    public static boolean mascarado(String cnpj) {
        String n = normalizar(cnpj);
        for (int i = 0; i < n.length(); i++) {
            if (CORINGAS.indexOf(n.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Todos os CNPJs do texto, em qualquer das formas reais. */
    public static java.util.List<String> todosNo(String texto) {
        java.util.List<String> achados = new java.util.ArrayList<>();
        Matcher m = PADRAO.matcher(texto == null ? "" : texto);
        while (m.find()) {
            achados.add(normalizar(m.group()));
        }
        return achados;
    }
}
