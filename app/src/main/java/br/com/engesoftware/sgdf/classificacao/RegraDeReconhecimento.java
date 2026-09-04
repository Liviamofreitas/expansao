package br.com.engesoftware.sgdf.classificacao;

import br.com.engesoftware.sgdf.extracao.PadraoDeCampo;
import br.com.engesoftware.sgdf.validacao.CampoEssencial;
import java.util.ArrayList;
import java.util.List;

/**
 * Como reconhecer um tipo documental. Corresponde a uma linha de
 * {@code regra_reconhecimento}.
 *
 * @param tipo           código do tipo documental
 * @param emissor        quem emite o documento, quando o mesmo tipo tem mais de
 *                       um layout ("FLASH", "PLUXEE"); nulo quando é único
 * @param ancoras        expressões que devem ocorrer, com peso
 * @param campos         padrões de extração; cada campo válido soma peso
 * @param essenciais     o que V8 exige depois de a classificação decidir
 * @param pesoPorCampo   quanto vale cada campo extraído e válido
 * @param limiarAuto     score a partir do qual o vínculo é automático
 * @param limiarTriagem  score abaixo do qual o arquivo é desconhecido
 * @param versao         versão da regra — gravada na decisão (cap. 16)
 */
public record RegraDeReconhecimento(String tipo, String emissor, List<Ancora> ancoras,
                                    List<PadraoDeCampo> campos,
                                    List<CampoEssencial> essenciais,
                                    double pesoPorCampo,
                                    double limiarAuto, double limiarTriagem, int versao) {

    public RegraDeReconhecimento {
        if (ancoras.isEmpty()) {
            throw new IllegalArgumentException(
                    "regra de '" + tipo + "' sem âncora: classificaria pelo nome do arquivo, "
                            + "que é exatamente o que o sistema existe para não fazer");
        }
        if (limiarTriagem >= limiarAuto) {
            throw new IllegalArgumentException("regra de '" + tipo
                    + "': limiar de triagem precisa ser menor que o automático");
        }
        ancoras = List.copyOf(ancoras);
        campos = List.copyOf(campos);
        essenciais = List.copyOf(essenciais);
    }

    /** Construtor de cadastro com os limiares padrão do cap. 8.3. */
    public static RegraDeReconhecimento de(String tipo, List<Ancora> ancoras) {
        return de(tipo, null, ancoras);
    }

    /**
     * Regra de um emissor específico de um tipo que tem vários.
     *
     * <p>Duas relações de VA/VR da massa real — Flash e Pluxee — não têm uma
     * palavra em comum além do nome da empresa. Uma regra por tipo não alcança
     * as duas: ou fica genérica a ponto de casar com qualquer coisa, ou casa com
     * uma e recusa a outra. Regras irmãs resolvem, e a decisão continua sendo o
     * tipo — o emissor entra na evidência, não na resposta.
     */
    public static RegraDeReconhecimento de(String tipo, String emissor, List<Ancora> ancoras) {
        return new RegraDeReconhecimento(tipo, emissor, ancoras, List.of(), List.of(),
                0.0, 0.95, 0.70, 1);
    }

    public RegraDeReconhecimento comCampos(List<PadraoDeCampo> campos, double pesoPorCampo) {
        return new RegraDeReconhecimento(tipo, emissor, ancoras, campos, essenciais, pesoPorCampo,
                limiarAuto, limiarTriagem, versao);
    }

    public RegraDeReconhecimento comEssenciais(List<CampoEssencial> essenciais) {
        return new RegraDeReconhecimento(tipo, emissor, ancoras, campos, essenciais, pesoPorCampo,
                limiarAuto, limiarTriagem, versao);
    }

    /** Identidade da regra: tipo, ou tipo e emissor quando o tipo tem vários. */
    public String identidade() {
        return emissor == null ? tipo : tipo + " (" + emissor + ")";
    }

    /** Soma de tudo que a regra pode marcar — o denominador do score. */
    public double pesoTotal() {
        double total = ancoras.stream().mapToDouble(Ancora::peso).sum();
        return total + campos.size() * pesoPorCampo;
    }

    public List<Ancora> discriminantes() {
        List<Ancora> ds = new ArrayList<>();
        for (Ancora a : ancoras) {
            if (a.discriminante()) {
                ds.add(a);
            }
        }
        return ds;
    }
}
