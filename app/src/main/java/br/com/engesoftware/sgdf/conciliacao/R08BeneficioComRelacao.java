package br.com.engesoftware.sgdf.conciliacao;

import br.com.engesoftware.sgdf.documento.BeneficiarioDaRelacao;
import br.com.engesoftware.sgdf.documento.ItemDaFolha;
import br.com.engesoftware.sgdf.documento.LeitorDeRelacaoDeBeneficio;
import br.com.engesoftware.sgdf.documento.RelacaoDeBeneficio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * R07 e R08 — a relação do fornecedor bate com a folha, beneficiário a
 * beneficiário.
 *
 * <p>Cap. 9: <i>"beneficiários(relação) ≡ matrículas com rubrica de desconto na
 * folha; comprovante pareado com fatura"</i>.
 *
 * <p><b>Correção que a massa real impôs.</b> A primeira leitura da regra
 * comparava o DESCONTO da folha com o CRÉDITO da relação. São grandezas
 * diferentes: o desconto é a coparticipação do empregado (R$ 6,05) e o crédito
 * é o valor cheio do benefício (R$ 604,80). Comparados entre si, acusariam
 * divergência em <b>todos</b> os registros.
 *
 * <p>A FOPAG mostrou a relação certa, e ela está impressa no documento:
 * {@code 17300 CUST TT VL ALIME} = {@code 17305 CUST EMP} + a rubrica de
 * desconto {@code 08305}. O custo total é o que se compara com a relação.
 *
 * <p><b>A junção é por matrícula, nunca por nome.</b> Nome tem homônimo, grafia
 * variante e quebra de linha — e a folha imprime "000101963" enquanto o
 * fornecedor imprime "101963", então nem a matrícula se compara como texto.
 */
public final class R08BeneficioComRelacao implements RegraDeConciliacao {

    private final String codigo;
    private final String tipoDaRelacao;
    private final String codigoDoCustoTotal;
    private final ResultadoDaConciliacao.Modo modoCadastrado;

    /**
     * @param codigo             R07 (plano de saúde) ou R08 (VA/VR e VT)
     * @param tipoDaRelacao      tipo documental da relação do fornecedor
     * @param codigoDoCustoTotal código da rubrica de custo total na FOPAG
     */
    public R08BeneficioComRelacao(String codigo, String tipoDaRelacao,
                                  String codigoDoCustoTotal,
                                  ResultadoDaConciliacao.Modo modoCadastrado) {
        this.codigo = codigo;
        this.tipoDaRelacao = tipoDaRelacao;
        this.codigoDoCustoTotal = codigoDoCustoTotal;
        this.modoCadastrado = modoCadastrado;
    }

    @Override
    public String codigo() {
        return codigo;
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado, tolerancia);
        RelacaoDeBeneficio relacao = ciclo.relacoes().get(tipoDaRelacao);

        if (ciclo.folha() == null || relacao == null) {
            return new ResultadoDaConciliacao(codigo, ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                    modo, null, null, null,
                    ciclo.folha() == null
                            ? "o ciclo não tem folha: " + codigo + " não tem o lado esperado"
                            : "o ciclo não tem " + tipoDaRelacao + ": nada a conciliar",
                    Map.of());
        }

        // A relação tem de fechar consigo antes de ser confrontada com a folha.
        // Conciliar contra uma leitura que não reproduz o próprio total do
        // documento produziria um veredito sobre número errado.
        if (!relacao.confere()) {
            return new ResultadoDaConciliacao(codigo, ResultadoDaConciliacao.Situacao.DIVERGENTE,
                    modo, relacao.totalDeclarado(), relacao.somaLida(), null,
                    "a relação não fecha consigo mesma: "
                            + String.join("; ", relacao.divergenciasComOTotal()),
                    Map.of("relacao", relacao.divergenciasComOTotal()));
        }

        List<String> divergentes = new ArrayList<>();
        List<String> soNaRelacao = new ArrayList<>();
        BigDecimal somaNaFolha = BigDecimal.ZERO;

        for (BeneficiarioDaRelacao b : relacao.beneficiarios()) {
            Optional<ItemDaFolha> naFolha = porMatricula(ciclo, b.matricula());
            if (naFolha.isEmpty()) {
                soNaRelacao.add(b.matricula() + " (" + b.nome() + ") recebeu R$ " + b.valor()
                        + " e não está na folha da competência");
                continue;
            }
            BigDecimal custo = naFolha.get().resultado(codigoDoCustoTotal);
            if (custo == null) {
                divergentes.add(b.matricula() + " (" + naFolha.get().nome()
                        + ") recebeu R$ " + b.valor()
                        + " e a folha não traz a rubrica de custo " + codigoDoCustoTotal);
                continue;
            }
            somaNaFolha = somaNaFolha.add(custo);
            if (!tolerancia.absorve(custo, b.valor())) {
                divergentes.add(b.matricula() + " (" + naFolha.get().nome() + "): a relação credita R$ "
                        + b.valor() + " e a folha calcula R$ " + custo
                        + " — diferença de R$ " + b.valor().subtract(custo).abs());
            }
        }

        // Quem tem custo na folha e não aparece na relação: o benefício foi
        // provisionado e não foi comprado.
        List<String> soNaFolha = new ArrayList<>();
        for (ItemDaFolha i : ciclo.folha().itens()) {
            BigDecimal custo = i.resultado(codigoDoCustoTotal);
            if (custo == null || custo.signum() == 0) {
                continue;
            }
            String sem = LeitorDeRelacaoDeBeneficio.semZeros(i.matricula());
            if (relacao.beneficiarios().stream().noneMatch(b -> b.matricula().equals(sem))) {
                soNaFolha.add(sem + " (" + i.nome() + ") tem custo de R$ " + custo
                        + " na folha e não consta na relação");
            }
        }

        Map<String, List<String>> itens = new LinkedHashMap<>();
        itens.put("relacao", List.of(relacao.beneficiarios().size() + " beneficiário(s), R$ "
                + relacao.somaLida()));
        itens.put("folha", List.of("custo " + codigoDoCustoTotal + " = R$ " + somaNaFolha));
        if (!divergentes.isEmpty()) {
            itens.put("valores_divergentes", List.copyOf(divergentes));
        }
        if (!soNaRelacao.isEmpty()) {
            itens.put("so_na_relacao", List.copyOf(soNaRelacao));
        }
        if (!soNaFolha.isEmpty()) {
            itens.put("so_na_folha", List.copyOf(soNaFolha));
        }

        BigDecimal delta = relacao.somaLida().subtract(somaNaFolha);
        if (divergentes.isEmpty() && soNaRelacao.isEmpty() && soNaFolha.isEmpty()) {
            return new ResultadoDaConciliacao(codigo, ResultadoDaConciliacao.Situacao.CONFORME,
                    modo, somaNaFolha, relacao.somaLida(), delta, null, itens);
        }

        List<String> tudo = new ArrayList<>(divergentes);
        tudo.addAll(soNaRelacao);
        tudo.addAll(soNaFolha);
        return new ResultadoDaConciliacao(codigo, ResultadoDaConciliacao.Situacao.DIVERGENTE,
                modo, somaNaFolha, relacao.somaLida(), delta,
                String.join(". ", tudo) + ". Tolerância aplicada: " + tolerancia.descricao(),
                itens);
    }

    private static Optional<ItemDaFolha> porMatricula(DadosDoCiclo ciclo, String matricula) {
        return ciclo.folha().itens().stream()
                .filter(i -> LeitorDeRelacaoDeBeneficio.semZeros(i.matricula()).equals(matricula))
                .findFirst();
    }
}
