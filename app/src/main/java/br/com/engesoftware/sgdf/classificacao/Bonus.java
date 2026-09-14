package br.com.engesoftware.sgdf.classificacao;

import java.util.Map;

/**
 * Bônus de contexto: pasta compatível e alias no nome do arquivo (cap. 8.3).
 *
 * <p>É deliberadamente pequeno e deliberadamente incapaz de eleger sozinho.
 * O {@link Classificador} só o considera depois de o CONTEÚDO já ter alcançado
 * o limiar de triagem: renomear um arquivo não pode classificá-lo, e o sistema
 * existe justamente porque o nome do arquivo não é confiável.
 */
public record Bonus(Map<String, Double> porTipo) {

    /**
     * O teto do bônus, e ele é o mesmo para quem monta e para quem cadastra.
     *
     * <p>Público porque o peso do alias agora vem do cadastro (RA-03) e alguém
     * precisa validar o valor <b>antes</b> de ele virar {@code Bonus} — recusar
     * só aqui transformaria um número errado numa linha do cadastro em falha no
     * meio da classificação de cada arquivo. Dois números diferentes para o
     * mesmo limite seriam a duplicação que a RA-10 acabou de custar caro.
     */
    public static final double MAXIMO = 0.20;

    private static final Bonus NENHUM = new Bonus(Map.of());

    public Bonus {
        porTipo = Map.copyOf(porTipo);
        porTipo.forEach((tipo, valor) -> {
            if (valor < 0 || valor > MAXIMO) {
                throw new IllegalArgumentException("bônus de " + tipo + " fora de [0; " + MAXIMO + "]: "
                        + valor + ". Bônus grande vira classificação pelo nome do arquivo.");
            }
        });
    }

    public static Bonus nenhum() {
        return NENHUM;
    }

    public double para(String tipo) {
        return porTipo.getOrDefault(tipo, 0.0);
    }
}
