package br.com.engesoftware.sgdf.coleta;

/**
 * Resultado de uma verificação antivírus.
 *
 * <p>{@link Situacao#INDISPONIVEL} é deliberadamente distinto de {@code LIMPO}.
 * Antivírus fora do ar não pode ser lido como "arquivo limpo": a validação V1 do
 * cap. 8.4 exige antivírus limpo, e tratar indisponibilidade como aprovação
 * transformaria uma falha de infraestrutura em ingestão de arquivo não
 * verificado.
 */
public record VeredictoAntivirus(Situacao situacao, String assinatura, String detalhe) {

    public enum Situacao { LIMPO, INFECTADO, INDISPONIVEL }

    public static VeredictoAntivirus limpo() {
        return new VeredictoAntivirus(Situacao.LIMPO, null, null);
    }

    public static VeredictoAntivirus infectado(String assinatura) {
        return new VeredictoAntivirus(Situacao.INFECTADO, assinatura, null);
    }

    public static VeredictoAntivirus indisponivel(String detalhe) {
        return new VeredictoAntivirus(Situacao.INDISPONIVEL, null, detalhe);
    }

    public boolean permitido() {
        return situacao == Situacao.LIMPO;
    }
}
