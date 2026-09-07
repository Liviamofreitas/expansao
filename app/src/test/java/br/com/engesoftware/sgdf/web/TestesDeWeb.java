package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeTriagem;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Papel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Testes da fronteira HTTP — o criterio de aceite da F0-01 ponta a ponta.
 *
 * <p>O criterio e <i>"papel errado recebe 403 em endpoint protegido"</i>. Os
 * testes de {@code TestesDeAutorizacao} provam que o {@code Autorizador} decide
 * certo; estes provam que o controlador <b>pergunta</b> — e que pergunta
 * <i>antes</i> de consultar. As duas coisas falham de formas diferentes: um
 * autorizador correto que ninguem consulta nao protege nada, e um controlador
 * que consulta e so entao decide ja leu o dado que ia negar.
 *
 * <p>Nao sobem o Spring. Sobem o caminho que interessa: um JWT de verdade
 * traduzido em {@link Ator}, o controlador de verdade, e um banco que explode se
 * for tocado quando nao devia.
 */
public final class TestesDeWeb {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    static final UUID CONTRATO_DO_ATOR = UUID.randomUUID();
    static final UUID CONTRATO_ALHEIO = UUID.randomUUID();
    static final UUID CICLO = UUID.randomUUID();

    public static void main(String[] args) {
        executar("grupoDesconhecidoRecebe403", TestesDeWeb::grupoDesconhecidoRecebe403);
        executar("semTokenRecebe403", TestesDeWeb::semTokenRecebe403);
        executar("papelErradoRecebe403NoEndpoint", TestesDeWeb::papelErradoRecebe403NoEndpoint);
        executar("cicloDeOutroContratoRecebe403", TestesDeWeb::cicloDeOutroContratoRecebe403);
        executar("cicloInexistenteRecebeOMesmo403",
                TestesDeWeb::cicloInexistenteRecebeOMesmo403);
        executar("negarNaoConsultaOBanco", TestesDeWeb::negarNaoConsultaOBanco);
        executar("papelCertoVeOPainel", TestesDeWeb::papelCertoVeOPainel);
        executar("bloqueioExibeMotivo", TestesDeWeb::bloqueioExibeMotivo);
        executar("triagemEscondeConteudoProfissional",
                TestesDeWeb::triagemEscondeConteudoProfissional);
        executar("nomeDeArquivoSaiMascarado", TestesDeWeb::nomeDeArquivoSaiMascarado);
        executar("tokenTraduzGrupos", TestesDeWeb::tokenTraduzGrupos);
        executar("tokenSemSujeitoNaoAutentica", TestesDeWeb::tokenSemSujeitoNaoAutentica);
        executar("contratoMalFormadoNoTokenNaoDaAcesso",
                TestesDeWeb::contratoMalFormadoNoTokenNaoDaAcesso);
        executar("oMotivoNaoVaiNoCorpo", TestesDeWeb::oMotivoNaoVaiNoCorpo);
        executar("escritaDaTriagemTambemEAutorizada",
                TestesDeWeb::escritaDaTriagemTambemEAutorizada);
        executar("oCicloTemDuasPermissoesDiferentes",
                TestesDeWeb::oCicloTemDuasPermissoesDiferentes);
        executar("oIndicadorAgregadoNaoSaiRecortado",
                TestesDeWeb::oIndicadorAgregadoNaoSaiRecortado);
        executar("aTrilhaSoAbreParaAuditoria", TestesDeWeb::aTrilhaSoAbreParaAuditoria);
        executar("ligarPedeMaisQueDesligar", TestesDeWeb::ligarPedeMaisQueDesligar);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    // --- o 403 do criterio de aceite -----------------------------------------

    /**
     * Um grupo do diretorio que nao corresponde a papel do cap. 15.1 nao vira
     * acesso nenhum. E o caso realista: alguem entra num grupo novo do AD e
     * ninguem mapeou o papel.
     */
    static void grupoDesconhecidoRecebe403() {
        Ator ator = AtorDaRequisicao.de(token("u1", List.of("FINANCEIRO-GERAL"), List.of()));
        ok("F0-01 . grupo desconhecido nao vira papel", ator.papeis().isEmpty());
        ok("F0-01 . e o endpoint devolve 403",
                negou(() -> triagemControlador(ator, bancoQueExplode()).fila(50)));
    }

    /** Sem autenticacao no contexto, o ator e nulo e o endpoint nega. */
    static void semTokenRecebe403() {
        ok("F0-01 . requisicao sem token recebe 403",
                negou(() -> triagemControlador(null, bancoQueExplode()).fila(50)));
    }

    /**
     * O criterio literal: papel valido, endpoint protegido, permissao que este
     * papel nao tem. O GESTOR_CONTRATO registra ateste; nao tria.
     */
    static void papelErradoRecebe403NoEndpoint() {
        Ator gestor = ator("gestor", Papel.GESTOR_CONTRATO, CONTRATO_DO_ATOR);
        ok("F0-01 . GESTOR_CONTRATO nao tria e recebe 403",
                negou(() -> triagemControlador(gestor, bancoQueExplode()).fila(50)));

        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        ok("F0-01 . mas o PUBLICADOR_FIN, que tria, passa",
                triagemControlador(financeiro, bancoComFila()).fila(50).size() == 1);
    }

    /**
     * O recorte por contrato do cap. 15.1 — e a razao de o contrato vir do
     * ciclo. Se viesse do parametro, este teste passaria omitindo-o.
     */
    static void cicloDeOutroContratoRecebe403() {
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        ConexaoDeMentira banco = bancoQueExplode()
                .respondendo("FROM ciclo WHERE id", linha(CONTRATO_ALHEIO));
        ok("Cap. 15.1 . ciclo de contrato alheio recebe 403",
                negou(() -> controlador(financeiro, banco).painel(CICLO)));
    }

    /**
     * Ciclo que nao existe responde igual a ciclo alheio. Um 404 aqui contaria a
     * quem nao pode ver o ciclo se ele existe — e a existencia e metade do que
     * se esta protegendo.
     */
    static void cicloInexistenteRecebeOMesmo403() {
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        ConexaoDeMentira banco = bancoQueExplode().respondendo("FROM ciclo WHERE id");
        ok("Cap. 15.1 . ciclo inexistente recebe 403, nao 404",
                negou(() -> controlador(financeiro, banco).painel(CICLO)));
    }

    /**
     * A ordem. O banco de mentira explode em qualquer consulta que o teste nao
     * registrou: se o controlador consultasse os estados do ciclo antes de
     * decidir, este teste falharia com AssertionError em vez de AcessoNegado.
     */
    static void negarNaoConsultaOBanco() {
        Ator gestor = ator("gestor", Papel.GESTOR_CONTRATO, CONTRATO_DO_ATOR);
        ConexaoDeMentira banco = bancoQueExplode();
        negou(() -> triagemControlador(gestor, banco).fila(50));
        ok("F0-01 . negar nao le o dado que estava negando",
                banco.consultasFeitas().isEmpty());

        ConexaoDeMentira comCiclo = bancoQueExplode()
                .respondendo("FROM ciclo WHERE id", linha(CONTRATO_ALHEIO));
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        negou(() -> controlador(financeiro, comCiclo).painel(CICLO));
        ok("F0-01 . do ciclo negado le-se so o contrato, nunca o conteudo",
                comCiclo.consultasFeitas().size() == 1
                        && comCiclo.consultasFeitas().get(0).contains("contrato_servico_id"));
    }

    // --- o caminho permitido -------------------------------------------------

    static void papelCertoVeOPainel() {
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        ConexaoDeMentira banco = bancoQueExplode()
                .respondendo("FROM ciclo WHERE id", linha(CONTRATO_DO_ATOR))
                .respondendo("FROM exigencia\nWHERE", linha("PENDENTE", 3),
                        linha("PUBLICADO", 7))
                .respondendo("FROM exigencia e")
                .respondendo("FROM conciliacao");
        PainelController.Painel painel = controlador(financeiro, banco).painel(CICLO);
        ok("F1-07 . a barra segmentada reflete os estados gravados",
                painel.estados().equals(Map.of("PENDENTE", 3, "PUBLICADO", 7)));
        ok("F1-07 . sem bloqueio, o ciclo pode publicar", painel.podePublicar());
        ok("Cap. 16 . o painel diz de quem e a sessao", "Fulano".equals(painel.ator()));
    }

    static void bloqueioExibeMotivo() {
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        ConexaoDeMentira banco = bancoQueExplode()
                .respondendo("FROM ciclo WHERE id", linha(CONTRATO_DO_ATOR))
                .respondendo("FROM exigencia\nWHERE", linha("PENDENTE", 3))
                .respondendo("FROM exigencia e", linha("CND_FEDERAL", "AUSENTE", 2))
                .respondendo("FROM conciliacao", linha("R01", "guia sem comprovante"));
        PainelController.Painel painel = controlador(financeiro, banco).painel(CICLO);
        ok("F1-07 . bloqueio de publicacao exibe motivo", !painel.podePublicar());
        ok("F1-07 . o motivo da exigencia nomeia o tipo e o estado",
                painel.bloqueios().get(0).contains("CND_FEDERAL")
                        && painel.bloqueios().get(0).contains("AUSENTE"));
        ok("F1-07 . e o motivo da conciliacao vem junto, porque quem resolve e outro",
                painel.bloqueios().stream().anyMatch(m -> m.contains("guia sem comprovante")));
    }

    /**
     * A fila mostra que o documento existe; abrir o conteudo com dado pessoal e
     * outra decisao. O PUBLICADOR_FIN nao ve escopo profissional (cap. 15.1).
     */
    static void triagemEscondeConteudoProfissional() {
        List<TriagemController.ItemDaFila> paraFin =
                triagemControlador(ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR),
                        bancoComFila()).fila(50);
        ok("Cap. 15.1 . o financeiro ve a exigencia pendente", paraFin.size() == 1);
        ok("Cap. 15.1 . mas nao o conteudo do contracheque",
                !paraFin.get(0).conteudoVisivel());
        ok("SEC-02 . e o nome do arquivo da fila tambem sai mascarado",
                !paraFin.get(0).nomeArquivo().contains("052.190.471-40"));

        List<TriagemController.ItemDaFila> paraAp =
                triagemControlador(ator("ap", Papel.PUBLICADOR_AP, CONTRATO_DO_ATOR),
                        bancoComFila()).fila(50);
        ok("Cap. 15.1 . quem trata exigencia trabalhista ve",
                paraAp.get(0).conteudoVisivel());
    }

    /**
     * SEC-02. A massa real mostrou nome de arquivo digitado por gente; um CPF
     * ali sai pela fronteira sem passar por lugar nenhum que o mascare.
     */
    static void nomeDeArquivoSaiMascarado() {
        ConexaoDeMentira banco = bancoQueExplode().respondendo("tipo_id IS NULL",
                linha(UUID.randomUUID(), "052.190.471-40 folha.pdf",
                        "/contratos/052.190.471-40/", "-", 0, "-"));
        List<PainelController.ItemDeTriagem> lista =
                controlador(ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR), banco)
                        .desconhecidos(50);
        ok("SEC-02 . o CPF no nome do arquivo sai mascarado",
                lista.get(0).nomeArquivo().contains("***.190.471-**")
                        && !lista.get(0).nomeArquivo().contains("052.190.471-40"));
        ok("SEC-02 . e o do caminho tambem",
                !lista.get(0).caminho().contains("052.190.471-40"));
    }

    // --- a traducao do token -------------------------------------------------

    static void tokenTraduzGrupos() {
        Ator daLista = AtorDaRequisicao.de(token("u2",
                List.of("PUBLICADOR_FIN", "GRUPO-QUE-NAO-EXISTE"), List.of()));
        ok("F0-01 . grupo conhecido vira papel e o resto e descartado",
                daLista.papeis().equals(Set.of(Papel.PUBLICADOR_FIN)));

        Jwt comString = Jwt.withTokenValue("t").header("alg", "RS256").subject("u3")
                .claim(AtorDaRequisicao.CLAIM_GRUPOS, "publicador-ap auditoria").build();
        ok("F0-01 . o IdP que publica grupos como texto e lido igual",
                AtorDaRequisicao.de(comString).papeis()
                        .equals(Set.of(Papel.PUBLICADOR_AP, Papel.AUDITORIA)));
    }

    static void tokenSemSujeitoNaoAutentica() {
        Jwt semSujeito = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim(AtorDaRequisicao.CLAIM_GRUPOS, List.of("AUDITORIA")).build();
        ok("Cap. 16 . token sem sujeito nao vira ator — a trilha exige quem",
                AtorDaRequisicao.de(semSujeito) == null);
    }

    static void contratoMalFormadoNoTokenNaoDaAcesso() {
        Ator ator = AtorDaRequisicao.de(token("u4", List.of("PUBLICADOR_FIN"),
                List.of("nao-e-uuid", CONTRATO_DO_ATOR.toString())));
        ok("F0-01 . contrato mal formado no token e descartado",
                ator.contratos().equals(Set.of(CONTRATO_DO_ATOR)));
        ConexaoDeMentira banco = bancoQueExplode()
                .respondendo("FROM ciclo WHERE id", linha(CONTRATO_ALHEIO));
        ok("F0-01 . e nao abre o recorte para os outros",
                negou(() -> controlador(ator, banco).painel(CICLO)));
    }

    /**
     * O motivo explica o 403 no log, para quem investiga. No corpo da resposta
     * ele confirmaria a existencia do contrato a quem nao pode ve-lo — por isso
     * a anotacao nao carrega {@code reason}.
     */
    static void oMotivoNaoVaiNoCorpo() {
        ResponseStatus anotacao = AcessoNegado.class.getAnnotation(ResponseStatus.class);
        ok("F0-01 . AcessoNegado e 403", anotacao != null
                && anotacao.value() == HttpStatus.FORBIDDEN);
        ok("F0-01 . e nao devolve o motivo no corpo",
                anotacao != null && anotacao.reason().isEmpty());
        ok("Cap. 16 . mas o motivo existe, para o log",
                new AcessoNegado("fora do escopo").getMessage().contains("escopo"));
    }

    /**
     * F1-06 . a escrita e autorizada pelo contrato DA CANDIDATURA.
     *
     * <p>Ler a fila recortada nao protege a escrita: quem souber o UUID de uma
     * candidatura de outro contrato a confirmaria pela URL. O alvo vem do dado,
     * como no painel — e candidatura inexistente responde igual a candidatura
     * alheia, pela mesma razao do 403-em-vez-de-404.
     */
    static void escritaDaTriagemTambemEAutorizada() {
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        UUID candidatura = UUID.randomUUID();

        ConexaoDeMentira alheia = bancoQueExplode()
                .respondendo("FROM   candidatura c", linha(CONTRATO_ALHEIO));
        ok("F1-06 . confirmar candidatura de contrato alheio recebe 403",
                negou(() -> triagemControlador(financeiro, alheia).confirmar(candidatura)));
        ok("F0-01 . e nada alem do contrato foi lido antes de negar",
                alheia.consultasFeitas().size() == 1);

        ConexaoDeMentira inexistente = bancoQueExplode()
                .respondendo("FROM   candidatura c");
        ok("F1-06 . candidatura inexistente recebe 403, nao 404",
                negou(() -> triagemControlador(financeiro, inexistente)
                        .ilegivel(candidatura, new TriagemController.Justificativa(
                                "esta ilegivel de verdade"))));

        Ator gestor = ator("gestor", Papel.GESTOR_CONTRATO, CONTRATO_DO_ATOR);
        ConexaoDeMentira minha = bancoQueExplode()
                .respondendo("FROM   candidatura c", linha(CONTRATO_DO_ATOR));
        ok("F0-01 . GESTOR_CONTRATO nao tria, nem pela escrita",
                negou(() -> triagemControlador(gestor, minha).confirmar(candidatura)));
    }

    // --- ciclo (F3-03, F3-04) -------------------------------------------------

    /**
     * Ateste e conducao do ciclo sao permissoes DIFERENTES, e a diferenca e do
     * cap. 15.1.
     *
     * <p>O GESTOR_CONTRATO "registra ateste; acompanha seu ciclo" — e nao move o
     * ciclo. Uma permissao so para as duas coisas o deixaria FECHAR o ciclo, que
     * nao e o que o capitulo lhe da. O PUBLICADOR_FIN e o inverso: conduz e nao
     * atesta.
     */
    static void oCicloTemDuasPermissoesDiferentes() {
        Ator gestor = ator("gestor", Papel.GESTOR_CONTRATO, CONTRATO_DO_ATOR);
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);

        ok("Cap. 15.1 . o GESTOR_CONTRATO nao move o ciclo",
                negou(() -> cicloControlador(gestor, bancoDoCiclo(CONTRATO_DO_ATOR))
                        .mover(CICLO, new CicloController.Movimento("FECHADO",
                                "encerrando a competencia por conta propria"))));
        ok("Cap. 15.1 . e o PUBLICADOR_FIN nao registra o ateste",
                negou(() -> cicloControlador(financeiro, bancoDoCiclo(CONTRATO_DO_ATOR))
                        .ateste(CICLO, new CicloController.Ateste(
                                java.time.OffsetDateTime.now(), "e-mail", null))));

        ConexaoDeMentira alheio = bancoDoCiclo(CONTRATO_ALHEIO);
        ok("F0-01 . e o ciclo de outro contrato recebe 403 mesmo com o papel certo",
                negou(() -> cicloControlador(financeiro, alheio)
                        .mover(CICLO, new CicloController.Movimento("EM_COLETA",
                                "iniciando a varredura da competencia"))));
        ok("F0-01 . tendo lido so o contrato antes de negar",
                alheio.consultasFeitas().size() == 1);

        ok("F0-01 . ciclo inexistente recebe o mesmo 403, nao 404",
                negou(() -> cicloControlador(financeiro,
                        bancoQueExplode().respondendo("FROM ciclo WHERE id"))
                        .estado(CICLO)));
    }

    /**
     * O indicador do cap. 21 e agregado da competencia.
     *
     * <p>Filtra-lo pelos contratos do ator daria um percentual diferente com o
     * mesmo nome — e dois numeros chamados "D+3" e pior que um so. Quem tem
     * recorte nao recebe esta visao; recebe o painel do seu ciclo.
     */
    static void oIndicadorAgregadoNaoSaiRecortado() {
        Ator recortado = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        ok("Cap. 21 . ator com recorte de contrato nao recebe o agregado",
                negou(() -> cicloControlador(recortado, bancoQueExplode()).d3("2026-06")));

        Ator daf = ator("daf", Papel.APROVADOR_DAF);
        ConexaoDeMentira banco = bancoQueExplode()
                // O fragmento precisa distinguir as DUAS consultas: ambas dizem
                // "FROM   ciclo", e casar pelo prefixo faria o indicador
                // responder tambem a consulta por contrato — com tres colunas
                // onde ela le seis.
                .respondendo("count(*) FILTER",
                        linha(2, 2, new java.math.BigDecimal("1.50")))
                .respondendo("JOIN   contrato_servico");
        Map<String, Object> visao = cicloControlador(daf, banco).d3("2026-06");
        ok("Cap. 21 . e quem tem visao global recebe o percentual e a meta",
                "100.00".equals(visao.get("percentual"))
                        && Boolean.TRUE.equals(visao.get("atinge_a_meta")));
    }

    /**
     * A permissao AUDITAR passa a ter endpoint — e so ela abre a trilha.
     *
     * <p>Uma permissao sem uso e pior que uma permissao faltando: ela aparece
     * na matriz de acesso, passa na recertificacao e nao da acesso a nada. A
     * organizacao acredita ter um controle que nao tem.
     */
    static void aTrilhaSoAbreParaAuditoria() {
        Ator auditoria = ator("aud", Papel.AUDITORIA);
        Ator daf = ator("daf", Papel.APROVADOR_DAF);
        Ator admin = ator("adm", Papel.ADMIN_SISTEMA);

        ok("Cap. 15.1 . o APROVADOR_DAF nao le a trilha",
                negou(() -> indicadorControlador(daf, bancoQueExplode())
                        .trilha("ciclo", null, null, null, null, 10)));
        ok("Cap. 15.1 . nem o ADMIN_SISTEMA — visao global de configuracao nao e "
                        + "visao global de trilha",
                negou(() -> indicadorControlador(admin, bancoQueExplode())
                        .trilha("ciclo", null, null, null, null, 10)));

        ConexaoDeMentira banco = bancoQueExplode().respondendo("FROM   log_auditoria");
        ok("Cap. 13 . e a AUDITORIA le",
                indicadorControlador(auditoria, banco)
                        .trilha("ciclo", null, null, null, null, 10).isEmpty());

        // O indicador e a trilha sao permissoes diferentes: quem audita ve os
        // dois, quem aprova excecao ve so o indicador.
        ConexaoDeMentira comIndicadores = bancoQueExplode()
                .respondendo("count(*) FILTER", linha(0, 0))
                .respondendo("SELECT count(*), avg", linha(0, null))
                .respondendo("FROM   exigencia e", linha(0, 0))
                .respondendo("FROM   pendencia p", linha(0))
                .respondendo("FROM   excecao x", linha(0))
                .respondendo("FROM   conciliacao k", linha(0))
                .respondendo("SELECT count(*), min", linha(0, null))
                .respondendo("FROM   candidatura k", linha(0, 0))
                .respondendo("FROM   book b", linha(0));
        ok("Cap. 21 . o APROVADOR_DAF ve os indicadores, que sao outra permissao",
                indicadorControlador(daf, comIndicadores).daCompetencia("2026-06").size() == 9);
    }

    /**
     * Ligar a cobranca pede CONFIGURAR_SISTEMA; desligar pede so ver o painel.
     *
     * <p>A assimetria e deliberada: ligar faz o sistema comecar a mandar e-mail
     * para pessoas, desligar apenas devolve o contrato ao estado seguro. Exigir
     * o mesmo papel nas duas pontas criaria a situacao em que quem percebe o
     * problema nao pode para-lo — e a primeira coisa que se quer numa
     * emergencia e a porta de saida aberta.
     */
    static void ligarPedeMaisQueDesligar() {
        Ator financeiro = ator("fin", Papel.PUBLICADOR_FIN, CONTRATO_DO_ATOR);
        Ator admin = ator("adm", Papel.ADMIN_SISTEMA);
        UUID contrato = UUID.randomUUID();

        ok("F3-02 . o PUBLICADOR_FIN nao liga a cobranca",
                negou(() -> notificacaoControlador(financeiro, bancoQueExplode())
                        .ativar(contrato, new NotificacaoController.Decisao(
                                "motivo suficientemente longo para passar"))));

        ConexaoDeMentira desliga = bancoQueExplode()
                .respondendo("INSERT INTO parametro")
                .respondendo("INSERT INTO log_auditoria")
                .respondendo("SELECT valor #>> ", linha("false"));
        ok("F3-02 . mas DESLIGA — a porta de saida fica aberta para quem opera",
                Boolean.FALSE.equals(notificacaoControlador(financeiro, desliga)
                        .desativar(contrato).get("envio_ativo")));

        // O ADMIN tem o papel de ligar e ainda assim e barrado: pelo transporte,
        // que e a barreira que nenhum papel contorna.
        ok("F3-02 . e nem o ADMIN_SISTEMA liga sem transporte",
                recusouPorFaltaDeTransporte(() -> notificacaoControlador(admin,
                        bancoQueExplode().respondendo("INSERT INTO log_auditoria"))
                        .ativar(contrato, new NotificacaoController.Decisao(
                                "motivo suficientemente longo para passar"))));
    }

    static boolean recusouPorFaltaDeTransporte(Runnable acao) {
        try {
            acao.run();
            return false;
        } catch (br.com.engesoftware.sgdf.persistencia.RepositorioDeParametro.SemTransporte e) {
            return true;
        }
    }

    // -------------------------------------------------------------------------

    static NotificacaoController notificacaoControlador(Ator ator, ConexaoDeMentira banco) {
        Sgdf sgdf = new Sgdf(banco.conexao());
        return new NotificacaoController(atores(ator),
                new br.com.engesoftware.sgdf.persistencia.RepositorioDeParametro(sgdf),
                new ConsultaDoPainel(sgdf));
    }

    static IndicadorController indicadorControlador(Ator ator, ConexaoDeMentira banco) {
        Sgdf sgdf = new Sgdf(banco.conexao());
        return new IndicadorController(atores(ator),
                new br.com.engesoftware.sgdf.persistencia.ConsultaDeIndicadores(sgdf),
                new br.com.engesoftware.sgdf.persistencia.ConsultaDeAuditoria(sgdf),
                new br.com.engesoftware.sgdf.persistencia.ConsultaDeRecertificacao(sgdf));
    }

    static CicloController cicloControlador(Ator ator, ConexaoDeMentira banco) {
        Sgdf sgdf = new Sgdf(banco.conexao());
        return new CicloController(atores(ator),
                new br.com.engesoftware.sgdf.persistencia.RepositorioDeCiclo(sgdf),
                new ConsultaDoPainel(sgdf));
    }

    /** So a consulta do contrato do ciclo responde: negar nao deve ler mais nada. */
    static ConexaoDeMentira bancoDoCiclo(UUID contrato) {
        return bancoQueExplode().respondendo("FROM ciclo WHERE id", linha(contrato));
    }

    static PainelController controlador(Ator ator, ConexaoDeMentira banco) {
        Sgdf sgdf = new Sgdf(banco.conexao());
        return new PainelController(atores(ator), new ConsultaDoPainel(sgdf),
                new br.com.engesoftware.sgdf.persistencia.RepositorioDeOrganizacao(sgdf));
    }

    static AtorDaRequisicao atores(Ator ator) {
        return new AtorDaRequisicao() {
            @Override
            public Ator atual() {
                return ator;
            }
        };
    }

    /** O contracheque na fila: candidatura aberta, escopo profissional. */
    static ConexaoDeMentira bancoComFila() {
        return bancoQueExplode().respondendo("FROM   fila_de_triagem",
                linha(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        CONTRATO_DO_ATOR, "2026-06",
                        "052.190.471-40 1041601__CONTRACHEQUE.pdf", "/rh/06.2026/",
                        "CONTRACHEQUE", "PESSOAL", 0.72, "margem de 0,04 sobre o segundo"));
    }

    static TriagemController triagemControlador(Ator ator, ConexaoDeMentira banco) {
        return new TriagemController(atores(ator),
                new RepositorioDeTriagem(new Sgdf(banco.conexao())));
    }

    /** Nenhuma resposta registrada: qualquer consulta e uma falha do teste. */
    static ConexaoDeMentira bancoQueExplode() {
        return new ConexaoDeMentira();
    }

    static Object[] linha(Object... valores) {
        return valores;
    }

    static Ator ator(String id, Papel papel, UUID contrato) {
        return new Ator(id, "Fulano", Set.of(papel), Set.of(contrato));
    }

    /** Papel de visao global: sem recorte de contrato (cap. 15.1). */
    static Ator ator(String id, Papel papel) {
        return new Ator(id, "Fulano", Set.of(papel), Set.of());
    }

    static Jwt token(String sujeito, List<String> grupos, List<String> contratos) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(sujeito)
                .claim("name", "Fulano")
                .claim(AtorDaRequisicao.CLAIM_GRUPOS, grupos)
                .claim(AtorDaRequisicao.CLAIM_CONTRATOS, contratos)
                .build();
    }

    /** Roda e diz se o endpoint negou — qualquer outra excecao propaga. */
    static boolean negou(Runnable chamada) {
        try {
            chamada.run();
            return false;
        } catch (AcessoNegado e) {
            return true;
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

    private TestesDeWeb() {}
}
