package br.com.engesoftware.sgdf.book;

import java.time.format.DateTimeFormatter;

/**
 * O {@code _manifesto.json} do cap. 10.
 *
 * <p>É o que torna o book <b>conferível sem o sistema</b>: com o manifesto e os
 * arquivos, qualquer pessoa recalcula os hashes e verifica que recebeu o que foi
 * publicado. Por isso ele traz a origem de cada peça e a versão da matriz —
 * cap. 16: a decisão é reproduzível a partir de documento (hash) + versão da
 * regra.
 */
public final class Manifesto {

    public static final String NOME = "_manifesto.json";

    private Manifesto() {}

    public static String json(Book book, java.util.UUID cicloId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        campo(sb, "contrato", book.contrato());
        campo(sb, "competencia", book.competencia());
        sb.append("  \"versao\": ").append(book.versao()).append(",\n");
        campo(sb, "publicado_em",
                book.publicadoEm().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        campo(sb, "publicado_por", book.publicadoPor());
        campo(sb, "ciclo_id", cicloId == null ? null : cicloId.toString());
        campo(sb, "versao_matriz", book.versaoMatriz());
        campo(sb, "hash_conjunto_sha256", book.hashDoConjunto());
        sb.append("  \"pecas\": [\n");
        for (int i = 0; i < book.pecas().size(); i++) {
            Peca p = book.pecas().get(i);
            sb.append("    {");
            sb.append("\"seq\": ").append(p.sequencia());
            sb.append(", \"tipo\": ").append(texto(p.tipo()));
            sb.append(", \"arquivo\": ").append(texto(p.arquivo()));
            sb.append(", \"hash_sha256\": ").append(texto(p.hashSha256()));
            sb.append(", \"origem\": ").append(texto(p.origem()));
            sb.append(", \"validado_em\": ").append(texto(p.validadoEm() == null ? null
                    : p.validadoEm().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            sb.append(", \"tarjado\": ").append(p.tarjado());
            sb.append('}').append(i + 1 < book.pecas().size() ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static void campo(StringBuilder sb, String nome, String valor) {
        sb.append("  ").append(texto(nome)).append(": ").append(texto(valor)).append(",\n");
    }

    private static String texto(String valor) {
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
