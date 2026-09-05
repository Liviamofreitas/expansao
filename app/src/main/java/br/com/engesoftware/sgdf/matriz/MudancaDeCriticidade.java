package br.com.engesoftware.sgdf.matriz;

import java.util.List;

/**
 * Decide se publicar um rascunho exige o APROVADOR_DAF — cap. 12 e 5.1.
 *
 * <p>O capítulo diz "bloqueante pede aprovação DAF" e não distingue os sentidos.
 * A leitura adotada é a conservadora, e é declarada aqui porque o documento não
 * a fecha: <b>qualquer item que toque uma criticidade BLOQUEANTE exige DAF</b> —
 * criar uma regra bloqueante, tornar bloqueante uma que não era, deixar de ser, e
 * <b>remover</b> uma que era.
 *
 * <p><b>Por que remover conta.</b> Acrescentar exigência bloqueante aperta o
 * portão; remover uma afrouxa. Das duas, a que deixa o faturamento passar sem
 * documento é a segunda — e seria estranho que a mudança perigosa fosse a que
 * dispensa aprovação. Se a área demandante quiser afrouxar essa leitura, é
 * decisão de governança, não de código.
 *
 * <p>A criticidade da regra pode ser nula, o que significa "herda a do tipo
 * documental" (V001). Comparar as nulas diretamente diria que nada mudou quando
 * o tipo por trás é bloqueante — por isso a comparação é sempre sobre a
 * criticidade EFETIVA.
 */
public final class MudancaDeCriticidade {

    public static final String BLOQUEANTE = "BLOQUEANTE";

    private MudancaDeCriticidade() {}

    /**
     * Um item do rascunho, já com as criticidades resolvidas.
     *
     * @param acao      INCLUIR, ALTERAR ou REMOVER
     * @param tipo      código do tipo documental, para a mensagem
     * @param atual     criticidade efetiva na base; nula quando o item é INCLUIR
     * @param proposta  criticidade efetiva depois; nula quando o item é REMOVER
     */
    public record Item(String acao, String tipo, String atual, String proposta) {

        boolean tocaBloqueante() {
            return BLOQUEANTE.equals(atual) || BLOQUEANTE.equals(proposta);
        }

        /** Só conta quando algo de fato muda: reescrever igual não pede aprovação. */
        boolean mudaAlgo() {
            return !java.util.Objects.equals(atual, proposta);
        }

        String descricao() {
            return switch (acao) {
                case "INCLUIR" -> tipo + ": nova regra " + proposta;
                case "REMOVER" -> tipo + ": remove regra " + atual;
                default -> tipo + ": " + atual + " → " + proposta;
            };
        }
    }

    /**
     * O que, no rascunho, exige APROVADOR_DAF. Vazio quando o CURADOR_MATRIZ
     * pode publicar sozinho.
     */
    public static List<String> exigemAprovacaoDaf(List<Item> itens) {
        return itens.stream()
                .filter(Item::tocaBloqueante)
                .filter(Item::mudaAlgo)
                .map(Item::descricao)
                .toList();
    }
}
