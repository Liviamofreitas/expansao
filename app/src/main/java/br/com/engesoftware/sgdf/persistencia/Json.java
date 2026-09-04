package br.com.engesoftware.sgdf.persistencia;

import java.util.List;
import java.util.Map;

/**
 * Serialização JSON mínima, só o que as colunas {@code jsonb} recebem.
 *
 * <p>Existe para não trazer uma biblioteca inteira por causa de três mapas. Se
 * o projeto precisar ler JSON de volta, ou serializar objetos arbitrários, esta
 * classe deve ser substituída — ela é deliberadamente incapaz disso, para que a
 * substituição seja óbvia e não uma extensão gradual.
 */
final class Json {

    private Json() {}

    /** Um mapa de listas de texto — a forma de {@code detalhe} e {@code itens}. */
    static String de(Map<String, List<String>> mapa) {
        StringBuilder sb = new StringBuilder("{");
        boolean primeiro = true;
        for (Map.Entry<String, List<String>> e : mapa.entrySet()) {
            if (!primeiro) {
                sb.append(',');
            }
            primeiro = false;
            sb.append(texto(e.getKey())).append(':').append('[');
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(texto(e.getValue().get(i)));
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }

    static String texto(String valor) {
        if (valor == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // O achado A15 encontrou NUL no texto extraído. PostgreSQL
                    // recusa NUL em text e em jsonb; escapar é o que impede que
                    // um documento com fonte quebrada derrube a gravação.
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
