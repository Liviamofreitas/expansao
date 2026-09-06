package br.com.engesoftware.sgdf.conciliacao;

import br.com.engesoftware.sgdf.documento.ItemDaFolha;
import br.com.engesoftware.sgdf.matriz.Alocacao;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R05 — alocados × folha × contracheque, com janela pró-rata (F2-04).
 *
 * <p>Cap. 9: <i>"conjunto(matrículas em OPE.RELACAO_ALOCADOS) ⊆ conjunto(matrículas
 * na folha) e cada uma tem FOL.CONTRACHEQUE vinculado. Exceções:
 * admitidos/desligados no mês (janela pró-rata)"</i>.
 *
 * <p><b>Comparar conjuntos é o erro; comparar períodos é a regra.</b> Uma
 * diferença de conjuntos acusa divergência a cada admissão e a cada
 * desligamento — num contrato de 42 pessoas com rotatividade normal, vários por
 * mês, todo mês. É o risco P01 ("falso positivo em massa → abandono") na sua
 * forma mais previsível. O que decide não é estar nos dois conjuntos: é a
 * <b>alocação intersetar a competência</b>, o mesmo critério do achado E-10 que
 * a materialização já usa.
 *
 * <p><b>E a regra é assimétrica.</b> As duas faltas não são a mesma coisa:
 *
 * <ul>
 *   <li><b>Alocado o mês inteiro e ausente da folha</b> — alguém trabalhou e não
 *       foi pago, ou a folha do contrato está incompleta. Divergência.</li>
 *   <li><b>Na folha e sem alocação nenhuma</b> — alguém foi pago por este
 *       contrato sem estar nele. É a mais grave das duas: a diferença de
 *       conjuntos costuma ser lida como "sobrou um nome", e o que ela pode
 *       significar é custo alocado ao contrato errado.</li>
 *   <li><b>Alocado por janela parcial e ausente da folha</b> — admitido no fim
 *       ou desligado no início. Pode entrar na folha seguinte, e por isso é
 *       <b>ressalva</b>, não divergência: a regra diz o que observou e não
 *       trava o faturamento por um caso que se resolve sozinho.</li>
 * </ul>
 */
public final class R05AlocadosNaFolha implements RegraDeConciliacao {

    public static final String CODIGO = "R05";

    private final ResultadoDaConciliacao.Modo modoCadastrado;

    public R05AlocadosNaFolha(ResultadoDaConciliacao.Modo modoCadastrado) {
        this.modoCadastrado = modoCadastrado;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado,
                tolerancia);

        if (ciclo.folha() == null || ciclo.alocacoes().isEmpty()) {
            return new ResultadoDaConciliacao(CODIGO,
                    ResultadoDaConciliacao.Situacao.NAO_APLICAVEL, modo, null, null, null,
                    "sem folha ou sem alocação cadastrada: não há o que comparar",
                    Map.of());
        }
        // A R05 é sobre a equipe DE UM CONTRATO. Rodá-la sobre a folha da
        // empresa acusaria todo colaborador dos outros contratos como "na folha
        // sem alocação" — o mesmo erro de população que a R09 sofreria.
        if (ciclo.escopoDaFolha() != DadosDoCiclo.EscopoDaFolha.CONTRATO) {
            return new ResultadoDaConciliacao(CODIGO,
                    ResultadoDaConciliacao.Situacao.NAO_APLICAVEL, modo, null, null, null,
                    "a folha do ciclo é da empresa inteira; a R05 compara a equipe de um "
                            + "contrato e precisa do recorte por centro de custo",
                    Map.of());
        }

        YearMonth competencia = competenciaDe(ciclo);
        LocalDate inicio = competencia.atDay(1);
        LocalDate fim = competencia.atEndOfMonth();

        Set<String> naFolha = new LinkedHashSet<>();
        for (ItemDaFolha item : ciclo.folha().itens()) {
            naFolha.add(item.matricula());
        }

        List<String> semPagamento = new ArrayList<>();
        List<String> semContracheque = new ArrayList<>();
        List<String> ressalvas = new ArrayList<>();
        Set<String> alocadosNoMes = new LinkedHashSet<>();

        for (Alocacao a : ciclo.alocacoes()) {
            if (!a.ativoEm(inicio, fim)) {
                continue;
            }
            alocadosNoMes.add(a.matricula());
            boolean mesInteiro = cobreOMesInteiro(a, inicio, fim);

            if (!naFolha.contains(a.matricula())) {
                if (mesInteiro) {
                    semPagamento.add(a.matricula());
                } else {
                    ressalvas.add(a.matricula() + ": alocação parcial ("
                            + janela(a, inicio, fim) + ") e ausente da folha — pode entrar "
                            + "na competência seguinte");
                }
                continue;
            }
            if (!ciclo.comContracheque().contains(a.matricula())) {
                semContracheque.add(a.matricula());
            }
        }

        List<String> pagosSemAlocacao = new ArrayList<>();
        for (String matricula : naFolha) {
            if (!alocadosNoMes.contains(matricula)) {
                pagosSemAlocacao.add(matricula);
            }
        }

        Map<String, List<String>> itens = new LinkedHashMap<>();
        itens.put("alocados_no_mes", List.copyOf(alocadosNoMes));
        itens.put("na_folha", List.copyOf(naFolha));
        if (!semPagamento.isEmpty()) {
            itens.put("alocado_sem_pagamento", semPagamento);
        }
        if (!semContracheque.isEmpty()) {
            itens.put("sem_contracheque_vinculado", semContracheque);
        }
        if (!pagosSemAlocacao.isEmpty()) {
            itens.put("na_folha_sem_alocacao", pagosSemAlocacao);
        }
        if (!ressalvas.isEmpty()) {
            itens.put("janela_pro_rata", ressalvas);
        }

        boolean divergente = !semPagamento.isEmpty() || !semContracheque.isEmpty()
                || !pagosSemAlocacao.isEmpty();
        if (!divergente) {
            // Conforme COM ressalva continua conforme: a ressalva é observação,
            // não pendência. Escondê-la seria pior — quem confere precisa saber
            // que houve movimentação no mês.
            return new ResultadoDaConciliacao(CODIGO,
                    ResultadoDaConciliacao.Situacao.CONFORME, modo,
                    java.math.BigDecimal.valueOf(alocadosNoMes.size()),
                    java.math.BigDecimal.valueOf(naFolha.size()),
                    java.math.BigDecimal.valueOf(naFolha.size() - alocadosNoMes.size()),
                    ressalvas.isEmpty() ? null
                            : alocadosNoMes.size() + " alocados, " + ressalvas.size()
                                    + " com janela parcial no mês",
                    itens);
        }

        return new ResultadoDaConciliacao(CODIGO,
                ResultadoDaConciliacao.Situacao.DIVERGENTE, modo,
                java.math.BigDecimal.valueOf(alocadosNoMes.size()),
                java.math.BigDecimal.valueOf(naFolha.size()),
                java.math.BigDecimal.valueOf(naFolha.size() - alocadosNoMes.size()),
                mensagem(semPagamento, semContracheque, pagosSemAlocacao), itens);
    }

    /**
     * A mensagem separa as três faltas porque quem age em cada uma é outro.
     *
     * <p>"5 divergências" manda todo mundo procurar a mesma coisa; nomear cada
     * grupo diz à AP o que cobrar e ao gestor o que conferir.
     */
    private static String mensagem(List<String> semPagamento, List<String> semContracheque,
                                   List<String> pagosSemAlocacao) {
        List<String> partes = new ArrayList<>();
        if (!pagosSemAlocacao.isEmpty()) {
            partes.add(pagosSemAlocacao.size() + " na folha do contrato SEM alocação — "
                    + "conferir se o custo está no contrato certo");
        }
        if (!semPagamento.isEmpty()) {
            partes.add(semPagamento.size() + " alocado(s) o mês inteiro e ausente(s) da folha");
        }
        if (!semContracheque.isEmpty()) {
            partes.add(semContracheque.size() + " na folha sem contracheque vinculado");
        }
        return String.join("; ", partes);
    }

    /** Cobre o mês inteiro quando começou antes e não terminou dentro dele. */
    static boolean cobreOMesInteiro(Alocacao a, LocalDate inicio, LocalDate fim) {
        return !a.inicio().isAfter(inicio) && (a.fim() == null || !a.fim().isBefore(fim));
    }

    private static String janela(Alocacao a, LocalDate inicio, LocalDate fim) {
        LocalDate de = a.inicio().isAfter(inicio) ? a.inicio() : inicio;
        LocalDate ate = a.fim() != null && a.fim().isBefore(fim) ? a.fim() : fim;
        return de + " a " + ate;
    }

    static YearMonth competenciaDe(DadosDoCiclo ciclo) {
        String bruta = ciclo.competencia();
        if (bruta != null && bruta.matches("\\d{2}/\\d{4}")) {
            return YearMonth.of(Integer.parseInt(bruta.substring(3)),
                    Integer.parseInt(bruta.substring(0, 2)));
        }
        return YearMonth.parse(bruta);
    }
}
