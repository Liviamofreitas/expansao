package br.com.engesoftware.sgdf.matriz;

import java.time.LocalDate;
import java.util.List;

/**
 * Uma exigência decidida pela resolução, antes de existir no banco.
 *
 * @param prazo        nulo quando a âncora aguarda um evento (cap. 7.3)
 * @param profissional preenchido só no escopo PROFISSIONAL
 * @param avisos       ajustes que o cálculo do prazo precisou fazer
 */
public record ExigenciaResolvida(String tipo, String escopo, String evento, String criticidade,
                                 String responsavel, LocalDate prazo, String profissional,
                                 String condicionalGrupo, List<String> avisos) {

    public ExigenciaResolvida {
        avisos = List.copyOf(avisos);
    }

    /** Cap. 7.1: a chave da exigência é tipo × evento × (profissional, quando individual). */
    public String chave() {
        return tipo + "|" + evento + "|" + (profissional == null ? "-" : profissional);
    }
}
