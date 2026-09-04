package br.com.engesoftware.sgdf.conciliacao;

import br.com.engesoftware.sgdf.documento.BeneficiarioDaRelacao;
import br.com.engesoftware.sgdf.documento.FolhaDeCompetencia;
import br.com.engesoftware.sgdf.documento.ItemDaFolha;
import br.com.engesoftware.sgdf.documento.RelacaoDeBeneficio;
import br.com.engesoftware.sgdf.documento.Rubrica;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Testes do motor de conciliacao (fase 1b).
 *
 * <p>Os numeros vem dos documentos reais de 06 e 07/2026: a guia do FGTS de
 * R$ 119.301,51, o PIX de mesmo valor e data, e a divergencia de R$ 144,00 do
 * vale-alimentacao no contrato DOCAS.
 */
public final class TestesDeConciliacao {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    static final Tolerancia UM_CENTAVO = Tolerancia.deCentavos(1);
    static final Tolerancia MEIO_PORCENTO = Tolerancia.dePercentual("0.005");

    public static void main(String[] args) {
        executar("tolerancia", TestesDeConciliacao::tolerancia);
        executar("semToleranciaNaoBloqueia", TestesDeConciliacao::semToleranciaNaoBloqueia);
        executar("pareamentoPorValorEData", TestesDeConciliacao::pareamentoPorValorEData);
        executar("pareamentoPorIdentificadorVemPrimeiro",
                TestesDeConciliacao::pareamentoPorIdentificadorVemPrimeiro);
        executar("comprovanteNaoPareiaDuasVezes",
                TestesDeConciliacao::comprovanteNaoPareiaDuasVezes);
        executar("r01SemComprovanteDiverge", TestesDeConciliacao::r01SemComprovanteDiverge);
        executar("r09RecusaFolhaRecortada", TestesDeConciliacao::r09RecusaFolhaRecortada);
        executar("r09ComFolhaDaEmpresa", TestesDeConciliacao::r09ComFolhaDaEmpresa);
        executar("r08ComparaCustoTotalNaoDesconto",
                TestesDeConciliacao::r08ComparaCustoTotalNaoDesconto);
        executar("r08AcusaQuemSoEstaDeUmLado", TestesDeConciliacao::r08AcusaQuemSoEstaDeUmLado);
        executar("resultadoSemMotivoNaoEAceito", TestesDeConciliacao::resultadoSemMotivoNaoEAceito);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    /**
     * A conciliacao real do FGTS deu delta de R$ 0,01 sobre R$ 49.693,18, por
     * arredondamento por colaborador. Com o dobro de colaboradores o erro
     * dobra: a tolerancia dessa regra tem de ser percentual.
     */
    static void tolerancia() {
        ok("Tol . a absoluta absorve o centavo",
                UM_CENTAVO.absorve(new BigDecimal("3975.45"), new BigDecimal("3975.44")));
        ok("Tol . e nao absorve dois centavos",
                !UM_CENTAVO.absorve(new BigDecimal("3975.45"), new BigDecimal("3975.43")));
        ok("Tol . a percentual cresce com o valor",
                MEIO_PORCENTO.absorve(new BigDecimal("100000.00"), new BigDecimal("99600.00")));
        ok("Tol . e recusa alem da margem",
                !MEIO_PORCENTO.absorve(new BigDecimal("100000.00"), new BigDecimal("99000.00")));
        ok("Tol . sem cadastro, nada e absorvido",
                !Tolerancia.NENHUMA.absorve(BigDecimal.ONE, BigDecimal.ONE.add(
                        new BigDecimal("0.01"))));
        ok("Tol . mas valor igual passa mesmo sem tolerancia",
                Tolerancia.NENHUMA.absorve(BigDecimal.ONE, BigDecimal.ONE));
        ok("Tol . a descricao diz a margem aplicada",
                MEIO_PORCENTO.descricao().contains("0.5"));
    }

    /** Cap. 9: regra sem tolerancia cadastrada opera em ALERTA e nunca bloqueia. */
    static void semToleranciaNaoBloqueia() {
        ResultadoDaConciliacao r = new R01GuiaComComprovante("FGT.GUIA",
                ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(cicloComGuiaSemComprovante(), Tolerancia.NENHUMA);

        ok("Cap. 9 . sem tolerancia o modo vira ALERTA",
                r.modo() == ResultadoDaConciliacao.Modo.ALERTA);
        ok("Cap. 9 . e a divergencia nao bloqueia o faturamento",
                r.resultado() == ResultadoDaConciliacao.Situacao.DIVERGENTE && !r.bloqueia());

        ResultadoDaConciliacao comTol = new R01GuiaComComprovante("FGT.GUIA",
                ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(cicloComGuiaSemComprovante(), UM_CENTAVO);
        ok("Cap. 9 . com tolerancia cadastrada o BLOQUEIO vale", comTol.bloqueia());
    }

    /**
     * O comprovante real do FGTS e um PIX: nao carrega o identificador da guia,
     * so valor e data. Exigir o identificador reprovaria um pagamento legitimo.
     */
    static void pareamentoPorValorEData() {
        Obrigacao guia = new Obrigacao("FGT.GUIA", "06/2026", new BigDecimal("119301.51"),
                LocalDate.of(2026, 7, 20), "0126071549847969-2");
        ComprovanteDePagamento pix = new ComprovanteDePagamento("CMP.TRANSFERENCIA",
                new BigDecimal("119301.51"), LocalDate.of(2026, 7, 20),
                "E37395399202607201500YUapTyZbF8L", "CEF MATRIZ");

        Pareamento.Resultado r = new Pareamento(UM_CENTAVO)
                .parear(List.of(guia), List.of(pix));

        ok("R01 . o PIX sem identificador da guia pareia por valor e data", r.completo());
        ok("R01 . e o pareamento e marcado como fraco",
                !r.pares().get(0).porIdentificador());

        // Pago depois do vencimento, sem carencia: nao pareia.
        ComprovanteDePagamento atrasado = new ComprovanteDePagamento("CMP.TRANSFERENCIA",
                new BigDecimal("119301.51"), LocalDate.of(2026, 7, 21), "x", "CEF");
        ok("R01 . pagamento depois do vencimento nao pareia sem carencia",
                !new Pareamento(UM_CENTAVO).parear(List.of(guia), List.of(atrasado)).completo());
        ok("R01 . com carencia cadastrada, pareia",
                new Pareamento(UM_CENTAVO.comCarencia(1))
                        .parear(List.of(guia), List.of(atrasado)).completo());
    }

    /**
     * O pareamento por identificador e certo; o por valor e data e inferencia.
     * Fazer na ordem inversa deixaria a inferencia consumir o comprovante que
     * casaria exatamente com outra obrigacao.
     */
    static void pareamentoPorIdentificadorVemPrimeiro() {
        Obrigacao a = new Obrigacao("INS.DARF", "06/2026", new BigDecimal("1000.00"),
                LocalDate.of(2026, 7, 20), "111");
        Obrigacao b = new Obrigacao("INS.DARF", "06/2026", new BigDecimal("1000.00"),
                LocalDate.of(2026, 7, 20), "222");
        ComprovanteDePagamento semId = new ComprovanteDePagamento("CMP.DARF",
                new BigDecimal("1000.00"), LocalDate.of(2026, 7, 18), null, "RFB");
        ComprovanteDePagamento comId = new ComprovanteDePagamento("CMP.DARF",
                new BigDecimal("1000.00"), LocalDate.of(2026, 7, 18), "222", "RFB");

        Pareamento.Resultado r = new Pareamento(UM_CENTAVO)
                .parear(List.of(a, b), List.of(semId, comId));

        ok("R01 . as duas obrigacoes pareiam", r.completo());
        Pareamento.Par parB = r.pares().stream()
                .filter(p -> "222".equals(p.obrigacao().identificador())).findFirst().orElseThrow();
        ok("R01 . o comprovante com identificador vai para a obrigacao dele",
                parB.porIdentificador() && "222".equals(parB.comprovante().identificador()));
    }

    /** Dois pagamentos iguais sao dois pagamentos: reusar um esconderia uma divida. */
    static void comprovanteNaoPareiaDuasVezes() {
        Obrigacao a = new Obrigacao("INS.DARF", "06/2026", new BigDecimal("500.00"),
                LocalDate.of(2026, 7, 20), null);
        Obrigacao b = new Obrigacao("INS.DARF", "06/2026", new BigDecimal("500.00"),
                LocalDate.of(2026, 7, 20), null);
        ComprovanteDePagamento unico = new ComprovanteDePagamento("CMP.DARF",
                new BigDecimal("500.00"), LocalDate.of(2026, 7, 15), null, "RFB");

        Pareamento.Resultado r = new Pareamento(UM_CENTAVO)
                .parear(List.of(a, b), List.of(unico));

        ok("R01 . um comprovante pareia com uma obrigacao so", r.pares().size() == 1);
        ok("R01 . e a outra fica registrada como sem comprovante",
                r.semComprovante().size() == 1);
    }

    static void r01SemComprovanteDiverge() {
        ResultadoDaConciliacao r = new R01GuiaComComprovante("FGT.GUIA",
                ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(cicloComGuiaSemComprovante(), UM_CENTAVO);

        ok("R01 . guia sem comprovante diverge",
                r.resultado() == ResultadoDaConciliacao.Situacao.DIVERGENTE);
        ok("R01 . a mensagem diz o valor e o vencimento do que falta",
                r.mensagem().contains("119301.51") && r.mensagem().contains("2026-07-20"));
        ok("R01 . e a tolerancia aplicada",
                r.mensagem().contains("0.01"));
        ok("R01 . ciclo sem guia nenhuma nao se aplica",
                new R01GuiaComComprovante("FGT.GUIA", ResultadoDaConciliacao.Modo.BLOQUEIO)
                        .executar(DadosDoCiclo.de("06/2026").construir(), UM_CENTAVO)
                        .resultado() == ResultadoDaConciliacao.Situacao.NAO_APLICAVEL);
    }

    /**
     * A guia do FGTS e da EMPRESA inteira; a folha do ciclo pode ser o recorte
     * de um contrato. Comparar acusaria divergencia de dezenas de milhares de
     * reais com os dois documentos corretos.
     */
    static void r09RecusaFolhaRecortada() {
        DadosDoCiclo recorte = DadosDoCiclo.de("06/2026")
                .noCentroDeCusto("104501")
                .comFolhaDoContrato(folhaCom(new BigDecimal("49693.18")))
                .comObrigacao(guia())
                .construir();

        ResultadoDaConciliacao r = new R09BaseFgtsComGuia(ResultadoDaConciliacao.Modo.ALERTA)
                .executar(recorte, MEIO_PORCENTO);

        ok("R09 . folha recortada por centro de custo nao e comparada com a guia",
                r.resultado() == ResultadoDaConciliacao.Situacao.NAO_APLICAVEL);
        ok("R09 . e o motivo explica que as populacoes sao diferentes",
                r.mensagem().contains("empresa inteira") && r.mensagem().contains("104501"));
    }

    static void r09ComFolhaDaEmpresa() {
        // 8% de 1.491.268,88 = 119.301,51 — a guia real.
        DadosDoCiclo empresa = DadosDoCiclo.de("06/2026")
                .comFolhaDaEmpresa(folhaCom(new BigDecimal("1491268.88")))
                .comObrigacao(guia())
                .construir();

        ResultadoDaConciliacao r = new R09BaseFgtsComGuia(ResultadoDaConciliacao.Modo.ALERTA)
                .executar(empresa, MEIO_PORCENTO);
        ok("R09 . com a folha da empresa, a guia confere", r.conforme());

        // Um centavo de arredondamento por colaborador: a percentual absorve.
        DadosDoCiclo comCentavo = DadosDoCiclo.de("06/2026")
                .comFolhaDaEmpresa(folhaCom(new BigDecimal("1491268.75")))
                .comObrigacao(guia())
                .construir();
        ok("R09 . e o arredondamento por colaborador e absorvido",
                new R09BaseFgtsComGuia(ResultadoDaConciliacao.Modo.ALERTA)
                        .executar(comCentavo, MEIO_PORCENTO).conforme());
    }

    /**
     * O desconto na folha e a coparticipacao do empregado; o credito da relacao
     * e o valor cheio. Comparados entre si acusariam divergencia em todos.
     */
    static void r08ComparaCustoTotalNaoDesconto() {
        DadosDoCiclo ciclo = cicloDeBeneficio(
                new BigDecimal("604.80"), new BigDecimal("604.80"), new BigDecimal("6.05"));

        ResultadoDaConciliacao r = new R08BeneficioComRelacao("R08", "BEN.RELACAO_VA_VR",
                "17300", ResultadoDaConciliacao.Modo.ALERTA).executar(ciclo, UM_CENTAVO);

        ok("R08 . custo total igual ao credito da relacao e conforme", r.conforme());
        ok("R08 . e o desconto de 6,05 nao entra na comparacao",
                r.esperado().compareTo(new BigDecimal("604.80")) == 0);

        // O caso real: licenca paternidade reduziu o VA na folha.
        ResultadoDaConciliacao divergente = new R08BeneficioComRelacao("R08",
                "BEN.RELACAO_VA_VR", "17300", ResultadoDaConciliacao.Modo.ALERTA)
                .executar(cicloDeBeneficio(new BigDecimal("460.80"), new BigDecimal("604.80"),
                        new BigDecimal("4.61")), UM_CENTAVO);
        ok("R08 . credito maior que o custo da folha diverge",
                divergente.resultado() == ResultadoDaConciliacao.Situacao.DIVERGENTE);
        ok("R08 . e a mensagem traz a matricula e os dois valores",
                divergente.mensagem().contains("100787")
                        && divergente.mensagem().contains("604.80")
                        && divergente.mensagem().contains("460.80"));
        ok("R08 . e a diferenca", divergente.mensagem().contains("144.00"));
    }

    static void r08AcusaQuemSoEstaDeUmLado() {
        // Na relacao, ausente da folha.
        FolhaDeCompetencia vazia = new FolhaDeCompetencia("06/2026", List.of());
        DadosDoCiclo soRelacao = DadosDoCiclo.de("06/2026")
                .comFolhaDoContrato(vazia)
                .comRelacao("BEN.RELACAO_VA_VR", new RelacaoDeBeneficio(
                        List.of(new BeneficiarioDaRelacao("100787", null, "FULANO",
                                new BigDecimal("604.80"))), new BigDecimal("604.80")))
                .construir();
        ResultadoDaConciliacao r = new R08BeneficioComRelacao("R08", "BEN.RELACAO_VA_VR",
                "17300", ResultadoDaConciliacao.Modo.ALERTA).executar(soRelacao, UM_CENTAVO);
        ok("R08 . quem recebeu e nao esta na folha e acusado",
                r.mensagem().contains("não está na folha"));

        // Na folha com custo, ausente da relacao.
        DadosDoCiclo soFolha = DadosDoCiclo.de("06/2026")
                .comFolhaDoContrato(folhaComCusto("000100787", new BigDecimal("604.80"),
                        new BigDecimal("6.05")))
                .comRelacao("BEN.RELACAO_VA_VR",
                        new RelacaoDeBeneficio(List.of(), BigDecimal.ZERO))
                .construir();
        ResultadoDaConciliacao r2 = new R08BeneficioComRelacao("R08", "BEN.RELACAO_VA_VR",
                "17300", ResultadoDaConciliacao.Modo.ALERTA).executar(soFolha, UM_CENTAVO);
        ok("R08 . quem tem custo na folha e nao consta na relacao tambem",
                r2.mensagem().contains("não consta na relação"));

        // A relacao que nao fecha consigo mesma nao e confrontada com a folha.
        DadosDoCiclo naoFecha = DadosDoCiclo.de("06/2026")
                .comFolhaDoContrato(vazia)
                .comRelacao("BEN.RELACAO_VA_VR", new RelacaoDeBeneficio(
                        List.of(new BeneficiarioDaRelacao("1", null, "X", BigDecimal.ONE)),
                        new BigDecimal("99.00")))
                .construir();
        ok("R08 . relacao que nao fecha consigo e recusada antes da comparacao",
                new R08BeneficioComRelacao("R08", "BEN.RELACAO_VA_VR", "17300",
                        ResultadoDaConciliacao.Modo.ALERTA).executar(naoFecha, UM_CENTAVO)
                        .mensagem().contains("não fecha consigo"));
    }

    static void resultadoSemMotivoNaoEAceito() {
        ok("Cap. 12 . divergencia sem mensagem nao e construida",
                recusa(() -> new ResultadoDaConciliacao("R01",
                        ResultadoDaConciliacao.Situacao.DIVERGENTE,
                        ResultadoDaConciliacao.Modo.ALERTA, null, null, null, null, Map.of())));
        ok("Cap. 12 . nem 'nao aplicavel' sem motivo",
                recusa(() -> new ResultadoDaConciliacao("R09",
                        ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                        ResultadoDaConciliacao.Modo.ALERTA, null, null, null, "  ", Map.of())));
    }

    // -------------------------------------------------------------------------

    static Obrigacao guia() {
        return new Obrigacao("FGT.GUIA", "06/2026", new BigDecimal("119301.51"),
                LocalDate.of(2026, 7, 20), "0126071549847969-2");
    }

    static DadosDoCiclo cicloComGuiaSemComprovante() {
        return DadosDoCiclo.de("06/2026").comObrigacao(guia()).construir();
    }

    static FolhaDeCompetencia folhaCom(BigDecimal baseFgts) {
        ItemDaFolha i = new ItemDaFolha("000000001", "FULANO", null, "06/2026",
                List.of(), List.of(), null, null, null, baseFgts, null, null, null);
        return new FolhaDeCompetencia("06/2026", List.of(i));
    }

    static FolhaDeCompetencia folhaComCusto(String matricula, BigDecimal custoTotal,
                                            BigDecimal desconto) {
        ItemDaFolha i = new ItemDaFolha(matricula, "SIDHARTHA BEZERRA DE SOUZA", null, "06/2026",
                List.of(), List.of(new Rubrica("08305", "VALE ALIMENTACAO", null, desconto)),
                null, null, null, null, null, null, null,
                Map.of("17300", custoTotal), "104501");
        return new FolhaDeCompetencia("06/2026", List.of(i));
    }

    static DadosDoCiclo cicloDeBeneficio(BigDecimal custoNaFolha, BigDecimal creditoNaRelacao,
                                         BigDecimal desconto) {
        return DadosDoCiclo.de("06/2026")
                .noCentroDeCusto("104501")
                .comFolhaDoContrato(folhaComCusto("000100787", custoNaFolha, desconto))
                .comRelacao("BEN.RELACAO_VA_VR", new RelacaoDeBeneficio(
                        List.of(new BeneficiarioDaRelacao("100787", null,
                                "SIDHARTHA BEZERRA DE SOUZA", creditoNaRelacao)),
                        creditoNaRelacao))
                .construir();
    }

    interface Teste {
        void executar() throws Exception;
    }

    static void executar(String nome, Teste teste) {
        try {
            teste.executar();
        } catch (Exception | AssertionError e) {
            falhas.add(nome + " lancou " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    static boolean recusa(Runnable acao) {
        try {
            acao.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    private TestesDeConciliacao() {}
}
