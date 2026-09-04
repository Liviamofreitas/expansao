package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * R01 — toda guia validada tem comprovante pareado.
 *
 * <p>Cap. 9: <i>"para cada FGT.GUIA validada: existe comprovante com |valor_c −
 * valor_g| ≤ tol e identificador igual (ou data_pgto ≤ vencimento+carência)?"</i>
 *
 * <p>É a regra que dá sentido ao pareamento: o comprovante bancário não diz o
 * que paga, e é esta conciliação que decide.
 */
public final class R01GuiaComComprovante implements RegraDeConciliacao {

    private final String tipoDaObrigacao;
    private final ResultadoDaConciliacao.Modo modoCadastrado;

    public R01GuiaComComprovante(String tipoDaObrigacao,
                                 ResultadoDaConciliacao.Modo modoCadastrado) {
        this.tipoDaObrigacao = tipoDaObrigacao;
        this.modoCadastrado = modoCadastrado;
    }

    @Override
    public String codigo() {
        return "R01";
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado, tolerancia);
        List<Obrigacao> guias = ciclo.obrigacoesDoTipo(tipoDaObrigacao);

        if (guias.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                    modo, null, null, null,
                    "o ciclo não tem nenhuma " + tipoDaObrigacao + " validada: "
                            + "não há o que conciliar",
                    Map.of());
        }

        Pareamento.Resultado p = new Pareamento(tolerancia).parear(guias, ciclo.comprovantes());
        BigDecimal esperado = somar(guias);
        BigDecimal obtido = p.pares().stream().map(x -> x.comprovante().valor())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (p.completo()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.CONFORME,
                    modo, esperado, obtido, obtido.subtract(esperado), null, p.itens());
        }

        String faltando = String.join("; ", p.semComprovante().stream()
                .map(o -> o.tipo() + " de " + o.competencia() + " no valor de R$ " + o.valor()
                        + ", vencimento " + o.vencimento())
                .toList());
        return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.DIVERGENTE,
                modo, esperado, obtido, obtido.subtract(esperado),
                "sem comprovante de pagamento pareado: " + faltando
                        + ". Tolerância aplicada: " + tolerancia.descricao(),
                p.itens());
    }

    private static BigDecimal somar(List<Obrigacao> obrigacoes) {
        return obrigacoes.stream().map(Obrigacao::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
