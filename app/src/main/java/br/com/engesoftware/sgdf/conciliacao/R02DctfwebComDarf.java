package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R02 — o total da DCTFWeb é a soma dos DARF vinculados, e cada DARF tem
 * comprovante.
 *
 * <p>Cap. 9: <i>"DCTFWeb.valor_total = Σ DARF.valor vinculados; cada DARF tem
 * comprovante pareado (regra R01)"</i>.
 *
 * <p>São <b>duas</b> conferências encadeadas, e a ordem importa. A primeira é
 * declarativa: o que a empresa declarou deve corresponder às guias emitidas. A
 * segunda é factual: as guias emitidas devem ter sido pagas. Uma pode falhar
 * sem a outra — declarar R$ 100 e emitir R$ 90 em DARF é erro de apuração;
 * emitir R$ 100 e pagar R$ 90 é inadimplência. A mensagem tem de distinguir as
 * duas, porque quem resolve cada uma é uma área diferente.
 */
public final class R02DctfwebComDarf implements RegraDeConciliacao {

    private final ResultadoDaConciliacao.Modo modoCadastrado;

    public R02DctfwebComDarf(ResultadoDaConciliacao.Modo modoCadastrado) {
        this.modoCadastrado = modoCadastrado;
    }

    @Override
    public String codigo() {
        return "R02";
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado, tolerancia);
        List<Obrigacao> declaracoes = ciclo.obrigacoesDoTipo("INS.DCTFWEB");
        List<Obrigacao> darfs = ciclo.obrigacoesDoTipo("INS.DARF");

        if (declaracoes.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                    modo, null, null, null,
                    "o ciclo não tem DCTFWeb validada: R02 não tem o lado declarado",
                    Map.of());
        }

        BigDecimal declarado = somar(declaracoes);
        BigDecimal emDarf = somar(darfs);
        BigDecimal delta = emDarf.subtract(declarado);

        Map<String, List<String>> itens = new LinkedHashMap<>();
        itens.put("declarado", List.of("DCTFWeb R$ " + declarado));
        itens.put("darf", List.of(darfs.size() + " DARF, R$ " + emDarf));

        List<String> problemas = new ArrayList<>();
        if (!tolerancia.absorve(declarado, emDarf)) {
            problemas.add("apuração: a DCTFWeb declara R$ " + declarado + " e os "
                    + darfs.size() + " DARF do ciclo somam R$ " + emDarf
                    + " — diferença de R$ " + delta.abs());
        }

        // Segunda conferência: as guias emitidas foram pagas. Só faz sentido
        // quando há DARF; sem eles o problema já é o de cima.
        if (!darfs.isEmpty()) {
            Pareamento.Resultado p = new Pareamento(tolerancia).parear(darfs, ciclo.comprovantes());
            itens.putAll(p.itens());
            if (!p.completo()) {
                problemas.add("pagamento: " + p.semComprovante().size() + " DARF sem comprovante "
                        + "pareado (" + p.semComprovante().stream()
                        .map(o -> "R$ " + o.valor() + " vencendo " + o.vencimento())
                        .reduce((a, b) -> a + "; " + b).orElse("") + ")");
            }
        }

        if (problemas.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.CONFORME,
                    modo, declarado, emDarf, delta, null, itens);
        }
        return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.DIVERGENTE,
                modo, declarado, emDarf, delta,
                String.join(". ", problemas) + ". Tolerância aplicada: " + tolerancia.descricao(),
                itens);
    }

    private static BigDecimal somar(List<Obrigacao> obrigacoes) {
        return obrigacoes.stream().map(Obrigacao::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
