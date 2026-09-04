package br.com.engesoftware.sgdf.book;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** SHA-256, e a definição do hash do conjunto. */
public final class Hash {

    private Hash() {}

    public static String de(byte[] conteudo) {
        return hex(digest().digest(conteudo));
    }

    /**
     * O hash do conjunto — {@code hash_conjunto_sha256} do manifesto.
     *
     * <p><b>É o hash dos hashes, na ordem da sequência.</b> A definição importa
     * e por isso está aqui e não espalhada: o cliente precisa conseguir
     * reproduzi-la com o book na mão, e uma definição implícita ("de algum
     * jeito somamos os hashes") não é reproduzível.
     *
     * <p>Depende da ordem de propósito. Duas publicações com as mesmas peças em
     * ordem diferente são books diferentes: a numeração é parte do que se
     * entrega, e o índice remete a ela.
     */
    public static String doConjunto(List<Peca> pecas) {
        StringBuilder sb = new StringBuilder();
        for (Peca p : pecas) {
            sb.append(p.sequencia()).append(':').append(p.hashSha256()).append('\n');
        }
        return hex(digest().digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
