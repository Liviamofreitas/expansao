package br.com.engesoftware.sgdf.conciliacao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R06 — todo evento derivado tem seu conjunto documental completo até o prazo.
 *
 * <p>Cap. 9: <i>"todo evento derivado (7.2) tem seu conjunto documental completo
 * até o prazo do evento. Tolerância: ASO demissional +10 dias corridos"</i>.
 *
 * <p><b>Incompleto não é o mesmo que atrasado, e a diferença é o dia de hoje.</b>
 * Um conjunto de rescisão aberto ontem está incompleto e não devia acusar nada:
 * o prazo é do evento, e o evento acabou de acontecer. A regra só diverge quando
 * o prazo <b>passou</b> — antes disso o conjunto incompleto é o estado normal de
 * um evento recente.
 *
 * <p>Por isso, e ao contrário de todas as regras anteriores, esta precisa de uma
 * <b>data de referência</b>, e ela é parâmetro explícito: uma regra que lesse o
 * relógio deixaria de ser reproduzível, e o cap. 16 exige que reexecutar uma
 * conciliação de seis meses atrás sobre os mesmos documentos dê o mesmo
 * resultado. Aqui a data entra pelo ciclo, como o resto.
 *
 * <p><b>O ASO tem prazo próprio.</b> A tolerância do cap. 9 dá +10 dias corridos
 * ao ASO demissional, e a razão é operacional: o exame depende de agenda de
 * clínica, que não obedece ao prazo do TRCT. Aplicar o mesmo prazo aos seis
 * documentos acusaria atraso no ASO em praticamente toda rescisão — falso
 * positivo previsível, do mesmo tipo que a janela pró-rata da R05 evita.
 */
public final class R06ConjuntoDoEvento implements RegraDeConciliacao {

    public static final String CODIGO = "R06";

    /** Cap. 9, tolerância da R06. Dias corridos além do prazo do evento. */
    public static final int CARENCIA_DO_ASO_EM_DIAS = 10;

    private final ResultadoDaConciliacao.Modo modoCadastrado;
    private final LocalDate referencia;

    /**
     * @param referencia a data em que a conciliação é avaliada — nunca o relógio
     */
    public R06ConjuntoDoEvento(ResultadoDaConciliacao.Modo modoCadastrado,
                               LocalDate referencia) {
        this.modoCadastrado = modoCadastrado;
        this.referencia = referencia;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado,
                tolerancia);

        if (ciclo.eventos().isEmpty()) {
            return new ResultadoDaConciliacao(CODIGO,
                    ResultadoDaConciliacao.Situacao.NAO_APLICAVEL, modo, null, null, null,
                    "nenhum evento derivado na competência", Map.of());
        }

        List<String> atrasados = new ArrayList<>();
        List<String> dentroDoPrazo = new ArrayList<>();
        List<String> completos = new ArrayList<>();

        for (DadosDoCiclo.ConjuntoDoEvento evento : ciclo.eventos()) {
            String etiqueta = evento.evento() + " de " + evento.matricula();
            if (evento.completo()) {
                completos.add(etiqueta);
                continue;
            }
            LocalDate limite = limiteDe(evento);
            if (referencia.isAfter(limite)) {
                atrasados.add(etiqueta + ": falta " + String.join(", ", evento.faltando())
                        + " — prazo era " + limite);
            } else {
                dentroDoPrazo.add(etiqueta + ": falta " + String.join(", ", evento.faltando())
                        + " — prazo " + limite);
            }
        }

        Map<String, List<String>> itens = new LinkedHashMap<>();
        itens.put("completos", completos);
        if (!dentroDoPrazo.isEmpty()) {
            itens.put("incompletos_no_prazo", dentroDoPrazo);
        }
        if (!atrasados.isEmpty()) {
            itens.put("atrasados", atrasados);
        }

        if (atrasados.isEmpty()) {
            return new ResultadoDaConciliacao(CODIGO,
                    ResultadoDaConciliacao.Situacao.CONFORME, modo,
                    java.math.BigDecimal.valueOf(ciclo.eventos().size()),
                    java.math.BigDecimal.valueOf(completos.size()),
                    java.math.BigDecimal.valueOf(completos.size() - ciclo.eventos().size()),
                    dentroDoPrazo.isEmpty() ? null
                            : dentroDoPrazo.size() + " conjunto(s) incompleto(s) ainda no prazo",
                    itens);
        }

        return new ResultadoDaConciliacao(CODIGO,
                ResultadoDaConciliacao.Situacao.DIVERGENTE, modo,
                java.math.BigDecimal.valueOf(ciclo.eventos().size()),
                java.math.BigDecimal.valueOf(completos.size()),
                java.math.BigDecimal.valueOf(completos.size() - ciclo.eventos().size()),
                atrasados.size() + " conjunto(s) documental(is) de evento vencido(s)", itens);
    }

    /**
     * O prazo que vale para este conjunto.
     *
     * <p>A carência do ASO se aplica ao CONJUNTO quando o que falta é o ASO — se
     * faltam o TRCT e o ASO, o conjunto já está atrasado pelo TRCT, e estender o
     * prazo por causa do ASO esconderia o atraso do outro documento.
     */
    LocalDate limiteDe(DadosDoCiclo.ConjuntoDoEvento evento) {
        boolean soFaltaOAso = evento.aso() && evento.faltando().size() == 1
                && evento.faltando().get(0).contains("ASO");
        return soFaltaOAso ? evento.prazo().plusDays(CARENCIA_DO_ASO_EM_DIAS) : evento.prazo();
    }
}
