package br.com.engesoftware.sgdf.classificacao;

import java.util.List;
import java.util.Optional;

/**
 * Resultado da classificação de um arquivo.
 *
 * @param decisao    o que fazer
 * @param melhor     candidato mais bem pontuado, ou vazio quando nenhum pontuou
 * @param candidatos todos os que pontuaram, do maior para o menor
 * @param motivo     por que a decisão foi essa — vai para a triagem e para o log
 */
public record Classificacao(Decisao decisao, Optional<Candidato> melhor,
                            List<Candidato> candidatos, String motivo) {

    public Classificacao {
        candidatos = List.copyOf(candidatos);
    }

    public boolean automatica() {
        return decisao == Decisao.AUTOMATICA;
    }

    public String tipo() {
        return melhor.map(Candidato::tipo).orElse(null);
    }
}
