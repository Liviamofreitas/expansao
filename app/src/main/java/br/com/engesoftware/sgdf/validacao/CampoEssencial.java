package br.com.engesoftware.sgdf.validacao;

/**
 * Campo cuja ausência torna o documento inútil para a função que ele cumpre.
 *
 * <p>Corresponde a uma entrada de {@code regra_reconhecimento.campos_essenciais}.
 * Não é "todo campo que a regra extrai": é o campo que alguma outra validação
 * ou regra de conciliação consome. Sem ele o documento não pode ser validado
 * nem conciliado, o que o torna equivalente a não ter sido entregue.
 *
 * @param campo   nome do campo, igual ao da regra de extração
 * @param formato forma que o valor precisa ter
 * @param motivo  por que é indispensável — vai para a mensagem ao usuário
 */
public record CampoEssencial(String campo, FormatoDeCampo formato, String motivo) {

    public CampoEssencial {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "campo essencial '" + campo + "' sem motivo: reprovar um documento sem "
                            + "dizer por que o campo era indispensável é o que o cap. 12 proíbe");
        }
    }
}
