package br.com.engesoftware.sgdf.ciclo;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeCiclo;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maquina de estados do ciclo e indicador D+3 — historias F3-03 e F3-04.
 *
 * <p>Criterios de aceite: F3-04, <i>"ciclo com divergencia bloqueante nao
 * publica; dispensa aprovada libera com trilha"</i>; F3-03, <i>"painel exibe
 * tempo ateste-NF por contrato e o % >= 98% da politica"</i>.
 */
public final class TestesDeCiclo {

    static final String MARCA = "teste-ciclo";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        // A tabela de transicoes e Java puro: ela vale com ou sem banco, e
        // pular esta parte por falta de conexao esconderia o que nao depende
        // de conexao nenhuma.
        executar("aTabelaDoCapitulo62", TestesDeCiclo::aTabelaDoCapitulo62);
        executar("oIndicadorSemFaturadoNaoAfirmaNada",
                TestesDeCiclo::oIndicadorSemFaturadoNaoAfirmaNada);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("oCicloAndaPeloCaminhoFeliz", () -> oCicloAndaPeloCaminhoFeliz(sgdf));
                    executar("bloqueanteEmAbertoImpedePronto",
                            () -> bloqueanteEmAbertoImpedePronto(sgdf));
                    executar("aDispensaLiberaComTrilha", () -> aDispensaLiberaComTrilha(sgdf));
                    executar("concilidadoNaoBastaParaPronto",
                            () -> concilidadoNaoBastaParaPronto(sgdf));
                    executar("atestadoExigeAteste", () -> atestadoExigeAteste(sgdf));
                    executar("faturadoExigeNotaFiscal", () -> faturadoExigeNotaFiscal(sgdf));
                    executar("oAtesteNaoSeSobrescreve", () -> oAtesteNaoSeSobrescreve(sgdf));
                    executar("nfAnteriorAoAtesteERecusada",
                            () -> nfAnteriorAoAtesteERecusada(sgdf));
                    executar("movimentoSemMotivoERecusado",
                            () -> movimentoSemMotivoERecusado(sgdf));
                    executar("aTrilhaGuardaDeParaEMotivo", () -> aTrilhaGuardaDeParaEMotivo(sgdf));
                    executar("oIndicadorD3ContaSobreOsFaturados",
                            () -> oIndicadorD3ContaSobreOsFaturados(sgdf));
                    executar("oTempoPorContratoSeparaOQueAindaNaoFaturou",
                            () -> oTempoPorContratoSeparaOQueAindaNaoFaturou(sgdf));
                } finally {
                    limpar(conexao);
                }
            }
        }

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    // --- a tabela, sem banco ---------------------------------------------------

    /**
     * O cap. 6.2, linha a linha — e o que ele NAO permite.
     *
     * <p>Afirmar so o caminho feliz deixaria passar uma tabela que permitisse
     * tudo: ABERTO → EM_COLETA continuaria valendo numa maquina sem restricao
     * nenhuma. Sao as negativas que dizem que a tabela e fechada.
     */
    static void aTabelaDoCapitulo62() {
        ok("Cap. 6.2 . ABERTO vai a EM_COLETA",
                EstadoDoCiclo.ABERTO.podeIrPara(EstadoDoCiclo.EM_COLETA));
        ok("Cap. 6.2 . EM_COLETA vai a PRONTO",
                EstadoDoCiclo.EM_COLETA.podeIrPara(EstadoDoCiclo.PRONTO));
        ok("Cap. 6.2 . PRONTO vai a ATESTADO",
                EstadoDoCiclo.PRONTO.podeIrPara(EstadoDoCiclo.ATESTADO));
        ok("Cap. 6.2 . ATESTADO vai a FATURADO",
                EstadoDoCiclo.ATESTADO.podeIrPara(EstadoDoCiclo.FATURADO));
        ok("Cap. 6.2 . FATURADO vai a FECHADO",
                EstadoDoCiclo.FATURADO.podeIrPara(EstadoDoCiclo.FECHADO));

        ok("Cap. 6.2 . ABERTO NAO salta para PRONTO — um ciclo que nao coletou "
                + "nada nao esta completo",
                !EstadoDoCiclo.ABERTO.podeIrPara(EstadoDoCiclo.PRONTO));
        ok("Cap. 6.2 . PRONTO NAO salta para FATURADO — pularia o ateste, que e "
                + "de onde o D+3 conta",
                !EstadoDoCiclo.PRONTO.podeIrPara(EstadoDoCiclo.FATURADO));
        ok("Cap. 6.2 . EM_COLETA NAO volta para ABERTO",
                !EstadoDoCiclo.EM_COLETA.podeIrPara(EstadoDoCiclo.ABERTO));
        ok("Cap. 6.2 . FECHADO so sai por REABERTO — e o que faz a versao do "
                + "book incrementar",
                Set.of(EstadoDoCiclo.REABERTO).equals(EstadoDoCiclo.FECHADO.destinos()));
        ok("Cap. 6.2 . BLOQUEADO devolve ao fluxo, nao o pula",
                EstadoDoCiclo.BLOQUEADO.podeIrPara(EstadoDoCiclo.EM_COLETA)
                        && !EstadoDoCiclo.BLOQUEADO.podeIrPara(EstadoDoCiclo.ATESTADO));
        ok("Cap. 6.2 . nenhum estado transita para si mesmo",
                EnumSet.allOf(EstadoDoCiclo.class).stream().noneMatch(e -> e.podeIrPara(e)));
        ok("Cap. 6.2 . ABERTO e alcancavel de lugar nenhum — so a abertura o cria",
                EnumSet.allOf(EstadoDoCiclo.class).stream()
                        .noneMatch(e -> e.destinos().contains(EstadoDoCiclo.ABERTO)));
        ok("Cap. 6.2 . destino nulo nao e movimento",
                !EstadoDoCiclo.ABERTO.podeIrPara(null));
        ok("Cap. 6.2 . texto desconhecido nao vira estado",
                EstadoDoCiclo.de("FATRADO") == null && EstadoDoCiclo.de(null) == null);

        ok("Cap. 6.2 . so PRONTO exige bloqueantes resolvidas",
                EnumSet.allOf(EstadoDoCiclo.class).stream()
                        .filter(EstadoDoCiclo::exigeBloqueantesResolvidas).toList()
                        .equals(List.of(EstadoDoCiclo.PRONTO)));
    }

    /**
     * Zero de zero nao e 0% nem 100%.
     *
     * <p>Devolver 0% faria o painel acusar descumprimento numa competencia onde
     * nada faturou ainda; devolver 100% faria o oposto. A resposta certa e nao
     * afirmar — e ela precisa estar testada, porque e a que ninguem lembra.
     */
    static void oIndicadorSemFaturadoNaoAfirmaNada() {
        var vazio = new RepositorioDeCiclo.IndicadorD3("2026-04", 0, 0, null);
        ok("Cap. 21 . sem ciclo faturado o percentual e nulo, nao zero",
                vazio.percentual() == null);
        ok("Cap. 21 . e nao se declara meta atingida sobre nada",
                !vazio.atingeAMeta());

        var noLimite = new RepositorioDeCiclo.IndicadorD3("2026-04", 100, 98, null);
        ok("Cap. 21 . 98 de 100 atinge a meta (>= 98%)",
                noLimite.atingeAMeta()
                        && noLimite.percentual().compareTo(new java.math.BigDecimal("98.00")) == 0);
        var abaixo = new RepositorioDeCiclo.IndicadorD3("2026-04", 100, 97, null);
        ok("Cap. 21 . 97 de 100 nao atinge", !abaixo.atingeAMeta());
    }

    // --- com banco -------------------------------------------------------------

    static void oCicloAndaPeloCaminhoFeliz(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);
        publicar(sgdf, f.exigencia);

        repo.mover(f.ciclo, EstadoDoCiclo.EM_COLETA, "ana.lima", "PUBLICADOR_FIN",
                "varredura da competencia iniciada");
        repo.mover(f.ciclo, EstadoDoCiclo.PRONTO, "ana.lima", "PUBLICADOR_FIN",
                "todas as bloqueantes publicadas no book");
        repo.registrarAteste(f.ciclo, OffsetDateTime.now().minusDays(2), "e-mail do gestor",
                null, "carlos.souza", "GESTOR_CONTRATO");
        repo.mover(f.ciclo, EstadoDoCiclo.ATESTADO, "ana.lima", "PUBLICADOR_FIN",
                "ateste recebido do cliente por e-mail");
        repo.registrarNotaFiscal(f.ciclo, OffsetDateTime.now().minusDays(1), "ana.lima",
                "PUBLICADOR_FIN");
        repo.mover(f.ciclo, EstadoDoCiclo.FATURADO, "ana.lima", "PUBLICADOR_FIN",
                "nota fiscal 1234 emitida");
        repo.mover(f.ciclo, EstadoDoCiclo.FECHADO, "ana.lima", "PUBLICADOR_FIN",
                "competencia encerrada sem pendencia");

        ok("Cap. 6.2 . o ciclo percorre ABERTO ate FECHADO",
                EstadoDoCiclo.FECHADO == repo.estadoDe(f.ciclo));
        ok("Cap. 6.2 . e FECHADO grava fechado_em",
                escalar(sgdf, "SELECT fechado_em FROM ciclo WHERE id = '" + f.ciclo + "'")
                        != null);
        // CINCO, e nao sete: EM_COLETA, PRONTO, ATESTADO, FATURADO, FECHADO.
        // O ateste e a NF sao REGISTROS, nao transicoes — e sao duas linhas
        // proprias na trilha. Contar as sete juntas seria contar o mesmo fluxo
        // com duas reguas.
        ok("Cap. 6.2 . as cinco transicoes estao na trilha",
                5 == contar(sgdf, "log_auditoria WHERE objeto_id = '" + f.ciclo
                        + "' AND acao LIKE 'CICLO\\_%' AND acao NOT IN "
                        + "('CICLO_ATESTE', 'CICLO_NF')"));
        ok("Cap. 16 . e os dois registros — ateste e NF — sao linhas proprias",
                2 == contar(sgdf, "log_auditoria WHERE objeto_id = '" + f.ciclo
                        + "' AND acao IN ('CICLO_ATESTE', 'CICLO_NF')"));
    }

    /** O criterio de aceite da F3-04, primeira metade. */
    static void bloqueanteEmAbertoImpedePronto(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);
        repo.mover(f.ciclo, EstadoDoCiclo.EM_COLETA, "ana.lima", "PUBLICADOR_FIN",
                "varredura iniciada na competencia");

        List<String> abertas = repo.bloqueantesEmAberto(f.ciclo);
        ok("F3-04 . a bloqueante pendente aparece nomeada, nao como um 'nao pode'",
                abertas.size() == 1 && abertas.get(0).contains("TST.CIC")
                        && abertas.get(0).contains("PENDENTE"));

        boolean recusou = false;
        List<String> noErro = List.of();
        try {
            repo.mover(f.ciclo, EstadoDoCiclo.PRONTO, "ana.lima", "PUBLICADOR_FIN",
                    "quero declarar pronto sem resolver a bloqueante");
        } catch (RepositorioDeCiclo.BloqueantesEmAberto e) {
            recusou = true;
            noErro = e.abertas();
        }
        ok("F3-04 . o ciclo com bloqueante em aberto nao vai a PRONTO", recusou);
        ok("F3-04 . e a recusa carrega a lista, para quem recebe saber o que resolver",
                noErro.size() == 1);
        ok("F3-04 . e o ciclo continua EM_COLETA",
                EstadoDoCiclo.EM_COLETA == repo.estadoDe(f.ciclo));
    }

    /** O criterio de aceite da F3-04, segunda metade: "dispensa aprovada libera com trilha". */
    static void aDispensaLiberaComTrilha(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);
        repo.mover(f.ciclo, EstadoDoCiclo.EM_COLETA, "ana.lima", "PUBLICADOR_FIN",
                "varredura iniciada na competencia");

        var excecoes = new br.com.engesoftware.sgdf.persistencia.RepositorioDeExcecao(sgdf);
        UUID pedido = excecoes.solicitar(f.exigencia,
                "documento inexistente por decisao judicial nos autos 456/26", null,
                "ana.lima", "PUBLICADOR_FIN");
        excecoes.aprovar(pedido, "maria.andrade", "APROVADOR_DAF", true);

        ok("F3-04 . dispensada, a exigencia sai da lista de bloqueio",
                repo.bloqueantesEmAberto(f.ciclo).isEmpty());
        RepositorioDeCiclo.Transicao t = repo.mover(f.ciclo, EstadoDoCiclo.PRONTO, "ana.lima",
                "PUBLICADOR_FIN", "unica bloqueante dispensada por excecao aprovada");
        ok("F3-04 . e o ciclo vai a PRONTO",
                t.de() == EstadoDoCiclo.EM_COLETA && t.para() == EstadoDoCiclo.PRONTO);
        ok("F3-04 . a dispensa esta na trilha",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'EXCECAO_APROVADA'"
                        + " AND objeto_id = '" + pedido + "'"));
        ok("F3-04 . e a liberacao do ciclo tambem",
                1 == contar(sgdf, "log_auditoria WHERE acao = 'CICLO_PRONTO'"
                        + " AND objeto_id = '" + f.ciclo + "'"));
    }

    /**
     * Os dois portoes sao diferentes, e este teste e o que os separa.
     *
     * <p>CONCILIADO basta para PUBLICAR O BOOK — e o que
     * {@code ConsultaDoPainel.motivosDeBloqueio} aceita — e NAO basta para o
     * ciclo ir a PRONTO, que o cap. 6.2 condiciona a PUBLICADO/DISPENSADO. Usar
     * o portao frouxo no lugar do rigido deixaria a medicao sair ao cliente com
     * documento ainda nao publicado no book.
     */
    static void concilidadoNaoBastaParaPronto(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);
        repo.mover(f.ciclo, EstadoDoCiclo.EM_COLETA, "ana.lima", "PUBLICADOR_FIN",
                "varredura iniciada na competencia");
        executar(sgdf, "UPDATE exigencia SET status = 'CONCILIADO' WHERE id = '"
                + f.exigencia + "'");

        ok("Cap. 6.2 . CONCILIADO ainda conta como bloqueante em aberto para PRONTO",
                repo.bloqueantesEmAberto(f.ciclo).size() == 1);
        ok("Painel . mas NAO impede publicar o book — os dois portoes sao diferentes",
                new br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel(sgdf)
                        .motivosDeBloqueio(f.ciclo).isEmpty());

        boolean recusou = false;
        try {
            repo.mover(f.ciclo, EstadoDoCiclo.PRONTO, "ana.lima", "PUBLICADOR_FIN",
                    "conciliado deveria bastar? o capitulo diz que nao");
        } catch (RepositorioDeCiclo.BloqueantesEmAberto e) {
            recusou = true;
        }
        ok("Cap. 6.2 . e o ciclo nao vai a PRONTO com bloqueante apenas CONCILIADA", recusou);
    }

    static void atestadoExigeAteste(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);
        publicar(sgdf, f.exigencia);
        repo.mover(f.ciclo, EstadoDoCiclo.EM_COLETA, "ana.lima", "PUBLICADOR_FIN",
                "varredura iniciada na competencia");
        repo.mover(f.ciclo, EstadoDoCiclo.PRONTO, "ana.lima", "PUBLICADOR_FIN",
                "bloqueantes publicadas no book da competencia");

        boolean recusou = false;
        try {
            repo.mover(f.ciclo, EstadoDoCiclo.ATESTADO, "ana.lima", "PUBLICADOR_FIN",
                    "declarando atestado sem ateste registrado");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            recusou = true;
        }
        ok("Cap. 6.2 . ATESTADO sem ateste registrado e recusado — o marco do D+3 "
                + "nao se declara", recusou);

        boolean semForma = false;
        try {
            repo.registrarAteste(f.ciclo, OffsetDateTime.now(), "  ", null, "carlos.souza",
                    "GESTOR_CONTRATO");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            semForma = true;
        }
        ok("Cap. 13 . ateste sem forma e recusado", semForma);

        boolean futuro = false;
        try {
            repo.registrarAteste(f.ciclo, OffsetDateTime.now().plusDays(1), "portal", null,
                    "carlos.souza", "GESTOR_CONTRATO");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            futuro = true;
        }
        ok("Cap. 21 . ateste em data futura e recusado — inventaria folga no D+3", futuro);

        repo.registrarAteste(f.ciclo, OffsetDateTime.now().minusHours(2), "portal do cliente",
                null, "carlos.souza", "GESTOR_CONTRATO");
        ok("Cap. 6.2 . registrado o ateste, o ciclo AINDA esta PRONTO — registrar "
                + "nao e transitar", EstadoDoCiclo.PRONTO == repo.estadoDe(f.ciclo));
        repo.mover(f.ciclo, EstadoDoCiclo.ATESTADO, "ana.lima", "PUBLICADOR_FIN",
                "ateste do cliente confirmado no portal");
        ok("Cap. 6.2 . e so entao ele vai a ATESTADO",
                EstadoDoCiclo.ATESTADO == repo.estadoDe(f.ciclo));
    }

    static void faturadoExigeNotaFiscal(Sgdf sgdf) {
        Fixture f = atestado(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);

        boolean recusou = false;
        try {
            repo.mover(f.ciclo, EstadoDoCiclo.FATURADO, "ana.lima", "PUBLICADOR_FIN",
                    "declarando faturado sem nota fiscal emitida");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            recusou = true;
        }
        ok("Cap. 6.2 . FATURADO sem nf_emitida_em e recusado", recusou);

        repo.registrarNotaFiscal(f.ciclo, OffsetDateTime.now(), "ana.lima", "PUBLICADOR_FIN");
        repo.mover(f.ciclo, EstadoDoCiclo.FATURADO, "ana.lima", "PUBLICADOR_FIN",
                "nota fiscal 5678 emitida para a competencia");
        ok("Cap. 6.2 . com a NF registrada, ele fatura",
                EstadoDoCiclo.FATURADO == repo.estadoDe(f.ciclo));
    }

    /** Reescrever o ateste moveria o marco depois de o relogio comecar. */
    static void oAtesteNaoSeSobrescreve(Sgdf sgdf) {
        Fixture f = atestado(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);
        String antes = escalar(sgdf, "SELECT ateste_em FROM ciclo WHERE id = '" + f.ciclo + "'");

        boolean recusou = false;
        try {
            repo.registrarAteste(f.ciclo, OffsetDateTime.now(), "outro e-mail", null,
                    "carlos.souza", "GESTOR_CONTRATO");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            recusou = true;
        }
        ok("Cap. 21 . o ateste ja registrado nao se sobrescreve", recusou);
        ok("Cap. 21 . e a data original permanece",
                antes.equals(escalar(sgdf, "SELECT ateste_em FROM ciclo WHERE id = '"
                        + f.ciclo + "'")));
    }

    /** Um intervalo negativo entraria no D+3 como cumprimento. */
    static void nfAnteriorAoAtesteERecusada(Sgdf sgdf) {
        Fixture f = atestado(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);

        boolean recusou = false;
        try {
            repo.registrarNotaFiscal(f.ciclo, OffsetDateTime.now().minusDays(30), "ana.lima",
                    "PUBLICADOR_FIN");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            recusou = true;
        }
        ok("Cap. 21 . NF anterior ao ateste e recusada — o intervalo negativo "
                + "entraria como cumprimento", recusou);

        Fixture semAteste = fixture(sgdf);
        boolean semMarco = false;
        try {
            repo.registrarNotaFiscal(semAteste.ciclo, OffsetDateTime.now(), "ana.lima",
                    "PUBLICADOR_FIN");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            semMarco = true;
        }
        ok("Cap. 21 . e NF em ciclo sem ateste tambem — nao ha de onde contar", semMarco);
    }

    static void movimentoSemMotivoERecusado(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);

        boolean recusou = false;
        try {
            repo.mover(f.ciclo, EstadoDoCiclo.EM_COLETA, "ana.lima", "PUBLICADOR_FIN", "ok");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            recusou = true;
        }
        ok("Cap. 6.2 . transicao com motivo vago e recusada — a trilha exige o porque",
                recusou);
        ok("Cap. 6.2 . e o ciclo nao se moveu",
                EstadoDoCiclo.ABERTO == repo.estadoDe(f.ciclo));

        boolean saltou = false;
        try {
            repo.mover(f.ciclo, EstadoDoCiclo.FATURADO, "ana.lima", "PUBLICADOR_FIN",
                    "tentando saltar direto para faturado");
        } catch (RepositorioDeCiclo.TransicaoInvalida e) {
            saltou = true;
        }
        ok("Cap. 6.2 . e o salto de ABERTO para FATURADO tambem", saltou);
    }

    static void aTrilhaGuardaDeParaEMotivo(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        new RepositorioDeCiclo(sgdf).mover(f.ciclo, EstadoDoCiclo.BLOQUEADO, "maria.andrade",
                "APROVADOR_DAF", "divergencia bloqueante na guia de FGTS da competencia");

        String detalhe = escalar(sgdf, "SELECT detalhe::text FROM log_auditoria"
                + " WHERE objeto_id = '" + f.ciclo + "' AND acao = 'CICLO_BLOQUEADO'");
        ok("Cap. 6.2 . a trilha guarda de, para e motivo",
                detalhe != null && detalhe.contains("ABERTO") && detalhe.contains("BLOQUEADO")
                        && detalhe.contains("FGTS"));
        ok("Cap. 16 . com ator e papel",
                "maria.andrade".equals(escalar(sgdf, "SELECT ator FROM log_auditoria"
                        + " WHERE objeto_id = '" + f.ciclo + "' AND acao = 'CICLO_BLOQUEADO'")));
    }

    /**
     * O denominador sao os faturados — e este teste e o que prova.
     *
     * <p>Tres ciclos atestados: dois faturaram (um dentro do D+3, outro fora) e
     * um ainda nao. Se o denominador fosse "atestados", o percentual daria
     * 33,33% e o ciclo que ainda tem prazo apareceria como descumprimento.
     */
    static void oIndicadorD3ContaSobreOsFaturados(Sgdf sgdf) {
        String competencia = "2026-0" + (1 + (sequencia % 9));
        Fixture dentro = fixture(sgdf, competencia);
        Fixture fora = fixture(sgdf, competencia);
        Fixture pendente = fixture(sgdf, competencia);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);

        atestarE(sgdf, dentro.ciclo, 10, 8);   // 2 dias: dentro
        atestarE(sgdf, fora.ciclo, 10, 4);     // 6 dias: fora
        atestarE(sgdf, pendente.ciclo, 10, -1); // sem NF

        RepositorioDeCiclo.IndicadorD3 i = repo.indicadorD3(competencia);
        ok("Cap. 21 . o denominador sao os ciclos FATURADOS, nao os atestados",
                i.faturados() == 2);
        ok("Cap. 21 . e o ciclo que ainda nao faturou nao conta como descumprimento",
                i.dentroDoPrazo() == 1
                        && i.percentual().compareTo(new java.math.BigDecimal("50.00")) == 0);
        ok("Cap. 21 . a media ateste->NF sai em dias, so dos faturados",
                i.mediaEmDias().compareTo(new java.math.BigDecimal("4.00")) == 0);
        ok("Cap. 21 . 50% nao atinge a meta de 98%", !i.atingeAMeta());
    }

    static void oTempoPorContratoSeparaOQueAindaNaoFaturou(Sgdf sgdf) {
        String competencia = "2026-1" + (sequencia % 2);
        Fixture com = fixture(sgdf, competencia);
        Fixture sem = fixture(sgdf, competencia);
        atestarE(sgdf, com.ciclo, 5, 4);
        atestarE(sgdf, sem.ciclo, 5, -1);

        List<RepositorioDeCiclo.TempoPorContrato> linhas =
                new RepositorioDeCiclo(sgdf).tempoPorContrato(competencia);
        ok("F3-03 . o painel lista um contrato por ciclo atestado", linhas.size() == 2);
        ok("F3-03 . com o tempo ateste->NF de quem faturou",
                linhas.stream().anyMatch(l -> l.cicloId().equals(com.ciclo)
                        && l.dias() != null
                        && l.dias().compareTo(new java.math.BigDecimal("1.00")) == 0));
        ok("F3-03 . e nulo — nao zero — para quem ainda nao emitiu a NF",
                linhas.stream().anyMatch(l -> l.cicloId().equals(sem.ciclo)
                        && l.dias() == null));
    }

    // -------------------------------------------------------------------------

    record Fixture(int seq, UUID ciclo, UUID exigencia) {}

    static Fixture fixture(Sgdf sgdf) {
        return fixture(sgdf, "2026-04");
    }

    static Fixture fixture(Sgdf sgdf, String competencia) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Ciclo " + n + "', '" + String.format("5%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-cic-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Ciclo " + n + "', '" + String.format("6%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-CIC-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/teste', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        UUID ciclo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '" + competencia
                + "', 'ABERTO', '" + versao + "', '" + MARCA + "') RETURNING id");
        UUID tipo = uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo,"
                + " evento, defasagem, criticidade, sigilo, criado_por) VALUES"
                + " ('TST.CIC" + n + "', 'Tipo', 'Teste', 'CONTRATO', 'MENSAL', 'M',"
                + " 'BLOQUEANTE', 'INTERNO', '" + MARCA + "') RETURNING id");
        UUID exigencia = uuid(sgdf, "INSERT INTO exigencia (ciclo_id, tipo_id, evento, status,"
                + " prazo_calculado, criticidade, responsavel, origem, criado_por) VALUES ('"
                + ciclo + "', '" + tipo + "', 'MENSAL', 'PENDENTE', DATE '2026-05-05',"
                + " 'BLOQUEANTE', 'AP', 'MATRIZ', '" + MARCA + "') RETURNING id");
        executar(sgdf, "INSERT INTO pendencia (exigencia_id, prazo) VALUES ('" + exigencia
                + "', DATE '2026-05-05')");
        return new Fixture(n, ciclo, exigencia);
    }

    /** Um ciclo ja em ATESTADO, para os testes que comecam depois do marco. */
    static Fixture atestado(Sgdf sgdf) {
        Fixture f = fixture(sgdf);
        RepositorioDeCiclo repo = new RepositorioDeCiclo(sgdf);
        publicar(sgdf, f.exigencia);
        repo.mover(f.ciclo, EstadoDoCiclo.EM_COLETA, "ana.lima", "PUBLICADOR_FIN",
                "varredura iniciada na competencia");
        repo.mover(f.ciclo, EstadoDoCiclo.PRONTO, "ana.lima", "PUBLICADOR_FIN",
                "bloqueantes publicadas no book da competencia");
        repo.registrarAteste(f.ciclo, OffsetDateTime.now().minusDays(3), "e-mail do gestor",
                null, "carlos.souza", "GESTOR_CONTRATO");
        repo.mover(f.ciclo, EstadoDoCiclo.ATESTADO, "ana.lima", "PUBLICADOR_FIN",
                "ateste recebido do cliente por e-mail");
        return f;
    }

    static void publicar(Sgdf sgdf, UUID exigencia) {
        executar(sgdf, "UPDATE exigencia SET status = 'PUBLICADO' WHERE id = '"
                + exigencia + "'");
    }

    /**
     * Datas em SQL, e nao pelo repositorio, de proposito.
     *
     * <p>O repositorio recusa ateste futuro e NF anterior ao ateste — o que e
     * certo e impediria montar um cenario de "faturou 6 dias depois" sem
     * esperar seis dias. O que se mede aqui e a CONTA do indicador, e ela tem
     * de valer sobre datas que o proprio sistema aceitaria.
     *
     * @param nfHaDias negativo para "ainda nao emitiu"
     */
    static void atestarE(Sgdf sgdf, UUID ciclo, int atesteHaDias, int nfHaDias) {
        executar(sgdf, "UPDATE ciclo SET ateste_em = now() - INTERVAL '" + atesteHaDias
                + " days', ateste_forma = 'e-mail', nf_emitida_em = "
                + (nfHaDias < 0 ? "NULL" : "now() - INTERVAL '" + nfHaDias + " days'")
                + " WHERE id = '" + ciclo + "'");
    }

    static UUID uuid(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, UUID.class);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static void executar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static String escalar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao ler " + sql, e);
        }
    }

    static long contar(Sgdf sgdf, String de) {
        return Long.parseLong(escalar(sgdf, "SELECT count(*) FROM " + de));
    }

    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM excecao WHERE exigencia_id IN (SELECT id FROM exigencia"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM pendencia WHERE exigencia_id IN (SELECT id FROM exigencia"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM exigencia WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM tipo_documental WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM empresa WHERE criado_por = '" + MARCA + "'",
        };
        try (Statement st = conexao.createStatement()) {
            for (String c : comandos) {
                st.execute(c);
            }
        }
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

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    private TestesDeCiclo() {}
}
