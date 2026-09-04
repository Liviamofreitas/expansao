package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * O que a folha diz sobre um colaborador numa competência.
 *
 * <p>É o lado ESPERADO de toda conciliação de fase 1b e 2: R05 confere as
 * matrículas, R08 os benefícios, R09 a base do FGTS, R10 as bases tributárias.
 * Sem ele, essas regras não têm com o que comparar.
 *
 * @param matricula       identificador do colaborador na folha — a chave de junção
 * @param nome            nome como impresso
 * @param cpf             CPF como impresso
 * @param competencia     competência no formato MM/AAAA
 * @param proventos       rubricas de crédito
 * @param descontos       rubricas de débito
 * @param totalProventos  total impresso, não somado
 * @param totalDescontos  total impresso, não somado
 * @param liquido         líquido impresso
 * @param baseFgts        base de cálculo do FGTS — insumo da R09
 * @param fgtsMes         FGTS do mês
 * @param baseInss        salário de contribuição do INSS — insumo da R10
 * @param baseIrrf        base de cálculo do IRRF — insumo da R10
 * @param resultados      coluna RESULTADOS da FOPAG, por código de rubrica; vazio
 *                        quando a fonte é o contracheque, que não a imprime
 */
public record ItemDaFolha(String matricula, String nome, String cpf, String competencia,
                          List<Rubrica> proventos, List<Rubrica> descontos,
                          BigDecimal totalProventos, BigDecimal totalDescontos,
                          BigDecimal liquido, BigDecimal baseFgts, BigDecimal fgtsMes,
                          BigDecimal baseInss, BigDecimal baseIrrf,
                          java.util.Map<String, BigDecimal> resultados) {

    public ItemDaFolha {
        proventos = List.copyOf(proventos);
        descontos = List.copyOf(descontos);
        resultados = java.util.Map.copyOf(resultados);
    }

    /** Construtor para fonte sem coluna de resultados codificada (contracheque). */
    public ItemDaFolha(String matricula, String nome, String cpf, String competencia,
                       List<Rubrica> proventos, List<Rubrica> descontos,
                       BigDecimal totalProventos, BigDecimal totalDescontos,
                       BigDecimal liquido, BigDecimal baseFgts, BigDecimal fgtsMes,
                       BigDecimal baseInss, BigDecimal baseIrrf) {
        this(matricula, nome, cpf, competencia, proventos, descontos, totalProventos,
                totalDescontos, liquido, baseFgts, fgtsMes, baseInss, baseIrrf,
                java.util.Map.of());
    }

    /** Valor de um código da coluna RESULTADOS, ou nulo. */
    public BigDecimal resultado(String codigo) {
        return resultados.get(codigo);
    }

    /** A rubrica de código {@code codigo}, entre proventos e descontos. */
    public Optional<Rubrica> rubrica(String codigo) {
        return java.util.stream.Stream.concat(proventos.stream(), descontos.stream())
                .filter(r -> r.temCodigo(codigo)).findFirst();
    }

    /** A rubrica de desconto cuja descrição contém {@code trecho}, se houver. */
    public Optional<Rubrica> desconto(String trecho) {
        return descontos.stream().filter(r -> r.contem(trecho)).findFirst();
    }

    public BigDecimal somaDosProventos() {
        return soma(proventos);
    }

    public BigDecimal somaDosDescontos() {
        return soma(descontos);
    }

    /**
     * O que não fecha dentro do próprio contracheque.
     *
     * <p>O documento carrega a sua própria conferência: a soma das rubricas tem
     * de dar o total impresso, e proventos menos descontos tem de dar o líquido.
     * Uma extração que não reproduz as duas coisas está errada — e é melhor
     * dizer isso do que entregar uma folha plausível com uma rubrica perdida.
     */
    public List<String> inconsistencias() {
        List<String> erros = new ArrayList<>();
        confere(erros, "a soma dos proventos", somaDosProventos(), totalProventos);
        confere(erros, "a soma dos descontos", somaDosDescontos(), totalDescontos);
        if (totalProventos != null && totalDescontos != null && liquido != null) {
            BigDecimal esperado = totalProventos.subtract(totalDescontos);
            if (esperado.compareTo(liquido) != 0) {
                erros.add("proventos menos descontos dá " + esperado
                        + " e o líquido impresso é " + liquido);
            }
        }
        return erros;
    }

    public boolean fecha() {
        return inconsistencias().isEmpty();
    }

    private static void confere(List<String> erros, String oQue,
                                BigDecimal somado, BigDecimal impresso) {
        if (impresso == null) {
            erros.add(oQue + " não pôde ser conferida: o total não foi lido do documento");
        } else if (somado.compareTo(impresso) != 0) {
            erros.add(oQue + " dá " + somado + " e o total impresso é " + impresso);
        }
    }

    private static BigDecimal soma(List<Rubrica> rubricas) {
        return rubricas.stream().map(Rubrica::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
