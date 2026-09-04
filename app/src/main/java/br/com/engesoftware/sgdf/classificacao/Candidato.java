package br.com.engesoftware.sgdf.classificacao;

import java.util.List;

/**
 * Um tipo documental que o arquivo poderia ser, com quanto marcou e por quê.
 *
 * <p>O "por quê" não é diagnóstico: o cap. 16 exige que toda decisão automática
 * seja reproduzível, e um score sem as âncoras que o produziram obriga quem
 * audita a reexecutar a regra para entender a decisão.
 *
 * @param tipo             código do tipo documental
 * @param scoreDeConteudo  só âncoras e campos, sem bônus — em [0,1]
 * @param score            com os bônus de pasta e de nome de arquivo, em [0,1]
 * @param evidencias       o que casou, em português
 * @param versaoDaRegra    versão da regra que produziu este score
 */
public record Candidato(String tipo, double scoreDeConteudo, double score,
                        List<String> evidencias, int versaoDaRegra) {

    public Candidato {
        evidencias = List.copyOf(evidencias);
    }
}
