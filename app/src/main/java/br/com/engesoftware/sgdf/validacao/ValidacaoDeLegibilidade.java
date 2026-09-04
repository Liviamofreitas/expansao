package br.com.engesoftware.sgdf.validacao;

import br.com.engesoftware.sgdf.extracao.PaginaExtraida;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V2 — o documento tem texto legível, ou o OCR foi confirmado em triagem.
 *
 * <p>Cap. 8.2: PDF sem camada de texto vai para OCR, e o resultado marcado
 * {@code ocr=true} tem <b>destino obrigatório: triagem</b>. V2 não reprova o
 * documento escaneado — reprova o ilegível, e encaminha o escaneado para
 * conferência humana.
 *
 * <p><b>O que V2 não faz.</b> Não pergunta se o documento tem conteúdo, só se
 * tem texto. Um comprovante com 603 caracteres de rótulos e nenhum valor passa
 * aqui — foi o achado A12, e é V8 que o pega. As duas validações medem coisas
 * diferentes e as duas são necessárias.
 */
public final class ValidacaoDeLegibilidade {

    public static final String CODIGO = "V2";

    /**
     * Mínimo de caracteres por página para considerar que há camada de texto.
     *
     * <p>Não é um número escolhido no ar: uma página de certidão real tem
     * centenas de caracteres, e uma página escaneada sem OCR tem zero ou uma
     * dúzia de artefatos. O limiar separa os dois casos com folga larga.
     */
    public static final int MINIMO_POR_PAGINA = 50;

    /**
     * @param texto        o que a extração devolveu
     * @param ocrConfirmado se um humano já confirmou o resultado do OCR em triagem
     */
    public ResultadoDeValidacao validar(TextoExtraido texto, boolean ocrConfirmado) {
        if (texto == null || texto.paginas().isEmpty()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o documento não produziu nenhuma página de texto", Map.of());
        }

        List<String> pobres = new ArrayList<>();
        for (PaginaExtraida p : texto.paginas()) {
            int visiveis = contarVisiveis(p.texto());
            if (visiveis < MINIMO_POR_PAGINA) {
                pobres.add("página " + p.numero() + " com " + visiveis + " caractere(s)");
            }
        }

        Map<String, List<String>> detalhe = pobres.isEmpty()
                ? Map.of() : Map.of("paginas_sem_texto", List.copyOf(pobres));

        if (texto.origem() == TextoExtraido.Origem.OCR) {
            if (ocrConfirmado) {
                return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null,
                        Map.of("origem", List.of("OCR confirmado em triagem")));
            }
            return new ResultadoDeValidacao(CODIGO, Veredito.NAO_APLICAVEL,
                    "o documento não tem camada de texto e foi lido por OCR: "
                            + "cap. 8.2 exige confirmação humana em triagem antes de valer",
                    Map.of("origem", List.of("OCR")));
        }

        if (!pobres.isEmpty()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o documento tem " + pobres.size() + " de " + texto.paginas().size()
                            + " página(s) sem texto suficiente (mínimo " + MINIMO_POR_PAGINA
                            + " caracteres): " + String.join(", ", pobres),
                    detalhe);
        }

        // A extração perdeu caracteres que o PDF não soube mapear (achado A15).
        // Não reprova — o texto continua utilizável — mas quem audita precisa
        // saber que o que está gravado não é exatamente o que está no papel.
        if (texto.extracaoDegradada()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null,
                    Map.of("degradacao", List.of(texto.caracteresDescartados()
                            + " caractere(s) descartado(s) na sanitização")));
        }
        return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null, Map.of());
    }

    private static int contarVisiveis(String texto) {
        int n = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (!Character.isWhitespace(texto.charAt(i))) {
                n++;
            }
        }
        return n;
    }
}
