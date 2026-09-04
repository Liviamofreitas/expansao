package br.com.engesoftware.sgdf.extracao;

import java.util.List;

/**
 * Resultado da extração de um documento.
 *
 * <p>Distinguir <b>página em branco</b> de <b>página digitalizada</b> não é
 * preciosismo: um comprovante bancário real de 8 páginas trouxe uma folha em
 * branco no meio, e tratá-la como digitalizada mandaria o documento inteiro
 * para OCR e triagem sem necessidade. Falso positivo em massa é o risco P01 do
 * cap. 22 — o que leva o operador a abandonar a ferramenta.
 *
 * <p>O critério é ter imagem: página sem texto e sem imagem não tem conteúdo a
 * recuperar; página sem texto e com imagem é folha digitalizada e precisa de
 * OCR (cap. 8.2).
 *
 * @param paginas         páginas com texto e posições
 * @param origem          de onde o texto veio
 * @param paginasParaOcr  sem texto, mas com imagem — há conteúdo a recuperar
 * @param paginasSemRecuperacao texto abaixo do mínimo e sem imagem — pode ser folha
 *                              em branco ou página de pouco texto; em ambos os casos
 *                              o OCR não acrescentaria nada
 */
public record TextoExtraido(List<PaginaExtraida> paginas, Origem origem,
                            List<Integer> paginasParaOcr, List<Integer> paginasSemRecuperacao) {

    public enum Origem { PDF_NATIVO, OCR, MISTA }

    public TextoExtraido {
        paginas = List.copyOf(paginas);
        paginasParaOcr = List.copyOf(paginasParaOcr);
        paginasSemRecuperacao = List.copyOf(paginasSemRecuperacao);
    }

    /**
     * Verdadeiro quando alguma página tem conteúdo que só o OCR recupera.
     *
     * <p>Basta UMA: um documento de 40 páginas com a 3ª digitalizada perderia
     * justamente aquela página em silêncio se a decisão fosse por maioria.
     * Páginas em branco não contam — ver a nota da classe.
     */
    public boolean exigeOcr() {
        return !paginasParaOcr.isEmpty();
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
