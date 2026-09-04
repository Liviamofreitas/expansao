package br.com.engesoftware.sgdf.seguranca;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Testes da autorizacao do cap. 15.1.
 *
 * <p>Metade destes testes verifica a coluna <b>"nao pode"</b> da tabela do
 * capitulo, que e tao normativa quanto a coluna "pode" e a que costuma ficar
 * sem teste. O criterio de aceite da F0-01 e exatamente esse: <i>"papel errado
 * recebe 403 em endpoint protegido"</i>.
 */
public final class TestesDeAutorizacao {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    static final UUID CONTRATO_A = UUID.randomUUID();
    static final UUID CONTRATO_B = UUID.randomUUID();

    public static void main(String[] args) {
        executar("negarEOPadrao", TestesDeAutorizacao::negarEOPadrao);
        executar("oQueCadaPapelPode", TestesDeAutorizacao::oQueCadaPapelPode);
        executar("adminNaoVeConteudoProfissional",
                TestesDeAutorizacao::adminNaoVeConteudoProfissional);
        executar("curadorNaoAprovaExcecao", TestesDeAutorizacao::curadorNaoAprovaExcecao);
        executar("segregacaoDeFuncoes", TestesDeAutorizacao::segregacaoDeFuncoes);
        executar("recorteporContrato", TestesDeAutorizacao::recorteporContrato);
        executar("auditoriaNaoEscreve", TestesDeAutorizacao::auditoriaNaoEscreve);
        executar("financeiroNaoVeEscopoProfissional",
                TestesDeAutorizacao::financeiroNaoVeEscopoProfissional);
        executar("contaDeServicoFazUmaCoisaSo",
                TestesDeAutorizacao::contaDeServicoFazUmaCoisaSo);
        executar("grupoDesconhecidoNaoViraLeitura",
                TestesDeAutorizacao::grupoDesconhecidoNaoViraLeitura);
        executar("atorSemIdentificadorNaoExiste",
                TestesDeAutorizacao::atorSemIdentificadorNaoExiste);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    /**
     * Uma operacao nova e invisivel para todos os papeis ate alguem declara-la.
     * Isso forca a decisao a passar por revisao em vez de aparecer por omissao.
     */
    static void negarEOPadrao() {
        ok("F0-01 . sem ator, nada é permitido",
                Autorizador.pode(null, Permissao.VER_PAINEL).negada());

        Ator semPapel = new Ator("fulano", "Fulano", Set.of(), Set.of());
        var d = Autorizador.pode(semPapel, Permissao.VER_PAINEL);
        ok("F0-01 . ator sem papel reconhecido não vê nada", d.negada());
        ok("F0-01 . e o motivo explica que omissão não vira leitura",
                d.motivo().contains("não vira acesso de leitura"));
    }

    static void oQueCadaPapelPode() {
        ok("Cap. 15.1 . o curador cadastra e publica a matriz",
                Autorizador.pode(com(Papel.CURADOR_MATRIZ), Permissao.CADASTRAR).permitida()
                        && Autorizador.pode(com(Papel.CURADOR_MATRIZ),
                                Permissao.PUBLICAR_MATRIZ).permitida());
        ok("Cap. 15.1 . o gestor registra o ateste",
                Autorizador.pode(com(Papel.GESTOR_CONTRATO),
                        Permissao.REGISTRAR_ATESTE).permitida());
        ok("Cap. 15.1 . o aprovador DAF aprova exceção e altera criticidade",
                Autorizador.pode(com(Papel.APROVADOR_DAF), Permissao.APROVAR_EXCECAO).permitida()
                        && Autorizador.pode(com(Papel.APROVADOR_DAF),
                                Permissao.ALTERAR_CRITICIDADE).permitida());
        ok("Cap. 15.1 . a auditoria lê a trilha",
                Autorizador.pode(com(Papel.AUDITORIA), Permissao.AUDITAR).permitida());
        ok("Cap. 15.1 . o admin configura o sistema",
                Autorizador.pode(com(Papel.ADMIN_SISTEMA),
                        Permissao.CONFIGURAR_SISTEMA).permitida());
    }

    /** "Nao pode: ver conteudo de documentos de escopo profissional." */
    static void adminNaoVeConteudoProfissional() {
        Ator admin = com(Papel.ADMIN_SISTEMA);
        ok("Cap. 15.1 . o admin vê o painel",
                Autorizador.pode(admin, Permissao.VER_PAINEL).permitida());

        var d = Autorizador.pode(admin, Permissao.VER_CONTEUDO_DOCUMENTO,
                Autorizador.Alvo.profissional(CONTRATO_A));
        ok("Cap. 15.1 . mas não abre documento de escopo profissional", d.negada());
        ok("Cap. 15.1 . e o motivo cita o dado pessoal",
                d.motivo().contains("dado pessoal"));

        ok("Cap. 15.1 . administrar o sistema não é aprovar exceção",
                Autorizador.pode(admin, Permissao.APROVAR_EXCECAO).negada());
    }

    /** "Nao pode: aprovar excecao; alterar criticidade bloqueante." */
    static void curadorNaoAprovaExcecao() {
        Ator curador = com(Papel.CURADOR_MATRIZ);
        ok("Cap. 15.1 . o curador não aprova exceção",
                Autorizador.pode(curador, Permissao.APROVAR_EXCECAO).negada());
        ok("Cap. 15.1 . nem altera criticidade bloqueante",
                Autorizador.pode(curador, Permissao.ALTERAR_CRITICIDADE).negada());
        ok("Cap. 15.1 . mas publica o book",
                Autorizador.pode(curador, Permissao.PUBLICAR_BOOK).permitida());
    }

    /** "Nao pode: aprovar excecao que ele mesmo solicitou (SoD)." */
    static void segregacaoDeFuncoes() {
        Ator daf = new Ator("maria", "Maria", Set.of(Papel.APROVADOR_DAF), Set.of());

        ok("F0-09 . o aprovador aprova a exceção de outro",
                Autorizador.pode(daf, Permissao.APROVAR_EXCECAO,
                        Autorizador.Alvo.excecaoDe("joao")).permitida());

        var propria = Autorizador.pode(daf, Permissao.APROVAR_EXCECAO,
                Autorizador.Alvo.excecaoDe("maria"));
        ok("F0-09 . mas não a própria", propria.negada());
        ok("F0-09 . e o motivo diz o que fazer",
                propria.motivo().contains("Outro APROVADOR_DAF"));
    }

    /** "Nao pode: acessar contratos fora do seu escopo." */
    static void recorteporContrato() {
        Ator publicador = new Ator("ana", "Ana", Set.of(Papel.PUBLICADOR_FIN),
                Set.of(CONTRATO_A));

        ok("Cap. 15.1 . o publicador vê o contrato dele",
                Autorizador.pode(publicador, Permissao.VER_CONTEUDO_DOCUMENTO,
                        Autorizador.Alvo.doContrato(CONTRATO_A)).permitida());

        var fora = Autorizador.pode(publicador, Permissao.VER_CONTEUDO_DOCUMENTO,
                Autorizador.Alvo.doContrato(CONTRATO_B));
        ok("Cap. 15.1 . e não vê o de fora do escopo", fora.negada());
        ok("Cap. 15.1 . o motivo nomeia o contrato",
                fora.motivo().contains(CONTRATO_B.toString()));

        // Visao global nao tem recorte.
        Ator auditoria = new Ator("bruno", "Bruno", Set.of(Papel.AUDITORIA), Set.of());
        ok("Cap. 15.1 . a auditoria tem visão global",
                Autorizador.pode(auditoria, Permissao.VER_CONTEUDO_DOCUMENTO,
                        Autorizador.Alvo.doContrato(CONTRATO_B)).permitida());
    }

    /** "Nao pode: qualquer escrita." */
    static void auditoriaNaoEscreve() {
        Ator auditoria = com(Papel.AUDITORIA);
        boolean escreveAlgo = Autorizador.pode(auditoria, Permissao.CADASTRAR).permitida()
                || Autorizador.pode(auditoria, Permissao.TRIAR).permitida()
                || Autorizador.pode(auditoria, Permissao.PUBLICAR_BOOK).permitida()
                || Autorizador.pode(auditoria, Permissao.APROVAR_EXCECAO).permitida()
                || Autorizador.pode(auditoria, Permissao.REGISTRAR_ATESTE).permitida()
                || Autorizador.pode(auditoria, Permissao.SOLICITAR_EXCECAO).permitida();
        ok("Cap. 15.1 . a auditoria não escreve nada", !escreveAlgo);
        ok("Cap. 15.1 . mas lê inclusive escopo profissional",
                Autorizador.pode(auditoria, Permissao.VER_CONTEUDO_DOCUMENTO,
                        Autorizador.Alvo.profissional(CONTRATO_A)).permitida());
    }

    /** "Nao pode: documentos de escopo profissional fora da conciliacao." */
    static void financeiroNaoVeEscopoProfissional() {
        Ator fin = new Ator("carlos", "Carlos", Set.of(Papel.PUBLICADOR_FIN),
                Set.of(CONTRATO_A));

        ok("Cap. 15.1 . o publicador financeiro abre documento fiscal",
                Autorizador.pode(fin, Permissao.VER_CONTEUDO_DOCUMENTO,
                        Autorizador.Alvo.doContrato(CONTRATO_A)).permitida());
        ok("Cap. 15.1 . e não abre o de escopo profissional",
                Autorizador.pode(fin, Permissao.VER_CONTEUDO_DOCUMENTO,
                        Autorizador.Alvo.profissional(CONTRATO_A)).negada());

        // O de AP ve, porque a exigencia trabalhista e o trabalho dele.
        Ator ap = new Ator("diana", "Diana", Set.of(Papel.PUBLICADOR_AP), Set.of(CONTRATO_A));
        ok("Cap. 15.1 . o publicador de AP vê, porque é o trabalho dele",
                Autorizador.pode(ap, Permissao.VER_CONTEUDO_DOCUMENTO,
                        Autorizador.Alvo.profissional(CONTRATO_A)).permitida());
    }

    /** "Nao pode: qualquer outra operacao; login interativo negado." */
    static void contaDeServicoFazUmaCoisaSo() {
        Ator leitura = com(Papel.SVC_LEITURA);
        Ator publica = com(Papel.SVC_PUBLICA);

        ok("Cap. 15.1 . svc-leitura lê o conteúdo",
                Autorizador.pode(leitura, Permissao.VER_CONTEUDO_DOCUMENTO).permitida());
        ok("Cap. 15.1 . e não faz mais nada",
                Autorizador.pode(leitura, Permissao.VER_PAINEL).negada()
                        && Autorizador.pode(leitura, Permissao.PUBLICAR_BOOK).negada());
        ok("Cap. 15.1 . svc-publica publica o book",
                Autorizador.pode(publica, Permissao.PUBLICAR_BOOK).permitida());
        ok("Cap. 15.1 . e não lê conteúdo",
                Autorizador.pode(publica, Permissao.VER_CONTEUDO_DOCUMENTO).negada());
        ok("Cap. 15.1 . contas de serviço são reconhecidas como tal",
                leitura.deServico() && publica.deServico() && !com(Papel.AUDITORIA).deServico());
    }

    /**
     * Mapear grupo desconhecido para o papel de menor privilegio PARECE seguro
     * e nao e: daria acesso a quem a organizacao nao autorizou, e esconderia o
     * erro de configuracao que precisa aparecer.
     */
    static void grupoDesconhecidoNaoViraLeitura() {
        ok("F0-01 . o grupo do diretório vira papel",
                Papel.doGrupo("APROVADOR_DAF") == Papel.APROVADOR_DAF);
        ok("F0-01 . com hífen também", Papel.doGrupo("aprovador-daf") == Papel.APROVADOR_DAF);
        ok("F0-01 . e o desconhecido não vira papel nenhum",
                Papel.doGrupo("SGDF_QUALQUER_COISA") == null && Papel.doGrupo(null) == null);
    }

    static void atorSemIdentificadorNaoExiste() {
        boolean recusou = false;
        try {
            new Ator("  ", "Sem nome", Set.of(Papel.AUDITORIA), Set.of());
        } catch (IllegalArgumentException e) {
            recusou = true;
        }
        ok("Cap. 16 . ator sem identificador é recusado — a trilha exige o ator", recusou);
    }

    // -------------------------------------------------------------------------

    static Ator com(Papel papel) {
        return new Ator("teste", "Teste", Set.of(papel), Set.of(CONTRATO_A));
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

    private TestesDeAutorizacao() {}
}
