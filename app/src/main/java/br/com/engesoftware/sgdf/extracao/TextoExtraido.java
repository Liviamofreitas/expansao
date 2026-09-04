package br.com.engesoftware.sgdf.extracao;

import java.util.List;

/**
 * Resultado da extração de um documento.
 *
 * @param paginas       páginas com texto e posições
 * @param origem        de onde o texto veio
 * @param paginasSemTexto páginas que não atingiram o mínimo de caracteres
 */
public record TextoExtraido(List<PaginaExtraida> paginas, Origem origem,
                            List<Integer> paginasSemTexto) {

    public enum Origem { PDF_NATIVO, OCR, MISTA }

    public TextoExtraido {
        paginas = List.copyOf(paginas);
        paginasSemTexto = List.copyOf(paginasSemTexto);
    }

    /**
     * Verdadeiro quando alguma página não tem camada de texto.
     *
     * <p>Basta UMA página: cap. 8.2 manda OCR para PDF sem camada de texto, e um
     * documento de 40 páginas com a 3ª digitalizada perderia justamente aquela
     * página em silêncio se a decisão fosse por maioria. O documento inteiro vai
     * a triagem (cap. 8.3), porque o operador precisa ver o que faltou.
     */
    public boolean exigeOcr() {
        return !paginasSemTexto.isEmpty();
    }

    public String textoCompleto() {
        StringBuilder sb = new StringBuilder();
        for (PaginaExtraida p : paginas) {
            sb.append(p.texto()).append('\n');
        }
        return sb.toString();
    }

    public int totalDePaginas() {
        return paginas.size();
    }
}
