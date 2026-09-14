package br.com.engesoftware.sgdf.seguranca;

import br.com.engesoftware.sgdf.persistencia.ConsultaDeRecertificacao;
import br.com.engesoftware.sgdf.web.AtorDaRequisicao;
import br.com.engesoftware.sgdf.web.ObservadorDeAcesso;
import br.com.engesoftware.sgdf.persistencia.RegistroDeAcesso;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import br.com.engesoftware.sgdf.persistencia.TrilhaDeAuditoria;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Recertificacao trimestral de acessos — historia F3-06, requisito SEC-10.
 *
 * <p>Criterio de aceite: <i>"relatorio gerado e enviado a DAF"</i>. SEC-10 pede
 * <i>"recertificacao trimestral de acessos por contrato"</i>.
 */
public final class TestesDeRecertificacao {

    static final String MARCA = "teste-recert";
    static final LocalDate DESDE = LocalDate.of(2027, 1, 1);
    static final LocalDate ATE = LocalDate.of(2027, 3, 31);
    static final LocalDate DENTRO = LocalDate.of(2027, 2, 10);

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        executar("aAtencaoNaoViraRuido", TestesDeRecertificacao::aAtencaoNaoViraRuido);
        executar("aFalhaDeObservacaoEContadaEMudaARessalva",
                TestesDeRecertificacao::aFalhaDeObservacaoEContadaEMudaARessalva);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("oAuditorApareceNaPropriaRecertificacao",
                            () -> oAuditorApareceNaPropriaRecertificacao(sgdf));
                    executar("oPrivilegioNaoExercidoEApontado",
                            () -> oPrivilegioNaoExercidoEApontado(sgdf));
                    executar("aDerivaDeConcessaoAparece",
                            () -> aDerivaDeConcessaoAparece(sgdf));
                    executar("aOrdemDoArrayNaoInventaDeriva",
                            () -> aOrdemDoArrayNaoInventaDeriva(sgdf));
                    executar("oEscopoDeContratoEntraNoRelatorio",
                            () -> oEscopoDeContratoEntraNoRelatorio(sgdf));
                    executar("oPeriodoRecorta", () -> oPeriodoRecorta(sgdf));
                    executar("oCsvComecaPelaRessalva", () -> oCsvComecaPelaRessalva(sgdf));
                    executar("observarNaoDerrubaARequisicao",
                            () -> observarNaoDerrubaARequisicao(sgdf));
                    executar("oInterceptorDecideAsQuatroCoisasQueLheCabem",
                            () -> oInterceptorDecideAsQuatroCoisasQueLheCabem(sgdf));
                    executar("aFalhaSoDestaTabelaDeixaMarcaDuravelNaTrilha",
                            () -> aFalhaSoDestaTabelaDeixaMarcaDuravelNaTrilha(sgdf));
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

    /**
     * "Somente leitura" nao e apontamento sozinho.
     *
     * <p>O papel AUDITORIA le por definicao (cap. 15.1: nao pode "qualquer
     * escrita"). Marca-lo faria a lista de atencao repetir todo trimestre a
     * unica linha que esta certa — e uma lista que sempre acusa a mesma coisa e
     * uma lista que ninguem le.
     */
    static void aAtencaoNaoViraRuido() {
        var auditor = acesso("auditora.silva", List.of("AUDITORIA"), 0, 1, 0);
        ok("SEC-10 . o AUDITORIA que so leu NAO e apontado — ler e o que ele faz",
                auditor.atencao().isEmpty()
                        && "SOMENTE_LEITURA".equals(auditor.situacao()));

        var dafParado = acesso("chefe.daf", List.of("APROVADOR_DAF"), 0, 1, 0);
        ok("SEC-10 . mas o APROVADOR_DAF que nunca aprovou nada E apontado",
                dafParado.atencao().stream().anyMatch(a -> a.contains("não exercido"))
                        && "REVISAR".equals(dafParado.situacao()));

        var dafAtivo = acesso("chefe.daf", List.of("APROVADOR_DAF"), 12, 1, 0);
        ok("SEC-10 . e o que aprovou nao e apontado por isso",
                dafAtivo.atencao().isEmpty() && "ATIVO".equals(dafAtivo.situacao()));

        var acumula = acesso("dono.de.tudo",
                List.of("APROVADOR_DAF", "CURADOR_MATRIZ"), 30, 1, 0);
        ok("Cap. 15.1 . acumular CURADOR_MATRIZ e APROVADOR_DAF e concentracao apontada",
                acumula.atencao().stream().anyMatch(a -> a.contains("concentração")));

        var servico = acesso("svc.leitura", List.of("SVC_LEITURA"), 0, 1, 0);
        ok("Cap. 15.1 . conta de servico que aparece como tendo ENTRADO e apontada — "
                        + "login interativo e negado",
                servico.atencao().stream().anyMatch(a -> a.contains("conta de serviço")));

        var comum = acesso("fulano", List.of("PUBLICADOR_FIN"), 5, 1, 3);
        ok("SEC-10 . e o ator comum e ativo, sem apontamento",
                comum.atencao().isEmpty() && "ATIVO".equals(comum.situacao()));
    }

    // --- com banco --------------------------------------------------------------

    /**
     * O que a trilha sozinha nao conseguiria.
     *
     * <p>A trilha registra quem ESCREVE. O auditor nao escreve — e um relatorio
     * de acessos que omite o auditor omite justamente quem tem leitura global.
     */
    static void oAuditorApareceNaPropriaRecertificacao(Sgdf sgdf) {
        observar(sgdf, "auditora.silva", DENTRO, List.of("AUDITORIA"), List.of());
        acao(sgdf, "outra.pessoa", "CURADOR_MATRIZ", "CADASTRO_CONTRATO");

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        var auditor = achar(r, "auditora.silva");
        ok("SEC-10 . o auditor aparece, mesmo sem ter escrito nada",
                auditor != null && auditor.acoesNoPeriodo() == 0);
        ok("SEC-10 . e com o papel que o token concedeu",
                auditor.papeis().equals(List.of("AUDITORIA")));
        ok("SEC-10 . quem so escreveu e nao entrou NAO aparece — a lista e de acesso "
                        + "observado, e a ressalva diz isso",
                achar(r, "outra.pessoa") == null);
        ok("SEC-10 . e a ressalva declara a cobertura",
                ConsultaDeRecertificacao.RESSALVA.contains("nunca entrou")
                        && ConsultaDeRecertificacao.RESSALVA.contains("RA-16"));
    }

    static void oPrivilegioNaoExercidoEApontado(Sgdf sgdf) {
        observar(sgdf, "daf.parado", DENTRO, List.of("APROVADOR_DAF"), List.of());
        observar(sgdf, "daf.ativo", DENTRO, List.of("APROVADOR_DAF"), List.of());
        acao(sgdf, "daf.ativo", "APROVADOR_DAF", "EXCECAO_APROVADA");

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        ok("SEC-10 . o DAF que entrou e nunca aprovou e apontado",
                "REVISAR".equals(achar(r, "daf.parado").situacao()));
        ok("SEC-10 . e o que aprovou nao e",
                "ATIVO".equals(achar(r, "daf.ativo").situacao())
                        && achar(r, "daf.ativo").acoesNoPeriodo() == 1);
        ok("SEC-10 . a ordenacao poe quem menos agiu primeiro — a lista comeca pelo "
                        + "que precisa de revisao",
                r.get(0).acoesNoPeriodo() <= r.get(r.size() - 1).acoesNoPeriodo());
    }

    /** Ganhar um papel no meio do trimestre e o que a recertificacao procura. */
    static void aDerivaDeConcessaoAparece(Sgdf sgdf) {
        observar(sgdf, "cresceu", DENTRO, List.of("PUBLICADOR_FIN"), List.of());
        observar(sgdf, "cresceu", DENTRO.plusDays(20),
                List.of("PUBLICADOR_FIN", "APROVADOR_DAF"), List.of());
        acao(sgdf, "cresceu", "APROVADOR_DAF+PUBLICADOR_FIN", "EXCECAO_APROVADA");

        var linha = achar(relatorio(sgdf), "cresceu");
        ok("SEC-10 . as duas concessoes distintas sao contadas",
                linha.concessoesDistintas() == 2);
        ok("SEC-10 . e a mudanca e apontada para conferencia",
                linha.atencao().stream().anyMatch(a -> a.contains("mudou 1 vez")));
        ok("SEC-10 . com os papeis de todo o periodo, nao so os do ultimo dia",
                linha.papeis().containsAll(List.of("APROVADOR_DAF", "PUBLICADOR_FIN")));
    }

    /**
     * {A,B} e {B,A} sao a mesma concessao — e o teste precisa olhar o ARRAY
     * GRAVADO, nao duas chamadas.
     *
     * <p><b>A primeira versao deste teste nao provava nada, e a quebra
     * deliberada mostrou.</b> Ela observava o mesmo ator duas vezes com os
     * papeis em ordem trocada e conferia que dava uma concessao so. Passava com
     * a ordenacao E sem ela — porque {@code Set.copyOf} tem ordem de iteracao
     * ESTAVEL DENTRO DE UMA EXECUCAO da JVM. As duas chamadas concordavam
     * sempre, com ou sem defeito.
     *
     * <p>A ordem so muda ENTRE execucoes (a JVM sorteia um SALT por processo).
     * O defeito, portanto, e invisivel num teste que roda numa JVM so, e
     * apareceria em producao como deriva de concessao fantasma depois de todo
     * reinicio — apontando mudanca de acesso onde ninguem mudou nada.
     *
     * <p>Verificado como tem de ser: lendo o array gravado e exigindo que ele
     * esteja ordenado. Isso o Set nao pode falsificar.
     */
    static void aOrdemDoArrayNaoInventaDeriva(Sgdf sgdf) {
        observar(sgdf, "estavel", DENTRO,
                List.of("PUBLICADOR_FIN", "GESTOR_CONTRATO", "APROVADOR_DAF"), List.of());

        String gravado = escalar(sgdf, "SELECT papeis::text FROM acesso_observado"
                + " WHERE ator = 'estavel'");
        ok("SEC-10 . o array gravado esta ORDENADO — sem isso, a ordem varia entre "
                        + "execucoes da JVM e todo reinicio inventaria deriva",
                "{APROVADOR_DAF,GESTOR_CONTRATO,PUBLICADOR_FIN}".equals(gravado));

        observar(sgdf, "estavel", DENTRO.plusDays(5),
                List.of("GESTOR_CONTRATO", "APROVADOR_DAF", "PUBLICADOR_FIN"), List.of());
        var linha = achar(relatorio(sgdf), "estavel");
        ok("SEC-10 . e a mesma concessao em dias diferentes conta como UMA",
                linha.concessoesDistintas() == 1);
        ok("SEC-10 . sem apontamento de deriva",
                linha.atencao().stream().noneMatch(a -> a.contains("mudou")));

        // O mesmo vale para os contratos: o recorte {c1,c2} e {c2,c1} e o mesmo.
        UUID menor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID maior = UUID.fromString("ffffffff-0000-0000-0000-000000000002");
        observar(sgdf, "contratos.ordenados", DENTRO, List.of("PUBLICADOR_AP"),
                List.of(maior, menor));
        ok("SEC-10 . e os contratos tambem saem ordenados",
                escalar(sgdf, "SELECT contratos::text FROM acesso_observado"
                        + " WHERE ator = 'contratos.ordenados'").indexOf("00000000") == 1);
    }

    /** SEC-10 pede "por contrato": o recorte do token entra no relatorio. */
    static void oEscopoDeContratoEntraNoRelatorio(Sgdf sgdf) {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        observar(sgdf, "recortado", DENTRO, List.of("PUBLICADOR_AP"), List.of(c1, c2));
        observar(sgdf, "global", DENTRO, List.of("APROVADOR_DAF"), List.of());

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        ok("SEC-10 . o recorte de contrato do token e relatado",
                achar(r, "recortado").contratosNoEscopo() == 2);
        ok("SEC-10 . e visao global aparece como zero contratos, nao como ausencia",
                achar(r, "global").contratosNoEscopo() == 0);
    }

    static void oPeriodoRecorta(Sgdf sgdf) {
        observar(sgdf, "dentro.do.trimestre", DENTRO, List.of("PUBLICADOR_FIN"), List.of());
        observar(sgdf, "fora.do.trimestre", ATE.plusDays(1), List.of("PUBLICADOR_FIN"),
                List.of());

        List<ConsultaDeRecertificacao.Acesso> r = relatorio(sgdf);
        ok("SEC-10 . o trimestre recorta", achar(r, "dentro.do.trimestre") != null
                && achar(r, "fora.do.trimestre") == null);

        boolean recusou = false;
        try {
            new ConsultaDeRecertificacao(sgdf).relatorio(ATE, DESDE);
        } catch (ConsultaDeRecertificacao.PeriodoInvalido e) {
            recusou = true;
        }
        ok("SEC-10 . periodo invertido e recusado", recusou);
    }

    /**
     * A ressalva e a PRIMEIRA linha do arquivo.
     *
     * <p>No fim, ou num campo por linha, ela sumiria quando alguem ordenasse a
     * planilha — e e ela que muda o significado da tabela inteira.
     */
    static void oCsvComecaPelaRessalva(Sgdf sgdf) {
        observar(sgdf, "alguem", DENTRO, List.of("PUBLICADOR_FIN"), List.of());
        String csv = new ConsultaDeRecertificacao(sgdf).csv(DESDE, ATE);
        String[] linhas = csv.split("\r\n");

        ok("SEC-10 . a primeira linha do CSV e a ressalva de cobertura",
                linhas[0].contains("COBERTURA") && linhas[0].contains("nunca entrou"));
        ok("SEC-10 . a segunda diz o periodo e quantos atores",
                linhas[1].contains("2027-01-01") && linhas[1].contains("ator(es)"));
        ok("SEC-10 . e o cabecalho vem depois",
                linhas[2].startsWith("ator,papeis"));
        ok("F3-05 . a ressalva passa pelo escape do CSV como qualquer celula",
                !linhas[0].startsWith("=") && !linhas[0].startsWith("+"));
    }

    /**
     * Observar acesso nao pode derrubar o acesso — e a falha nao pode calar.
     *
     * <p>Um erro ao gravar a observacao transformaria uma consulta ao painel em
     * erro 500: a recertificacao derrubando o produto que ela existe para
     * revisar. Ate a RA-15 o preco disso era o silencio — `observar` devolvia
     * `false` tanto para "ja registrada" (o caso normal, toda requisicao depois
     * da primeira do dia) quanto para "falhou", <b>o mesmo valor para dois fatos
     * opostos</b>, e nenhum chamador conseguia distingui-los.
     */
    static void observarNaoDerrubaARequisicao(Sgdf sgdf) {
        RegistroDeAcesso registro = new RegistroDeAcesso(sgdf);
        Ator semPapel = new Ator("ninguem", "Ninguem", Set.of(), Set.of());
        ok("SEC-10 . ator sem papel nao vira observacao — nao ha concessao a certificar",
                registro.observar(semPapel, DENTRO)
                        == RegistroDeAcesso.Desfecho.SEM_CONCESSAO);
        ok("SEC-10 . e ator nulo tampouco",
                registro.observar(null, DENTRO)
                        == RegistroDeAcesso.Desfecho.SEM_CONCESSAO);

        Ator valido = new Ator("repetido", "Fulano", Set.of(Papel.PUBLICADOR_FIN), Set.of());
        ok("SEC-10 . a primeira observacao do dia grava",
                registro.observar(valido, DENTRO) == RegistroDeAcesso.Desfecho.GRAVADA);
        ok("SEC-10 . e a segunda igual nao duplica — uma linha por concessao por dia",
                registro.observar(valido, DENTRO)
                        == RegistroDeAcesso.Desfecho.JA_REGISTRADA);
        ok("SEC-10 . mas nao levanta excecao por isso",
                1 == contar(sgdf, "acesso_observado WHERE ator = 'repetido'"));

        // RA-15: OS TRES DESFECHOS SAO TRES, E NAO DOIS.
        //
        // "Nao gravou porque nao havia o que gravar", "nao gravou porque ja
        // estava la" e "nao gravou porque quebrou" eram todos `false`. O
        // terceiro e o unico que exige alguem agir, e era o indistinguivel.
        ok("RA-15 . SEM_CONCESSAO, JA_REGISTRADA e FALHOU sao fatos distintos",
                RegistroDeAcesso.Desfecho.SEM_CONCESSAO
                        != RegistroDeAcesso.Desfecho.JA_REGISTRADA
                && !RegistroDeAcesso.Desfecho.JA_REGISTRADA.falhou()
                && !RegistroDeAcesso.Desfecho.SEM_CONCESSAO.falhou()
                && RegistroDeAcesso.Desfecho.FALHOU.falhou());
    }

    /**
     * A falha de gravacao e CONTADA, e muda a ressalva do relatorio.
     *
     * <p>Sem banco: uma {@link Sgdf} sobre conexao nula reproduz o caso real
     * mais provavel — o repositorio de escopo de requisicao usado fora de uma
     * requisicao, que levanta antes de haver SQL. E o caminho que a captura de
     * `SQLException` sozinha deixava escapar.
     */
    static void aFalhaDeObservacaoEContadaEMudaARessalva() {
        RegistroDeAcesso.Falhas.zerar();
        ok("RA-15 . o contador comeca zerado e a ressalva e so a do RA-16",
                RegistroDeAcesso.Falhas.total() == 0
                        && ConsultaDeRecertificacao.ressalva()
                                .equals(ConsultaDeRecertificacao.RESSALVA));

        RegistroDeAcesso quebrado = new RegistroDeAcesso(new Sgdf(null));
        Ator valido = new Ator("vitima", "Fulano", Set.of(Papel.PUBLICADOR_FIN), Set.of());

        ok("RA-15 . a gravacao que quebra devolve FALHOU, e nao JA_REGISTRADA",
                quebrado.observar(valido, DENTRO) == RegistroDeAcesso.Desfecho.FALHOU);
        ok("RA-15 . e nao levanta — observar acesso nao derruba o acesso",
                RegistroDeAcesso.Falhas.total() == 1);

        quebrado.observar(valido, DENTRO);
        quebrado.observar(valido, DENTRO);
        ok("RA-15 . falhas repetidas sao contadas, nao colapsadas em uma",
                RegistroDeAcesso.Falhas.total() == 3);

        // O PONTO DA HISTORIA INTEIRA. Antes disto, uma falha persistente de
        // escrita produzia uma lista de recertificacao MENOR — e lista menor
        // parece revisao mais facil, nao defeito.
        String ressalva = ConsultaDeRecertificacao.ressalva();
        ok("RA-15 . a ressalva passa a dizer que a lista esta incompleta",
                ressalva.contains("RA-15") && ressalva.contains("INCOMPLETA")
                        && ressalva.contains("3 observação"));
        ok("RA-15 . e diz para NAO aprovar a revisao com ela assim",
                ressalva.contains("NÃO deve ser aprovada"));
        ok("RA-15 . sem perder a ressalva de cobertura do RA-16",
                ressalva.contains("RA-16"));
        ok("RA-15 . e nomeia o motivo, para alguem ter por onde comecar",
                RegistroDeAcesso.Falhas.ultimoMotivo() != null
                        && RegistroDeAcesso.Falhas.primeira() != null);
        RegistroDeAcesso.Falhas.zerar();
    }

    /**
     * O interceptor — as linhas de cola que a RA-15 dizia nao serem exercitadas.
     *
     * <p>Sao quatro decisoes, e todas as quatro tinham de ser medidas em algum
     * lugar. A quarta e a que justifica a classe existir tao pequena: a promessa
     * de que uma falha de gravacao nao sobe era a unica que ninguem media.
     */
    static void oInterceptorDecideAsQuatroCoisasQueLheCabem(Sgdf sgdf) {
        RegistroDeAcesso.Falhas.zerar();
        Ator ator = new Ator("interceptado", "Fulano", Set.of(Papel.PUBLICADOR_FIN), Set.of());

        ObservadorDeAcesso observador =
                new ObservadorDeAcesso(atorFixo(ator), new RegistroDeAcesso(sgdf));

        // 1. O 4xx NAO vira acesso observado. Em preHandle a gravacao entraria
        //    antes da autorizacao, e um 403 deixaria registrado um "acesso" de
        //    quem foi barrado — a recertificacao listaria TENTATIVAS como
        //    concessoes, que e o oposto do que ela mede.
        observador.afterCompletion(null, resposta(403), null, null);
        ok("SEC-10 . resposta 403 nao vira acesso observado — tentativa nao e concessao",
                0 == contar(sgdf, "acesso_observado WHERE ator = 'interceptado'"));
        observador.afterCompletion(null, resposta(500), null, null);
        ok("SEC-10 . nem 500 — so o que deu certo conta como acesso",
                0 == contar(sgdf, "acesso_observado WHERE ator = 'interceptado'"));

        // 2. Sem ator nao ha o que observar.
        new ObservadorDeAcesso(atorFixo(null), new RegistroDeAcesso(sgdf))
                .afterCompletion(null, resposta(200), null, null);
        ok("SEC-10 . requisicao sem ator resolvido nao grava nada",
                0 == contar(sgdf, "acesso_observado WHERE ator = 'interceptado'"));

        // 3. O caminho feliz grava.
        observador.afterCompletion(null, resposta(200), null, null);
        ok("SEC-10 . a resposta 200 com ator vira a concessao do dia",
                1 == contar(sgdf, "acesso_observado WHERE ator = 'interceptado'"));

        // 4. E a falha NAO sobe.
        new ObservadorDeAcesso(atorFixo(ator), new RegistroDeAcesso(new Sgdf(null)))
                .afterCompletion(null, resposta(200), null, null);
        ok("RA-15 . gravacao quebrada nao devolve erro a quem fez uma requisicao que "
                        + "DEU CERTO — e e contada",
                RegistroDeAcesso.Falhas.total() == 1);

        // 5. E a falha ANTES de gravar — ler o status, resolver o ator —
        //    tambem nao sobe, e tambem e contada. E a unica parte que o
        //    RegistroDeAcesso nao alcanca.
        new ObservadorDeAcesso(atorQueQuebra(), new RegistroDeAcesso(sgdf))
                .afterCompletion(null, resposta(200), null, null);
        ok("RA-15 . a falha na fronteira, antes de gravar, tambem e engolida e contada",
                RegistroDeAcesso.Falhas.total() == 2
                        && RegistroDeAcesso.Falhas.ultimoMotivo().contains("na fronteira"));
        RegistroDeAcesso.Falhas.zerar();
    }

    /**
     * O caso mais traicoeiro: ESTA tabela quebrada, o resto do banco saudavel.
     *
     * <p>E o que dura meses. Com o banco inteiro fora do ar alguem percebe em
     * minutos; com uma restricao nova ou um tipo de array mudado so nesta
     * tabela, tudo o mais funciona e o relatorio de recertificacao apenas
     * encolhe. O contador em memoria morre no restart e nao atravessa
     * instancias — por isso a falha tambem vai a trilha, que tem as duas
     * propriedades.
     *
     * <p>A simulacao e literal: um CHECK que recusa tudo em `acesso_observado`,
     * com `log_auditoria` intacta ao lado.
     */
    static void aFalhaSoDestaTabelaDeixaMarcaDuravelNaTrilha(Sgdf sgdf) {
        RegistroDeAcesso.Falhas.zerar();
        // DELTA, E NUNCA CONTAGEM ABSOLUTA.
        //
        // `log_auditoria` e append-only por RULE (V002): o limpar() dos testes
        // NAO a apaga, e o DELETE e silenciosamente descartado. Uma assercao de
        // "== 1" aqui passa na primeira execucao da suite e falha em todas as
        // seguintes, com uma linha por rodada acumulada — e o sintoma aparece
        // longe da causa, derrubando este caso sempre que QUALQUER outro
        // quebrasse. Foi assim que este comentario nasceu.
        long antes = contar(sgdf, "log_auditoria WHERE acao = 'OBSERVAR_ACESSO'");
        long antesDeste = contar(sgdf, "log_auditoria WHERE acao = 'OBSERVAR_ACESSO' "
                + "AND resultado = 'ERRO' AND ator = 'durav'");
        Ator ator = new Ator("durav", "Fulano", Set.of(Papel.AUDITORIA), Set.of());

        executarSql(sgdf, "ALTER TABLE acesso_observado ADD CONSTRAINT quebra_deliberada "
                + "CHECK (false) NOT VALID");
        try {
            ok("RA-15 . com a tabela quebrada, a observacao devolve FALHOU",
                    new RegistroDeAcesso(sgdf).observar(ator, DENTRO)
                            == RegistroDeAcesso.Desfecho.FALHOU);
            ok("RA-15 . e a falha ficou na trilha, que sobrevive ao restart e e comum "
                            + "as instancias",
                    contar(sgdf, "log_auditoria WHERE acao = 'OBSERVAR_ACESSO' "
                            + "AND resultado = 'ERRO' AND ator = 'durav'") == antesDeste + 1);

            new RegistroDeAcesso(sgdf).observar(ator, DENTRO);
            new RegistroDeAcesso(sgdf).observar(ator, DENTRO);
            ok("RA-15 . mas so UMA vez por dia — um erro a cada requisicao afogaria a "
                            + "trilha append-only que ele existe para alimentar",
                    contar(sgdf, "log_auditoria WHERE acao = 'OBSERVAR_ACESSO'") == antes + 1);
            ok("RA-15 . enquanto o contador em memoria conta as tres",
                    RegistroDeAcesso.Falhas.total() == 3);
        } finally {
            // Sem transacao a desfazer aqui: a conexao esta em autocommit, que e
            // o que permite a INSERT falhada nao envenenar a escrita na trilha
            // logo em seguida — que e justamente o caso real sendo simulado.
            executarSql(sgdf, "ALTER TABLE acesso_observado DROP CONSTRAINT "
                    + "IF EXISTS quebra_deliberada");
            RegistroDeAcesso.Falhas.zerar();
        }
    }

    static void executarSql(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    /** Uma resposta que so sabe dizer o proprio status — que e tudo que o interceptor le. */
    static jakarta.servlet.http.HttpServletResponse resposta(int status) {
        return (jakarta.servlet.http.HttpServletResponse) java.lang.reflect.Proxy
                .newProxyInstance(TestesDeRecertificacao.class.getClassLoader(),
                        new Class<?>[] {jakarta.servlet.http.HttpServletResponse.class},
                        (proxy, metodo, args) -> {
                            if ("getStatus".equals(metodo.getName())) {
                                return status;
                            }
                            if ("hashCode".equals(metodo.getName())) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(metodo.getName())) {
                                return proxy == args[0];
                            }
                            if ("toString".equals(metodo.getName())) {
                                return "resposta(" + status + ")";
                            }
                            return null;
                        });
    }

    static AtorDaRequisicao atorFixo(Ator ator) {
        return new AtorDaRequisicao() {
            @Override
            public Ator atual() {
                return ator;
            }
        };
    }

    /** O contexto de seguranca ja saiu da thread — acontece, e nao pode virar 500. */
    static AtorDaRequisicao atorQueQuebra() {
        return new AtorDaRequisicao() {
            @Override
            public Ator atual() {
                throw new IllegalStateException("contexto de seguranca ausente na thread");
            }
        };
    }

    // -------------------------------------------------------------------------

    static ConsultaDeRecertificacao.Acesso acesso(String ator, List<String> papeis, int acoes,
                                                  int concessoes, int contratos) {
        return new ConsultaDeRecertificacao.Acesso(ator, papeis, contratos, DENTRO, DENTRO,
                concessoes, acoes, List.of(), acoes == 0 ? null : DENTRO);
    }

    static List<ConsultaDeRecertificacao.Acesso> relatorio(Sgdf sgdf) {
        return new ConsultaDeRecertificacao(sgdf).relatorio(DESDE, ATE);
    }

    static ConsultaDeRecertificacao.Acesso achar(List<ConsultaDeRecertificacao.Acesso> r,
                                                 String ator) {
        return r.stream().filter(a -> a.ator().equals(ator)).findFirst().orElse(null);
    }

    static void observar(Sgdf sgdf, String ator, LocalDate dia, List<String> papeis,
                         List<UUID> contratos) {
        new RegistroDeAcesso(sgdf).observar(new Ator(ator, ator,
                papeis.stream().map(Papel::valueOf)
                        .collect(java.util.stream.Collectors.toSet()),
                Set.copyOf(contratos)), dia);
    }

    static void acao(Sgdf sgdf, String ator, String papel, String acao) {
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, acao, "teste", MARCA + "-" + (++sequencia), Map.of()));
            return null;
        });
        // A trilha grava ocorrido_em = now(); o relatorio recorta por data, e o
        // fixture precisa cair dentro do trimestre de 2027.
        executar(sgdf, "UPDATE log_auditoria SET ator = ator WHERE false");
        executarDireto(sgdf, "ALTER TABLE log_auditoria DISABLE RULE log_auditoria_sem_update");
        executar(sgdf, "UPDATE log_auditoria SET ocorrido_em = TIMESTAMPTZ '" + DENTRO
                + " 12:00:00-03' WHERE objeto_tipo = 'teste' AND objeto_id LIKE '" + MARCA
                + "%'");
        executarDireto(sgdf, "ALTER TABLE log_auditoria ENABLE RULE log_auditoria_sem_update");
    }

    static void executarDireto(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static void executar(Sgdf sgdf, String sql) {
        executarDireto(sgdf, sql);
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
        try (Statement st = conexao.createStatement()) {
            st.execute("DELETE FROM acesso_observado");
            st.execute("ALTER TABLE log_auditoria DISABLE RULE log_auditoria_sem_delete");
            st.execute("DELETE FROM log_auditoria WHERE objeto_tipo = 'teste'");
            st.execute("ALTER TABLE log_auditoria ENABLE RULE log_auditoria_sem_delete");
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

    private TestesDeRecertificacao() {}
}
