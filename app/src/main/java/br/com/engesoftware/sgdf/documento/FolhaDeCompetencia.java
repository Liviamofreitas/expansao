package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A folha de uma competência, montada a partir dos contracheques.
 *
 * <p><b>Sobre a pendência A05.</b> O sistema de folha da Engesoftware ainda não
 * foi nomeado, e o layout de exportação do cap. 14.3 continua sem fonte. Isso
 * não é resolvido aqui — continua sendo decisão de TI e da área demandante, e
 * um export estruturado seguirá sendo melhor que reler PDF.
 *
 * <p>O que muda é o BLOQUEIO. A fase 1b estava parada por não ter o lado
 * esperado das conciliações; o contracheque é fonte verificável desse lado, e
 * já está no repositório. A folha derivada dele não é uma aproximação: cada
 * item carrega a conferência que o próprio documento imprime, e um item que
 * não fecha é recusado em vez de somado.
 *
 * @param competencia competência no formato MM/AAAA
 * @param itens       um por colaborador, já sem as vias repetidas
 */
public record FolhaDeCompetencia(String competencia, List<ItemDaFolha> itens) {

    public FolhaDeCompetencia {
        itens = List.copyOf(itens);
    }

    /**
     * A folha recortada num centro de custo.
     *
     * <p>A FOPAG é emitida por EMPRESA, não por contrato: as páginas se agrupam
     * por centro de custo, e o recorte de um ciclo de faturamento é o centro de
     * custo. Conciliar sem recortar somaria colaboradores de outro contrato —
     * e a divergência apareceria em todas as regras de valor, com os dois lados
     * corretos.
     */
    public FolhaDeCompetencia doCentroDeCusto(String centro) {
        List<ItemDaFolha> recorte = itens.stream()
                .filter(i -> centro == null ? i.centroDeCusto() == null
                                            : centro.equals(i.centroDeCusto()))
                .toList();
        return new FolhaDeCompetencia(competencia, recorte);
    }

    /** Centros de custo presentes na folha, na ordem em que aparecem. */
    public List<String> centrosDeCusto() {
        return itens.stream().map(ItemDaFolha::centroDeCusto)
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    public Optional<ItemDaFolha> porMatricula(String matricula) {
        return itens.stream().filter(i -> i.matricula().equals(matricula)).findFirst();
    }

    public Optional<ItemDaFolha> porCpf(String cpf) {
        return itens.stream().filter(i -> cpf.equals(i.cpf())).findFirst();
    }

    /** Matrículas da competência — insumo da R05 e da R11. */
    public List<String> matriculas() {
        return itens.stream().map(ItemDaFolha::matricula).toList();
    }

    /** Quem tem a rubrica de desconto pedida, por CPF — insumo da R07 e da R08. */
    public Map<String, Rubrica> comDescontoDe(String trecho) {
        Map<String, Rubrica> achados = new LinkedHashMap<>();
        for (ItemDaFolha i : itens) {
            i.desconto(trecho).ifPresent(r -> achados.put(i.cpf(), r));
        }
        return achados;
    }

    /** Soma das bases de FGTS — insumo da R09. */
    public BigDecimal somaDasBasesFgts() {
        return somar(ItemDaFolha::baseFgts);
    }

    /** Soma dos salários de contribuição do INSS — insumo da R10. */
    public BigDecimal somaDasBasesInss() {
        return somar(ItemDaFolha::baseInss);
    }

    public BigDecimal somaDosLiquidos() {
        return somar(ItemDaFolha::liquido);
    }

    /** Todo item que não fecha consigo mesmo, nomeado. */
    public List<String> inconsistencias() {
        List<String> erros = new ArrayList<>();
        for (ItemDaFolha i : itens) {
            for (String erro : i.inconsistencias()) {
                erros.add(i.matricula() + " (" + i.nome() + "): " + erro);
            }
        }
        return erros;
    }

    private BigDecimal somar(java.util.function.Function<ItemDaFolha, BigDecimal> campo) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemDaFolha i : itens) {
            BigDecimal v = campo.apply(i);
            if (v != null) {
                total = total.add(v);
            }
        }
        return total;
    }
}
