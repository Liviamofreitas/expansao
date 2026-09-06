package br.com.engesoftware.sgdf.indicadores;

import java.math.BigDecimal;

/**
 * Um indicador do cap. 21, com o que foi contado para chegar nele.
 *
 * <p><b>O número sozinho não é auditável, e por isso a população vem junto.</b>
 * "98%" pode ser 98 de 100 ou 49 de 50, e pode ser 0 de 0 disfarçado de nada.
 * Quem lê o painel precisa poder perguntar "de quantos?" sem abrir o banco — é a
 * mesma razão pela qual a conciliação acusa os dois números em vez de somá-los.
 *
 * @param valor       nulo quando não há população — ver {@link Situacao#SEM_POPULACAO}
 * @param numerador   o que entrou em cima; nulo em indicadores de contagem
 * @param denominador de quantos; nulo em indicadores de contagem
 * @param observacao  o que o leitor precisa saber para não ler errado; pode ser nulo
 */
public record Indicador(String codigo, String nome, BigDecimal valor, Unidade unidade,
                        Meta meta, Integer numerador, Integer denominador, String fonte,
                        String observacao) {

    /**
     * O que dizer sobre a meta.
     *
     * <p>Quatro estados e não dois. {@link #SEM_POPULACAO} é o que impede o
     * painel de afirmar sobre uma competência da qual nada se sabe ainda: zero
     * de zero não é 0% nem 100%, e um mês que começou hoje não descumpriu nada.
     */
    public enum Situacao {
        ATINGIDA,
        NAO_ATINGIDA,
        /** KRI: a leitura é a série, não o valor isolado. */
        MONITORADO,
        /** Não houve o que contar. Nem sucesso nem falha. */
        SEM_POPULACAO
    }

    public enum Unidade { PERCENTUAL, DIAS, CONTAGEM }

    public Situacao situacao() {
        if (valor == null) {
            return Situacao.SEM_POPULACAO;
        }
        Boolean cumpre = meta.cumpre(valor);
        if (cumpre == null) {
            return Situacao.MONITORADO;
        }
        return cumpre ? Situacao.ATINGIDA : Situacao.NAO_ATINGIDA;
    }

    /** "98,00% (98 de 100)" — o número e de quantos, na mesma linha. */
    public String descricaoDoValor() {
        if (valor == null) {
            return "—";
        }
        String v = switch (unidade) {
            case PERCENTUAL -> valor + "%";
            case DIAS -> valor + " dia(s)";
            case CONTAGEM -> valor.toPlainString();
        };
        return denominador == null ? v : v + " (" + numerador + " de " + denominador + ")";
    }

    /** Razão: numerador/denominador em percentual, nula quando não há população. */
    public static Indicador razao(String codigo, String nome, int numerador, int denominador,
                                  Meta meta, String fonte, String observacao) {
        BigDecimal valor = denominador == 0 ? null
                : BigDecimal.valueOf(numerador * 100L)
                        .divide(BigDecimal.valueOf(denominador), 2,
                                java.math.RoundingMode.HALF_UP);
        return new Indicador(codigo, nome, valor, Unidade.PERCENTUAL, meta, numerador,
                denominador, fonte, observacao);
    }

    /**
     * Contagem.
     *
     * <p>Zero é um valor, não ausência: "nenhuma exceção aprovada nesta
     * competência" é informação, e devolver nulo a esconderia. É o oposto do
     * caso da razão, onde zero de zero não afirma nada — e a diferença é
     * exatamente que aqui houve o que contar.
     */
    public static Indicador contagem(String codigo, String nome, int quantidade, Meta meta,
                                     String fonte, String observacao) {
        return new Indicador(codigo, nome, BigDecimal.valueOf(quantidade), Unidade.CONTAGEM,
                meta, null, null, fonte, observacao);
    }

    /** Média em dias, sobre uma população que pode estar vazia. */
    public static Indicador media(String codigo, String nome, BigDecimal media, int populacao,
                                  Meta meta, String fonte, String observacao) {
        return new Indicador(codigo, nome, populacao == 0 ? null : media, Unidade.DIAS, meta,
                null, populacao == 0 ? null : populacao, fonte, observacao);
    }
}
