package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * O que uma regra de conciliação concluiu.
 *
 * <p>Corresponde a uma linha de {@code conciliacao}. O cap. 9 exige que o
 * resultado seja <b>sempre gravado com os valores comparados e o delta</b> —
 * inclusive quando conforme. Um "está tudo certo" sem os números não permite a
 * ninguém conferir se estava mesmo.
 *
 * @param codigo    R01..R12
 * @param resultado conforme, divergente ou não aplicável
 * @param modo      BLOQUEIO ou ALERTA, como o cadastro define
 * @param esperado  o valor que a regra esperava; nulo quando a regra é de conjunto
 * @param obtido    o valor encontrado
 * @param delta     obtido menos esperado
 * @param mensagem  o que dizer a quem precisa agir (cap. 12)
 * @param itens     documentos e valores comparados — a base da mensagem acionável
 */
public record ResultadoDaConciliacao(String codigo, Situacao resultado, Modo modo,
                                     BigDecimal esperado, BigDecimal obtido, BigDecimal delta,
                                     String mensagem, Map<String, List<String>> itens) {

    public enum Situacao { CONFORME, DIVERGENTE, NAO_APLICAVEL }

    public enum Modo { BLOQUEIO, ALERTA }

    public ResultadoDaConciliacao {
        if (resultado != Situacao.CONFORME && (mensagem == null || mensagem.isBlank())) {
            throw new IllegalArgumentException(
                    codigo + ": " + resultado + " sem mensagem não é acionável (cap. 12)");
        }
        itens = Map.copyOf(itens);
    }

    /**
     * Se este resultado impede a publicação do book.
     *
     * <p>Só divergência em modo BLOQUEIO trava. Divergência em ALERTA aparece,
     * é registrada e não para o faturamento — é o princípio 1 do cap. 1 outra
     * vez: sem tolerância cadastrada, a regra não bloqueia.
     */
    public boolean bloqueia() {
        return resultado == Situacao.DIVERGENTE && modo == Modo.BLOQUEIO;
    }

    public boolean conforme() {
        return resultado == Situacao.CONFORME;
    }
}
