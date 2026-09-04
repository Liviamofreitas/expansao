package br.com.engesoftware.sgdf.matriz;

/** O tipo documental como o cadastro o define — {@code tipo_documental}. */
public record TipoDoCadastro(String codigo, String escopo, String evento, String criticidade,
                             String condicionalGrupo) {
}
