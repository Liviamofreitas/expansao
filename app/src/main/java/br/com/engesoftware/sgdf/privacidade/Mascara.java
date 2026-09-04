package br.com.engesoftware.sgdf.privacidade;

import br.com.engesoftware.sgdf.validacao.Cnpj;
import br.com.engesoftware.sgdf.validacao.DigitoVerificador;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Máscara de dado pessoal para tela, log e notificação — história F2-06.
 *
 * <p>Critério de aceite: <i>"CPF nunca aparece em claro em UI, log ou
 * notificação"</i>. Esta classe é o único lugar onde a decisão de quanto
 * esconder é tomada, para que ela seja auditável num arquivo e não espalhada
 * por vinte pontos de formatação.
 *
 * <p><b>O que a máscara preserva, e por quê.</b> Esconde os três primeiros
 * dígitos e os dois verificadores, mantendo os seis do meio:
 * {@code ***.190.471-**}. Quem opera precisa distinguir dois colaboradores numa
 * lista de pendências; esconder tudo tornaria a tela inútil e levaria alguém a
 * consultar o CPF em claro em outro lugar — que é como o controle vira teatro.
 * É a mesma forma que os documentos bancários reais já usam (achado A7).
 */
public final class Mascara {

    private static final Pattern CPF = Pattern.compile(
            "(\\d{3})\\.(\\d{3})\\.(\\d{3})-(\\d{2})");

    /** CPF sem pontuação, como aparece em arquivo de integração e em log. */
    private static final Pattern CPF_CRU = Pattern.compile("(?<!\\d)(\\d{11})(?!\\d)");

    private Mascara() {}

    /** {@code 052.190.471-40} vira {@code ***.190.471-**}. */
    public static String cpf(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            return valor;
        }
        return "***." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-**";
    }

    /**
     * Mascara todo CPF que aparecer no texto.
     *
     * <p>Só mascara o que <b>tem DV válido</b>. Um número de protocolo com onze
     * dígitos não é CPF, e mascará-lo esconderia informação que a mensagem
     * precisa ter — o oposto do que se quer. É a exigência da seção 6 dos
     * achados, aqui virada em código.
     */
    public static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String comPontuacao = substituir(valor, CPF, m -> DigitoVerificador.cpfValido(m.group())
                ? cpf(m.group()) : m.group());
        return substituir(comPontuacao, CPF_CRU, m -> DigitoVerificador.cpfValido(m.group())
                ? cpf(m.group()) : m.group());
    }

    /**
     * CNPJ mascarado na forma que os bancos usam: {@code **.681.946/0001-**}.
     *
     * <p>CNPJ de pessoa jurídica não é dado pessoal, e por isso não é mascarado
     * por padrão. Existe aqui para quando a exigência vier de contrato — e
     * porque o achado A7 mostrou que os comprovantes reais já chegam assim.
     */
    public static String cnpj(String valor) {
        String d = Cnpj.normalizar(valor);
        if (d.length() != 14) {
            return valor;
        }
        return "**." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                + d.substring(8, 12) + "-**";
    }

    /**
     * Nome reduzido ao primeiro nome e à inicial do último.
     *
     * <p>Nome completo é dado pessoal (LGPD art. 5º, I). Numa fila de triagem
     * ou numa notificação, "HERMES L." identifica para quem precisa agir sem
     * publicar o registro inteiro.
     */
    public static String nome(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }
        String[] partes = valor.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        String ultimo = partes[partes.length - 1];
        return partes[0] + " " + ultimo.charAt(0) + ".";
    }

    private static String substituir(String texto, Pattern padrao,
                                     java.util.function.Function<Matcher, String> troca) {
        Matcher m = padrao.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(troca.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
