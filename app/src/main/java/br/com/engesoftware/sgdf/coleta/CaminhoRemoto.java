package br.com.engesoftware.sgdf.coleta;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Normalização e contenção de caminhos vindos do servidor — premissa R-03.
 *
 * <p>O href de uma resposta PROPFIND é entrada não confiável: o servidor pode
 * devolver caminho com {@code ..}, com codificação dupla, ou apontando para
 * fora da raiz declarada no contrato. Cap. 14.1: "fora dela, nada é lido".
 */
public final class CaminhoRemoto {

    private CaminhoRemoto() {}

    /**
     * Decodifica e resolve o href, colapsando {@code .} e {@code ..}.
     *
     * <p>A decodificação é feita UMA vez. Decodificar em laço "até estabilizar"
     * é o que abre a porta para codificação dupla — {@code %252e%252e} viraria
     * {@code ..} num segundo passe.
     */
    public static String canonicalizar(String href) {
        String caminho = href;
        if (caminho.startsWith("http://") || caminho.startsWith("https://")) {
            caminho = URI.create(caminho).getRawPath();
        }
        caminho = URLDecoder.decode(caminho, StandardCharsets.UTF_8);
        caminho = caminho.replace('\\', '/');

        Deque<String> pilha = new ArrayDeque<>();
        for (String parte : caminho.split("/")) {
            if (parte.isEmpty() || parte.equals(".")) {
                continue;
            }
            if (parte.equals("..")) {
                pilha.pollLast();
                continue;
            }
            // Caractere de controle em nome de arquivo não tem uso legítimo e
            // quebra log, e-mail e nome de objeto no bucket.
            if (parte.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
                throw new IllegalArgumentException("caractere de controle no caminho: " + href);
            }
            pilha.addLast(parte);
        }
        String resolvido = "/" + String.join("/", pilha);
        boolean pasta = caminho.endsWith("/") && !resolvido.equals("/");
        return pasta ? resolvido + "/" : resolvido;
    }

    /** Verdadeiro quando {@code caminho} está contido em {@code raiz}. */
    public static boolean dentroDe(String raiz, String caminho) {
        String r = canonicalizar(raiz.endsWith("/") ? raiz : raiz + "/");
        String c = canonicalizar(caminho);
        return c.equals(r) || (c + "/").startsWith(r);
    }

    /** Junta segmentos garantindo uma única barra entre eles. */
    public static String juntar(String base, String... segmentos) {
        StringBuilder sb = new StringBuilder(base.endsWith("/")
                ? base.substring(0, base.length() - 1) : base);
        for (String s : segmentos) {
            sb.append('/').append(s.startsWith("/") ? s.substring(1) : s);
        }
        return sb.toString();
    }
}
