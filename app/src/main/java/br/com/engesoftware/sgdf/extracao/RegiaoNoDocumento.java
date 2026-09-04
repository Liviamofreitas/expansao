package br.com.engesoftware.sgdf.extracao;

import java.util.List;

/**
 * Onde, no documento, um valor foi lido — critério de aceite da história F1-02.
 *
 * <p>É uma LISTA de retângulos, não um só: um valor que quebra linha ocupa dois
 * trechos distantes, e a envolvente dos dois cobriria meia página, destacando
 * texto que não é o valor. Cada retângulo corresponde a um trecho contínuo numa
 * mesma linha.
 *
 * <p>Persistido em {@code campo_extraido.posicao} (jsonb).
 *
 * @param pagina      número da página, começando em 1
 * @param retangulos  trechos contínuos onde o valor aparece
 */
public record RegiaoNoDocumento(int pagina, List<Retangulo> retangulos) {

    public RegiaoNoDocumento {
        if (retangulos.isEmpty()) {
            throw new IllegalArgumentException("região sem retângulo não é auditável");
        }
        retangulos = List.copyOf(retangulos);
    }

    /** Menor retângulo que cobre todos os trechos. Útil para rolar até o valor. */
    public Retangulo envolvente() {
        return retangulos.stream().reduce(Retangulo::unir).orElseThrow();
    }

    /** Serialização para {@code campo_extraido.posicao}. */
    public String paraJson() {
        StringBuilder sb = new StringBuilder("{\"pagina\":").append(pagina).append(",\"retangulos\":[");
        for (int i = 0; i < retangulos.size(); i++) {
            Retangulo r = retangulos.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"x\":").append(r.x()).append(",\"y\":").append(r.y())
              .append(",\"largura\":").append(r.largura())
              .append(",\"altura\":").append(r.altura()).append('}');
        }
        return sb.append("]}").toString();
    }
}
