package br.com.engesoftware.sgdf.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decide qual obrigação cada comprovante paga.
 *
 * <p><b>Por que isto existe.</b> Os comprovantes bancários não se distinguem
 * por conteúdo. Os do INSS e do IRRF do Santander são <b>textualmente
 * idênticos</b>: mesmo cabeçalho, mesma expressão "comprovante de pagamento de
 * DARF", e o código de receita que os separaria não está impresso. Uma regra de
 * classificação que fingisse distingui-los classificaria metade errado com
 * score 1,00 — o pior resultado possível, porque não pediria triagem.
 *
 * <p>O cap. 8.5 já dizia o que fazer: <i>"identificado pelo pareamento: valor +
 * data + identificador da obrigação"</i>. É o que esta classe faz.
 *
 * <p><b>Regra de pareamento (cap. 9, R01).</b> Um comprovante pareia com uma
 * obrigação quando o valor bate dentro da tolerância <b>e</b> (o identificador é
 * o mesmo <b>ou</b> o pagamento ocorreu até o vencimento mais a carência). O
 * "ou" não é frouxidão: o comprovante real do FGTS é um PIX e não carrega o
 * identificador da guia — só valor e data. Exigir o identificador reprovaria um
 * pagamento legítimo.
 */
public final class Pareamento {

    /**
     * Um par comprovante/obrigação, com o motivo pelo qual foi aceito.
     *
     * @param porIdentificador verdadeiro quando o identificador coincidiu — o
     *                         pareamento forte
     */
    public record Par(Obrigacao obrigacao, ComprovanteDePagamento comprovante,
                      BigDecimal delta, boolean porIdentificador) {
    }

    private final Tolerancia tolerancia;

    public Pareamento(Tolerancia tolerancia) {
        this.tolerancia = tolerancia;
    }

    /**
     * Pareia cada obrigação com um comprovante.
     *
     * <p>Um comprovante só pareia uma vez: dois pagamentos iguais em valor e
     * data são dois pagamentos, e reusar um deles esconderia uma obrigação não
     * paga. É a mesma razão do achado A17 na folha — contar duas vezes o mesmo
     * documento produz um resultado bonito e falso.
     */
    public Resultado parear(List<Obrigacao> obrigacoes,
                            List<ComprovanteDePagamento> comprovantes) {
        List<Par> pares = new ArrayList<>();
        List<Obrigacao> semComprovante = new ArrayList<>();
        List<ComprovanteDePagamento> disponiveis = new ArrayList<>(comprovantes);

        // Por POSIÇÃO, não por igualdade de valor. Dois DARF do mesmo valor no
        // mesmo vencimento são registros iguais e obrigações distintas: tratar
        // os dois como um só daria uma delas por paga sem comprovante nenhum.
        boolean[] pareada = new boolean[obrigacoes.size()];

        // Primeiro os pareamentos por identificador, que são certos; só depois
        // os por valor e data, que são inferência. Fazer na ordem inversa
        // deixaria um pareamento fraco consumir o comprovante que casaria
        // exatamente com outra obrigação.
        for (boolean exigindoIdentificador : new boolean[] {true, false}) {
            for (int i = 0; i < obrigacoes.size(); i++) {
                if (pareada[i]) {
                    continue;
                }
                Obrigacao o = obrigacoes.get(i);
                Optional<ComprovanteDePagamento> achado =
                        procurar(o, disponiveis, exigindoIdentificador);
                if (achado.isPresent()) {
                    disponiveis.remove(achado.get());
                    pareada[i] = true;
                    pares.add(new Par(o, achado.get(),
                            achado.get().valor().subtract(o.valor()), exigindoIdentificador));
                }
            }
        }
        for (int i = 0; i < obrigacoes.size(); i++) {
            if (!pareada[i]) {
                semComprovante.add(obrigacoes.get(i));
            }
        }
        return new Resultado(pares, semComprovante, disponiveis);
    }

    private Optional<ComprovanteDePagamento> procurar(Obrigacao o,
                                                      List<ComprovanteDePagamento> candidatos,
                                                      boolean exigindoIdentificador) {
        for (ComprovanteDePagamento c : candidatos) {
            if (!tolerancia.absorve(o.valor(), c.valor())) {
                continue;
            }
            if (exigindoIdentificador) {
                if (mesmoIdentificador(o, c)) {
                    return Optional.of(c);
                }
            } else if (pagoAteOVencimento(o, c)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /**
     * Compara identificadores por dígitos.
     *
     * <p>O mesmo identificador aparece com e sem separadores conforme o
     * emissor: {@code 0126071549847969-2} na guia e {@code 01260715498479692}
     * no extrato. Comparar como texto não casaria.
     */
    static boolean mesmoIdentificador(Obrigacao o, ComprovanteDePagamento c) {
        String a = digitos(o.identificador());
        String b = digitos(c.identificador());
        return !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a));
    }

    private boolean pagoAteOVencimento(Obrigacao o, ComprovanteDePagamento c) {
        if (o.vencimento() == null || c.data() == null) {
            return false;
        }
        LocalDate limite = o.vencimento().plusDays(tolerancia.carenciaEmDias());
        return !c.data().isAfter(limite);
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /**
     * @param pares            o que pareou
     * @param semComprovante   obrigações sem pagamento identificado
     * @param semObrigacao     comprovantes que não pareiam com nada — pagamento
     *                         de obrigação não cadastrada, ou de outra competência
     */
    public record Resultado(List<Par> pares, List<Obrigacao> semComprovante,
                            List<ComprovanteDePagamento> semObrigacao) {

        public Resultado {
            pares = List.copyOf(pares);
            semComprovante = List.copyOf(semComprovante);
            semObrigacao = List.copyOf(semObrigacao);
        }

        public boolean completo() {
            return semComprovante.isEmpty();
        }

        /** Os itens comparados, para o registro da conciliação. */
        public Map<String, List<String>> itens() {
            Map<String, List<String>> itens = new LinkedHashMap<>();
            itens.put("pareados", pares.stream().map(p -> p.obrigacao().tipo()
                    + " " + p.obrigacao().valor() + " ↔ comprovante " + p.comprovante().valor()
                    + " em " + p.comprovante().data()
                    + (p.porIdentificador() ? " (identificador)" : " (valor e data)")).toList());
            if (!semComprovante.isEmpty()) {
                itens.put("sem_comprovante", semComprovante.stream()
                        .map(o -> o.tipo() + " " + o.valor() + " vencendo " + o.vencimento())
                        .toList());
            }
            if (!semObrigacao.isEmpty()) {
                itens.put("sem_obrigacao", semObrigacao.stream()
                        .map(c -> c.familia() + " " + c.valor() + " em " + c.data()).toList());
            }
            return itens;
        }
    }
}
