package br.com.engesoftware.sgdf.conciliacao;

import br.com.engesoftware.sgdf.documento.BeneficiarioDaRelacao;
import br.com.engesoftware.sgdf.documento.FolhaDeCompetencia;
import br.com.engesoftware.sgdf.matriz.Alocacao;
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
        executar("r02SeparaApuracaoDePagamento", TestesDeConciliacao::r02SeparaApuracaoDePagamento);
        executar("r03OlhaOConjuntoDeCertidoes", TestesDeConciliacao::r03OlhaOConjuntoDeCertidoes);
        executar("r04ElevaV6AoCiclo", TestesDeConciliacao::r04ElevaV6AoCiclo);
        executar("r05AdmitidoNoMeioDoMesNaoEFalsoPositivo",
                TestesDeConciliacao::r05AdmitidoNoMeioDoMesNaoEFalsoPositivo);
        executar("r05AssimetriaDasDuasFaltas",
                TestesDeConciliacao::r05AssimetriaDasDuasFaltas);
        executar("r05RecusaFolhaDaEmpresa", TestesDeConciliacao::r05RecusaFolhaDaEmpresa);
        executar("r06IncompletoNoPrazoNaoDiverge",
                TestesDeConciliacao::r06IncompletoNoPrazoNaoDiverge);
        executar("r06ACarenciaDoAsoNaoEscondeOutroAtraso",
                TestesDeConciliacao::r06ACarenciaDoAsoNaoEscondeOutroAtraso);
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

    /**
     * R02 encadeia duas conferencias, e a mensagem tem de distinguir: declarar
     * R$ 100 e emitir R$ 90 em DARF e erro de apuracao; emitir R$ 100 e pagar
     * R$ 90 e inadimplencia. Quem resolve cada uma e uma area diferente.
     */
    static void r02SeparaApuracaoDePagamento() {
        Obrigacao dctf = new Obrigacao("INS.DCTFWEB", "06/2026", new BigDecimal("1000.00"),
                LocalDate.of(2026, 7, 20), "07.16.26196.1378103-1");
        Obrigacao darf = new Obrigacao("INS.DARF", "06/2026", new BigDecimal("1000.00"),
                LocalDate.of(2026, 7, 20), "111");
        ComprovanteDePagamento pago = new ComprovanteDePagamento("CMP.DARF",
                new BigDecimal("1000.00"), LocalDate.of(2026, 7, 18), "111", "RFB");

        ok("R02 . declarado igual ao emitido e pago e conforme",
                new R02DctfwebComDarf(ResultadoDaConciliacao.Modo.BLOQUEIO)
                        .executar(DadosDoCiclo.de("06/2026").comObrigacao(dctf)
                                .comObrigacao(darf).comComprovante(pago).construir(),
                                UM_CENTAVO).conforme());

        // Erro de apuracao: o DARF emitido nao cobre o declarado.
        Obrigacao menor = new Obrigacao("INS.DARF", "06/2026", new BigDecimal("900.00"),
                LocalDate.of(2026, 7, 20), "111");
        ComprovanteDePagamento pagoMenor = new ComprovanteDePagamento("CMP.DARF",
                new BigDecimal("900.00"), LocalDate.of(2026, 7, 18), "111", "RFB");
        ResultadoDaConciliacao apuracao = new R02DctfwebComDarf(
                ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(DadosDoCiclo.de("06/2026").comObrigacao(dctf).comObrigacao(menor)
                        .comComprovante(pagoMenor).construir(), UM_CENTAVO);
        ok("R02 . DARF menor que o declarado e erro de apuracao",
                apuracao.mensagem().startsWith("apuração"));
        ok("R02 . e nao de pagamento", !apuracao.mensagem().contains("pagamento:"));

        // Inadimplencia: o DARF cobre o declarado, mas nao foi pago.
        ResultadoDaConciliacao pagamento = new R02DctfwebComDarf(
                ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(DadosDoCiclo.de("06/2026").comObrigacao(dctf).comObrigacao(darf)
                        .construir(), UM_CENTAVO);
        ok("R02 . DARF sem comprovante e problema de pagamento",
                pagamento.mensagem().contains("pagamento:"));
        ok("R02 . e a apuracao nao e acusada junto",
                !pagamento.mensagem().startsWith("apuração"));

        ok("R02 . ciclo sem DCTFWeb nao se aplica",
                new R02DctfwebComDarf(ResultadoDaConciliacao.Modo.BLOQUEIO)
                        .executar(DadosDoCiclo.de("06/2026").construir(), UM_CENTAVO)
                        .resultado() == ResultadoDaConciliacao.Situacao.NAO_APLICAVEL);
    }

    /**
     * R03 e V5 elevada ao conjunto, e o conjunto tem uma propriedade que
     * nenhuma certidao sozinha tem: a primeira a vencer manda.
     */
    static void r03OlhaOConjuntoDeCertidoes() {
        LocalDate nf = LocalDate.of(2026, 8, 10);
        var rfb = new R03CertidoesVigentes.CertidaoDoCiclo("CER.CND_RFB",
                LocalDate.of(2026, 10, 24),
                br.com.engesoftware.sgdf.validacao.NaturezaDaCertidao.POSITIVA_COM_EFEITO_NEGATIVA,
                false);
        var crf = new R03CertidoesVigentes.CertidaoDoCiclo("CER.CRF_FGTS",
                LocalDate.of(2026, 8, 9),
                br.com.engesoftware.sgdf.validacao.NaturezaDaCertidao.NEGATIVA, false);
        var cndt = new R03CertidoesVigentes.CertidaoDoCiclo("CER.CNDT",
                LocalDate.of(2026, 11, 25),
                br.com.engesoftware.sgdf.validacao.NaturezaDaCertidao.NEGATIVA, false);

        DadosDoCiclo ciclo = DadosDoCiclo.de("06/2026").construir();

        ResultadoDaConciliacao conforme = new R03CertidoesVigentes(List.of(rfb, cndt), nf,
                ResultadoDaConciliacao.Modo.BLOQUEIO).executar(ciclo, UM_CENTAVO);
        ok("R03 . certidoes vigentes na data da NF sao conformes", conforme.conforme());
        ok("R03 . e o resultado diz qual vence primeiro",
                conforme.itens().get("primeira_a_vencer").get(0).contains("CER.CND_RFB"));

        ResultadoDaConciliacao vencida = new R03CertidoesVigentes(List.of(rfb, crf, cndt), nf,
                ResultadoDaConciliacao.Modo.BLOQUEIO).executar(ciclo, UM_CENTAVO);
        ok("R03 . uma certidao vencida faz o conjunto divergir",
                vencida.resultado() == ResultadoDaConciliacao.Situacao.DIVERGENTE);
        ok("R03 . e a mensagem nomeia quantas de quantas",
                vencida.mensagem().startsWith("1 de 3"));
        ok("R03 . nomeando qual", vencida.mensagem().contains("CER.CRF_FGTS"));

        // Achado A10: a certidao POSITIVA aceita por cadastro passa com ressalva.
        var civel = new R03CertidoesVigentes.CertidaoDoCiclo("CER.CND_CIVEL_CRIMINAL",
                LocalDate.of(2026, 11, 25),
                br.com.engesoftware.sgdf.validacao.NaturezaDaCertidao.POSITIVA, true);
        ResultadoDaConciliacao comRessalva = new R03CertidoesVigentes(List.of(civel), nf,
                ResultadoDaConciliacao.Modo.BLOQUEIO).executar(ciclo, UM_CENTAVO);
        ok("A10 . certidao positiva aceita por cadastro nao trava o ciclo",
                comRessalva.conforme());
        ok("A10 . e a ressalva sobe para o resultado da conciliacao",
                comRessalva.itens().containsKey("ressalvas"));
    }

    /** R04 e V6 elevada ao ciclo: distingue bloqueante de nao bloqueante. */
    static void r04ElevaV6AoCiclo() {
        DadosDoCiclo ciclo = DadosDoCiclo.de("06/2026").construir();

        var completa = new R04CompletudeDeFormatos.ExigenciaDoCiclo("OPE.MEDICAO",
                List.of("pdf", "xlsx"), Map.of("pdf", "06/2026", "xlsx", "06/2026"), true);
        var faltaBloqueante = new R04CompletudeDeFormatos.ExigenciaDoCiclo("FGT.GUIA",
                List.of("pdf", "xlsx"), Map.of("pdf", "06/2026"), true);
        var faltaLeve = new R04CompletudeDeFormatos.ExigenciaDoCiclo("CER.SICAF",
                List.of("pdf", "xlsx"), Map.of("pdf", "06/2026"), false);
        var incoerente = new R04CompletudeDeFormatos.ExigenciaDoCiclo("FOL.FOPAG",
                List.of("pdf", "xlsx"), Map.of("pdf", "06/2026", "xlsx", "05/2026"), true);

        ok("R04 . ciclo com tudo completo e conforme",
                new R04CompletudeDeFormatos(List.of(completa),
                        ResultadoDaConciliacao.Modo.BLOQUEIO).executar(ciclo, UM_CENTAVO)
                        .conforme());

        ResultadoDaConciliacao r = new R04CompletudeDeFormatos(
                List.of(completa, faltaBloqueante, faltaLeve, incoerente),
                ResultadoDaConciliacao.Modo.BLOQUEIO).executar(ciclo, UM_CENTAVO);

        ok("R04 . formato faltando faz o ciclo divergir",
                r.resultado() == ResultadoDaConciliacao.Situacao.DIVERGENTE);
        ok("R04 . a bloqueante e separada da nao bloqueante",
                r.itens().containsKey("bloqueantes_pendentes")
                        && r.itens().containsKey("nao_bloqueantes_pendentes"));
        ok("R04 . a mensagem destaca a bloqueante",
                r.mensagem().contains("BLOQUEANTE"));
        ok("R04 . e a incoerencia de competencia aparece separada",
                r.itens().containsKey("competencias_incoerentes"));
        ok("R04 . ciclo sem exigencia nenhuma nao se aplica",
                new R04CompletudeDeFormatos(List.of(), ResultadoDaConciliacao.Modo.BLOQUEIO)
                        .executar(ciclo, UM_CENTAVO).resultado()
                        == ResultadoDaConciliacao.Situacao.NAO_APLICAVEL);
    }

    // -------------------------------------------------------------------------

    static Obrigacao guia() {
        return new Obrigacao("FGT.GUIA", "06/2026", new BigDecimal("119301.51"),
                LocalDate.of(2026, 7, 20), "0126071549847969-2");
    }

    // =========================================================================
    // R05 e R06 — janelas pro-rata (F2-04)
    // =========================================================================

    /**
     * O CRITERIO DE ACEITE DA F2-04. Quem foi admitido no dia 20 esta na folha
     * com contracheque proporcional, e a regra tem de dizer CONFORME — sem
     * ressalva sobre valor, sem divergencia por "conjunto diferente".
     */
    static void r05AdmitidoNoMeioDoMesNaoEFalsoPositivo() {
        DadosDoCiclo ciclo = DadosDoCiclo.de("06/2026")
                .noCentroDeCusto("104501")
                .comFolhaDoContrato(folhaDeMatriculas("0001", "0002"))
                .comAlocacao(new Alocacao("0001", LocalDate.of(2024, 1, 1), null))
                .comAlocacao(new Alocacao("0002", LocalDate.of(2026, 6, 20), null))
                .comContrachequeDe("0001")
                .comContrachequeDe("0002")
                .construir();

        ResultadoDaConciliacao r = new R05AlocadosNaFolha(ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(ciclo, Tolerancia.deCentavos(1));

        ok("F2-04 . admitido no meio do mes, presente na folha, e CONFORME",
                r.conforme());
        ok("F2-04 . e nao aparece ressalva, porque ele esta na folha",
                !r.itens().containsKey("janela_pro_rata"));
        ok("F2-04 . os dois contam como alocados no mes",
                r.itens().get("alocados_no_mes").size() == 2);

        // O desligado no dia 3 que JA saiu da folha: janela parcial, ausencia
        // esperada — ressalva, nao divergencia.
        DadosDoCiclo comDesligado = DadosDoCiclo.de("06/2026")
                .noCentroDeCusto("104501")
                .comFolhaDoContrato(folhaDeMatriculas("0001"))
                .comAlocacao(new Alocacao("0001", LocalDate.of(2024, 1, 1), null))
                .comAlocacao(new Alocacao("0003", LocalDate.of(2024, 1, 1),
                        LocalDate.of(2026, 6, 3)))
                .comContrachequeDe("0001")
                .construir();
        ResultadoDaConciliacao comRessalva =
                new R05AlocadosNaFolha(ResultadoDaConciliacao.Modo.BLOQUEIO)
                        .executar(comDesligado, Tolerancia.deCentavos(1));

        ok("F2-04 . desligado no dia 3 e ausente da folha nao e divergencia",
                comRessalva.conforme());
        ok("F2-04 . mas vira ressalva, porque quem confere precisa ver a movimentacao",
                comRessalva.itens().get("janela_pro_rata").size() == 1);
        ok("F2-04 . e a ressalva diz a janela",
                comRessalva.itens().get("janela_pro_rata").get(0).contains("2026-06-03"));
    }

    /**
     * As duas faltas nao sao a mesma coisa. "Na folha sem alocacao" e a mais
     * grave: pode ser custo alocado ao contrato errado.
     */
    static void r05AssimetriaDasDuasFaltas() {
        DadosDoCiclo semPagamento = DadosDoCiclo.de("06/2026")
                .noCentroDeCusto("104501")
                .comFolhaDoContrato(folhaDeMatriculas("0001"))
                .comAlocacao(new Alocacao("0001", LocalDate.of(2024, 1, 1), null))
                .comAlocacao(new Alocacao("0002", LocalDate.of(2024, 1, 1), null))
                .comContrachequeDe("0001")
                .construir();
        ResultadoDaConciliacao r1 = new R05AlocadosNaFolha(ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(semPagamento, Tolerancia.deCentavos(1));

        ok("R05 . alocado o mes inteiro e ausente da folha e divergencia",
                !r1.conforme() && r1.itens().get("alocado_sem_pagamento").size() == 1);

        DadosDoCiclo pagoSemAlocacao = DadosDoCiclo.de("06/2026")
                .noCentroDeCusto("104501")
                .comFolhaDoContrato(folhaDeMatriculas("0001", "0009"))
                .comAlocacao(new Alocacao("0001", LocalDate.of(2024, 1, 1), null))
                .comContrachequeDe("0001")
                .comContrachequeDe("0009")
                .construir();
        ResultadoDaConciliacao r2 = new R05AlocadosNaFolha(ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(pagoSemAlocacao, Tolerancia.deCentavos(1));

        ok("R05 . na folha do contrato SEM alocacao e divergencia",
                !r2.conforme() && r2.itens().get("na_folha_sem_alocacao").size() == 1);
        ok("R05 . e a mensagem manda conferir o contrato, nao cobrar documento",
                r2.mensagem().contains("contrato certo"));

        DadosDoCiclo semContracheque = DadosDoCiclo.de("06/2026")
                .noCentroDeCusto("104501")
                .comFolhaDoContrato(folhaDeMatriculas("0001"))
                .comAlocacao(new Alocacao("0001", LocalDate.of(2024, 1, 1), null))
                .construir();
        ok("R05 . na folha mas sem contracheque vinculado tambem diverge",
                !new R05AlocadosNaFolha(ResultadoDaConciliacao.Modo.BLOQUEIO)
                        .executar(semContracheque, Tolerancia.deCentavos(1))
                        .conforme());
    }

    /** Mesmo erro de populacao que a R09 sofreria — ver DadosDoCiclo.EscopoDaFolha. */
    static void r05RecusaFolhaDaEmpresa() {
        DadosDoCiclo ciclo = DadosDoCiclo.de("06/2026")
                .comFolhaDaEmpresa(folhaDeMatriculas("0001", "9999"))
                .comAlocacao(new Alocacao("0001", LocalDate.of(2024, 1, 1), null))
                .comContrachequeDe("0001")
                .construir();
        ResultadoDaConciliacao r = new R05AlocadosNaFolha(ResultadoDaConciliacao.Modo.BLOQUEIO)
                .executar(ciclo, Tolerancia.deCentavos(1));

        ok("R05 . com a folha da empresa a regra se declara NAO APLICAVEL",
                r.resultado() == ResultadoDaConciliacao.Situacao.NAO_APLICAVEL);
        ok("R05 . em vez de acusar todo colaborador dos outros contratos",
                r.mensagem().contains("recorte por centro de custo"));
    }

    /**
     * Um conjunto de rescisao aberto ontem esta incompleto e nao devia acusar
     * nada: o prazo e do evento, e o evento acabou de acontecer.
     */
    static void r06IncompletoNoPrazoNaoDiverge() {
        DadosDoCiclo.ConjuntoDoEvento incompleto = new DadosDoCiclo.ConjuntoDoEvento(
                "0001", "RESCISAO", LocalDate.of(2026, 6, 30),
                List.of("RES.TRCT", "RES.ASO_DEMISSIONAL"), List.of("RES.TRCT"), true);

        ResultadoDaConciliacao noPrazo = new R06ConjuntoDoEvento(
                ResultadoDaConciliacao.Modo.BLOQUEIO, LocalDate.of(2026, 6, 25))
                .executar(DadosDoCiclo.de("06/2026").comEvento(incompleto).construir(),
                        Tolerancia.deCentavos(1));
        ok("R06 . conjunto incompleto ANTES do prazo nao diverge", noPrazo.conforme());
        ok("R06 . mas aparece como incompleto no prazo, para quem acompanha",
                noPrazo.itens().get("incompletos_no_prazo").size() == 1);

        ResultadoDaConciliacao vencido = new R06ConjuntoDoEvento(
                ResultadoDaConciliacao.Modo.BLOQUEIO, LocalDate.of(2026, 7, 5))
                .executar(DadosDoCiclo.de("06/2026").comEvento(incompleto).construir(),
                        Tolerancia.deCentavos(1));
        ok("R06 . depois do prazo, diverge", !vencido.conforme());
    }

    /**
     * A carencia de 10 dias e do ASO. Estende-la quando falta TAMBEM o TRCT
     * esconderia o atraso do outro documento.
     */
    static void r06ACarenciaDoAsoNaoEscondeOutroAtraso() {
        LocalDate prazo = LocalDate.of(2026, 6, 30);
        LocalDate seteDiasDepois = LocalDate.of(2026, 7, 7);

        DadosDoCiclo soAso = DadosDoCiclo.de("06/2026").comEvento(
                new DadosDoCiclo.ConjuntoDoEvento("0001", "RESCISAO", prazo,
                        List.of("RES.TRCT", "RES.ASO_DEMISSIONAL"),
                        List.of("RES.ASO_DEMISSIONAL"), true)).construir();
        ok("Cap. 9 . faltando so o ASO, a carencia de 10 dias vale",
                new R06ConjuntoDoEvento(ResultadoDaConciliacao.Modo.BLOQUEIO, seteDiasDepois)
                        .executar(soAso, Tolerancia.deCentavos(1))
                        .conforme());

        DadosDoCiclo asoETrct = DadosDoCiclo.de("06/2026").comEvento(
                new DadosDoCiclo.ConjuntoDoEvento("0001", "RESCISAO", prazo,
                        List.of("RES.TRCT", "RES.ASO_DEMISSIONAL"),
                        List.of("RES.TRCT", "RES.ASO_DEMISSIONAL"), true)).construir();
        ok("Cap. 9 . mas faltando tambem o TRCT o conjunto ja esta atrasado",
                !new R06ConjuntoDoEvento(ResultadoDaConciliacao.Modo.BLOQUEIO, seteDiasDepois)
                        .executar(asoETrct, Tolerancia.deCentavos(1))
                        .conforme());

        DadosDoCiclo completo = DadosDoCiclo.de("06/2026").comEvento(
                new DadosDoCiclo.ConjuntoDoEvento("0001", "RESCISAO", prazo,
                        List.of("RES.TRCT"), List.of(), false)).construir();
        ok("R06 . conjunto completo e conforme, mesmo depois do prazo",
                new R06ConjuntoDoEvento(ResultadoDaConciliacao.Modo.BLOQUEIO,
                        LocalDate.of(2026, 12, 1))
                        .executar(completo, Tolerancia.deCentavos(1))
                        .conforme());
    }

    static FolhaDeCompetencia folhaDeMatriculas(String... matriculas) {
        List<ItemDaFolha> itens = new java.util.ArrayList<>();
        for (String m : matriculas) {
            itens.add(new ItemDaFolha(m, "TRABALHADOR " + m, null, "06/2026",
                    List.of(), List.of(), null, null, null, null, null, null, null,
                    Map.of(), "104501"));
        }
        return new FolhaDeCompetencia("06/2026", itens);
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
