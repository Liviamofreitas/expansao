package br.com.engesoftware.sgdf.validacao;

import java.util.List;
import java.util.Map;

/**
 * Veredito de uma validação unitária sobre um documento.
 *
 * <p>Corresponde a uma linha de {@code validacao_documento}. O motivo não é
 * decoração: o cap. 12 exige mensagem acionável, e o cap. 16 exige que a
 * decisão seja reproduzível. Um "REPROVADO" sem motivo não satisfaz nenhum
 * dos dois.
 *
 * @param codigo   V1..V8, como no cap. 8.4
 * @param veredito resultado
 * @param motivo   por que, em português, dirigido a quem precisa agir
 * @param detalhe  o que sustenta o motivo, para auditoria
 */
public record ResultadoDeValidacao(String codigo, Veredito veredito, String motivo,
                                   Map<String, List<String>> detalhe) {

    public ResultadoDeValidacao {
        if (veredito != Veredito.APROVADO && (motivo == null || motivo.isBlank())) {
            throw new IllegalArgumentException(
                    codigo + ": " + veredito + " sem motivo não é auditável (cap. 16)");
        }
        detalhe = Map.copyOf(detalhe);
    }

    public boolean aprovado() {
        return veredito == Veredito.APROVADO;
    }

    public boolean reprovado() {
        return veredito == Veredito.REPROVADO;
    }
}
