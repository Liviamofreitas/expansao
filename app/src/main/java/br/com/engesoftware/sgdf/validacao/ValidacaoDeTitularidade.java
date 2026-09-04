package br.com.engesoftware.sgdf.validacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V3 — titularidade: o documento é da empresa a que a exigência se refere.
 *
 * <p><b>Por que não é comparação de string.</b> Os documentos reais trazem o
 * CNPJ de três formas que a igualdade recusaria (achados A6, A7 e A16):
 * truncado na raiz na guia do FGTS, mascarado no comprovante do SICOOB, e com o
 * separador quebrado no rodapé da Flash. São documentos legítimos da empresa
 * certa, e reprová-los é o falso positivo que o risco P01 descreve — o sistema
 * bloqueando faturamento por defeito próprio.
 *
 * <p><b>O que a validação decide, e o que não decide.</b> Ela responde "este
 * documento pode ser da empresa esperada?". Quando o documento traz só a raiz,
 * ela responde sim — porque a raiz é o que o documento afirma, e exigir mais
 * seria inventar informação. Se a exigência é por ESTABELECIMENTO, quem tem de
 * dizer isso é o cadastro, não a validação: por isso {@link #exigindoCnpjCompleto}.
 *
 * <p><b>Um CNPJ de terceiro no documento não reprova.</b> A relação da Flash
 * traz o CNPJ da própria Flash no rodapé, e o comprovante do SICOOB traz o do
 * destinatário. O que reprova é NENHUM dos CNPJs do documento poder ser o da
 * empresa — não a presença de outros.
 */
public final class ValidacaoDeTitularidade {

    public static final String CODIGO = "V3";

    private final boolean exigindoCnpjCompleto;

    /** Aceita documento que traga só a raiz — o caso da guia do FGTS Digital. */
    public ValidacaoDeTitularidade() {
        this(false);
    }

    /**
     * @param exigindoCnpjCompleto verdadeiro quando a exigência é por
     *                             estabelecimento e a raiz não basta
     */
    public ValidacaoDeTitularidade(boolean exigindoCnpjCompleto) {
        this.exigindoCnpjCompleto = exigindoCnpjCompleto;
    }

    /**
     * @param textoDoDocumento texto extraído, de onde os CNPJs são lidos
     * @param cnpjDaEmpresa    CNPJ que a exigência espera, em qualquer formato
     */
    public ResultadoDeValidacao validar(String textoDoDocumento, String cnpjDaEmpresa) {
        if (cnpjDaEmpresa == null || Cnpj.normalizar(cnpjDaEmpresa).length() < 8) {
            return new ResultadoDeValidacao(CODIGO, Veredito.NAO_APLICAVEL,
                    "a exigência não tem CNPJ de empresa cadastrado: "
                            + "V3 não confere titularidade aqui",
                    Map.of());
        }

        List<String> encontrados = Cnpj.todosNo(textoDoDocumento);
        if (encontrados.isEmpty()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "nenhum CNPJ foi encontrado no documento: não há como conferir "
                            + "de quem ele é",
                    Map.of());
        }

        List<String> compativeis = new ArrayList<>();
        List<String> soRaiz = new ArrayList<>();
        List<String> mascarados = new ArrayList<>();
        for (String lido : encontrados) {
            if (Cnpj.podeSer(lido, cnpjDaEmpresa)) {
                compativeis.add(lido);
                if (!Cnpj.completo(lido)) {
                    soRaiz.add(lido);
                }
                if (Cnpj.mascarado(lido)) {
                    mascarados.add(lido);
                }
            }
        }

        Map<String, List<String>> detalhe = new LinkedHashMap<>();
        detalhe.put("encontrados", List.copyOf(encontrados));

        if (compativeis.isEmpty()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o documento não traz o CNPJ da empresa: esperava "
                            + formatar(Cnpj.normalizar(cnpjDaEmpresa)) + " e encontrou "
                            + String.join(", ", encontrados.stream().map(
                                    ValidacaoDeTitularidade::formatar).toList()),
                    detalhe);
        }

        if (exigindoCnpjCompleto && compativeis.size() == soRaiz.size()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o documento traz apenas a raiz do CNPJ (" + formatar(soRaiz.get(0))
                            + ") e este tipo é exigido por estabelecimento: "
                            + "a raiz identifica a empresa, não a filial",
                    detalhe);
        }

        detalhe.put("compativeis", List.copyOf(compativeis));

        // Evidência parcial não é evidência plena, e a diferença tem de ficar
        // no registro. Um CNPJ mascarado como "**.681.946/0001-**" esconde
        // dois dígitos da raiz: cem raízes diferentes casariam com ele. Aprovar
        // é certo — recusar reprovaria um comprovante legítimo — mas quem
        // audita precisa poder ver em que a aprovação se apoiou.
        if (compativeis.size() == mascarados.size()) {
            detalhe.put("evidencia_parcial",
                    List.of("todos os CNPJs compatíveis estão mascarados: "
                            + "a titularidade foi estabelecida sobre dígitos escondidos"));
        } else if (compativeis.size() == soRaiz.size()) {
            detalhe.put("evidencia_parcial",
                    List.of("o documento traz apenas a raiz: prova a empresa, não o "
                            + "estabelecimento"));
        }
        return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null, detalhe);
    }

    /** Devolve o CNPJ à forma legível, preservando coringa. */
    static String formatar(String normalizado) {
        if (normalizado.length() == 8) {
            return normalizado.substring(0, 2) + "." + normalizado.substring(2, 5)
                    + "." + normalizado.substring(5, 8);
        }
        if (normalizado.length() == 14) {
            return normalizado.substring(0, 2) + "." + normalizado.substring(2, 5)
                    + "." + normalizado.substring(5, 8) + "/" + normalizado.substring(8, 12)
                    + "-" + normalizado.substring(12);
        }
        return normalizado;
    }
}
