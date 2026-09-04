package br.com.engesoftware.sgdf.extracao;

import java.util.List;

/**
 * Uma página com o texto e a posição de cada caractere.
 *
 * @param numero               página, começando em 1
 * @param texto                texto na ordem de leitura, já sanitizado
 * @param glifos               um glifo por caractere de {@code texto}, no mesmo índice
 * @param caracteresDescartados quantos caracteres a sanitização removeu — ver
 *                             {@link TextoExtraido#extracaoDegradada()}
 */
public record PaginaExtraida(int numero, String texto, List<Glifo> glifos,
                             int caracteresDescartados) {

    public PaginaExtraida {
        if (texto.length() != glifos.size()) {
            throw new IllegalStateException(
                    "texto e glifos desalinhados na página " + numero
                            + ": " + texto.length() + " vs " + glifos.size());
        }
        glifos = List.copyOf(glifos);
    }

    public PaginaExtraida(int numero, String texto, List<Glifo> glifos) {
        this(numero, texto, glifos, 0);
    }

    public boolean temTexto(int minimoDeCaracteres) {
        return texto.replaceAll("\\s", "").length() >= minimoDeCaracteres;
    }
}
