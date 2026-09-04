package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;

/**
 * R09 — a soma das bases de FGTS da folha, aplicada a alíquota, bate com a guia.
 *
 * <p>Cap. 9: <i>"Σ base_fgts(folha) × alíquota ≈ FGT.GUIA.valor"</i>, tolerância
 * padrão ±0,5%.
 *
 * <p><b>Por que a tolerância tem de ser percentual.</b> Na massa real de
 * 06/2026, 8% da soma das bases deu R$ 3.975,45 e a soma do FGTS impresso nos
 * recibos deu R$ 3.975,44 — <b>um centavo</b>, produzido pelo arredondamento
 * por colaborador. Com oito colaboradores o erro é de um centavo; com
 * oitocentos, não é. Uma tolerância absoluta calibrada nesta folha reprovaria a
 * próxima.
 *
 * <p><b>E por que a base tem de vir da FOPAG.</b> O rodapé do contracheque
 * imprime "Base Cálc. FGTS" incluindo o adiantamento do 13º; o código
 * {@code 14000 BASE FGTS MES} da FOPAG é só a base mensal. Na folha do contrato
 * DOCAS os dois diferem em R$ 5.277,14 — e usar um pelo outro produziria
 * divergência de milhares de reais com os dois documentos corretos.
 */
public final class R09BaseFgtsComGuia implements RegraDeConciliacao {

    /** Alíquota mensal do FGTS. É parâmetro de lei, não de cadastro do cliente. */
    private static final BigDecimal ALIQUOTA = new BigDecimal("0.08");

    private final ResultadoDaConciliacao.Modo modoCadastrado;

    public R09BaseFgtsComGuia(ResultadoDaConciliacao.Modo modoCadastrado) {
        this.modoCadastrado = modoCadastrado;
    }

    @Override
    public String codigo() {
        return "R09";
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado, tolerancia);
        List<Obrigacao> guias = ciclo.obrigacoesDoTipo("FGT.GUIA");

        if (ciclo.folha() == null || guias.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                    modo, null, null, null,
                    ciclo.folha() == null
                            ? "o ciclo não tem folha: R09 não tem o lado esperado"
                            : "o ciclo não tem guia de FGTS: R09 não tem com o que comparar",
                    Map.of());
        }

        // A guia do FGTS é da EMPRESA inteira. Comparar com o recorte de um
        // contrato acusaria divergência de dezenas de milhares de reais com os
        // dois documentos corretos — comparando populações diferentes. Recusar
        // a comparação é o resultado certo; produzi-la seria o falso positivo
        // que o risco P01 descreve.
        if (ciclo.escopoDaFolha() != DadosDoCiclo.EscopoDaFolha.EMPRESA) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                    modo, null, null, null,
                    "a guia do FGTS é da empresa inteira e a folha do ciclo é o recorte do "
                            + "centro de custo " + ciclo.centroDeCusto()
                            + ": R09 precisa da folha completa da competência",
                    Map.of());
        }

        BigDecimal base = ciclo.folha().somaDasBasesFgts();
        BigDecimal esperado = base.multiply(ALIQUOTA, MathContext.DECIMAL64)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal obtido = guias.stream().map(Obrigacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = obtido.subtract(esperado);

        Map<String, List<String>> itens = Map.of(
                "folha", List.of("Σ base FGTS de " + ciclo.folha().itens().size()
                        + " colaborador(es) = R$ " + base + "; × 8% = R$ " + esperado),
                "guia", List.of("R$ " + obtido));

        if (tolerancia.absorve(esperado, obtido)) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.CONFORME,
                    modo, esperado, obtido, delta, null, itens);
        }
        return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.DIVERGENTE,
                modo, esperado, obtido, delta,
                "a guia do FGTS é de R$ " + obtido + " e 8% das bases da folha dá R$ " + esperado
                        + " — diferença de R$ " + delta.abs() + ", acima de "
                        + tolerancia.descricao(),
                itens);
    }
}
