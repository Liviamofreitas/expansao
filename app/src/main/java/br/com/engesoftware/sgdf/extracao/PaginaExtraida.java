package br.com.engesoftware.sgdf.extracao;

import java.util.List;

/**
 * Uma página com o texto e a posição de cada caractere.
 *
 * @param numero  página, começando em 1
 * @param texto   texto na ordem de leitura
 * @param glifos  um glifo por caractere de {@code texto}, no mesmo índice
 */
public record PaginaExtraida(int numero, String texto, List<Glifo> glifos) {

    public PaginaExtraida {
        if (texto.length() != glifos.size()) {
            throw new IllegalStateException(
                    "texto e glifos desalinhados na página " + numero
                            + ": " + texto.length() + " vs " + glifos.size());
        }
        glifos = List.copyOf(glifos);
    }

    public boolean temTexto(int minimoDeCaracteres) {
        return texto.replaceAll("\\s", "").length() >= minimoDeCaracteres;
    }
}
