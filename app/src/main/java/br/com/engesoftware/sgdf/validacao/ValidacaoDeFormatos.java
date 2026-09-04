package br.com.engesoftware.sgdf.validacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V6 — todos os formatos exigidos estão presentes e coerentes entre si.
 *
 * <p>Cap. 8.4 e 7.5: a exigência lista formatos obrigatórios (por exemplo
 * {@code [pdf, xlsx]}) e a satisfação exige <b>todos</b>; entre os formatos do
 * mesmo tipo, a competência tem de ser a mesma.
 *
 * <p><b>A falha aqui não reprova o documento.</b> É a única validação do cap.
 * 8.4 cujo efeito é outro: a exigência <i>permanece parcial</i>, com
 * {@code formatos_pendentes} preenchido. O PDF entregue está certo; o que falta
 * é a planilha. Reprovar o PDF por causa da planilha ausente mandaria reenviar
 * o documento que já está bom.
 *
 * <p>A coerência de competência é a parte que pega o erro real: quando o PDF é
 * de junho e a planilha de maio, os dois documentos são válidos isoladamente e
 * o conjunto não presta.
 */
public final class ValidacaoDeFormatos {

    public static final String CODIGO = "V6";

    /**
     * @param formatosExigidos o que o tipo documental pede
     * @param competenciaPorFormato o que foi entregue: formato para a competência
     *                              lida naquele arquivo
     */
    public ResultadoDeValidacao validar(List<String> formatosExigidos,
                                        Map<String, String> competenciaPorFormato) {
        if (formatosExigidos == null || formatosExigidos.isEmpty()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.NAO_APLICAVEL,
                    "o tipo documental não declara formatos obrigatórios: "
                            + "V6 não confere completude aqui",
                    Map.of());
        }

        List<String> pendentes = new ArrayList<>();
        for (String formato : formatosExigidos) {
            if (!competenciaPorFormato.containsKey(formato)) {
                pendentes.add(formato);
            }
        }

        Set<String> competencias = new LinkedHashSet<>();
        List<String> semCompetencia = new ArrayList<>();
        competenciaPorFormato.forEach((formato, competencia) -> {
            if (competencia == null || competencia.isBlank()) {
                semCompetencia.add(formato);
            } else {
                competencias.add(competencia);
            }
        });

        Map<String, List<String>> detalhe = new LinkedHashMap<>();
        if (!pendentes.isEmpty()) {
            detalhe.put("formatos_pendentes", List.copyOf(pendentes));
        }
        if (competencias.size() > 1) {
            detalhe.put("competencias_divergentes", List.copyOf(competencias));
        }

        if (competencias.size() > 1) {
            // Este é o erro que só V6 pega: cada arquivo é válido sozinho.
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "os formatos entregues são de competências diferentes ("
                            + String.join(", ", competencias)
                            + "): cada arquivo é válido sozinho e o conjunto não é",
                    detalhe);
        }

        if (!pendentes.isEmpty()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "faltam " + pendentes.size() + " de " + formatosExigidos.size()
                            + " formato(s) exigido(s): " + String.join(", ", pendentes)
                            + ". Os já entregues continuam válidos — a exigência fica parcial",
                    detalhe);
        }

        if (!semCompetencia.isEmpty()) {
            detalhe.put("sem_competencia", List.copyOf(semCompetencia));
            return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null, detalhe);
        }
        return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null, detalhe);
    }
}
