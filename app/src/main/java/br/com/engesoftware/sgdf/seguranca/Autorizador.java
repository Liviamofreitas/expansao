package br.com.engesoftware.sgdf.seguranca;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decide se um ator pode fazer uma operação — cap. 15.1.
 *
 * <p><b>Por que a regra vive aqui e não em anotações espalhadas.</b> A tabela do
 * cap. 15.1 tem uma coluna "não pode" que é tão normativa quanto a coluna
 * "pode": o ADMIN_SISTEMA <i>não pode</i> ver conteúdo de documento de escopo
 * profissional, e o APROVADOR_DAF <i>não pode</i> aprovar exceção que ele mesmo
 * solicitou. Uma anotação por endpoint expressa bem o que se permite e mal o que
 * se proíbe — e quem audita precisa ler as duas colunas num lugar só.
 *
 * <p><b>Negar é o padrão.</b> Papel sem permissão declarada não recebe nada.
 * Uma operação nova é invisível para todos os papéis até que alguém a declare,
 * o que força a decisão a passar por revisão em vez de aparecer por omissão.
 */
public final class Autorizador {

    private static final Map<Papel, Set<Permissao>> CONCEDIDAS = new EnumMap<>(Papel.class);

    static {
        // "Configuração técnica, regras de reconhecimento, integrações,
        // parâmetros". Não aprova exceção.
        //
        // Ele VÊ conteúdo de documento, e a leitura é do próprio capítulo: a
        // coluna "não pode" veda especificamente o ESCOPO PROFISSIONAL, e essa
        // ressalva só faz sentido se o resto for permitido. Negar tudo pareceria
        // mais seguro e tornaria a ressalva do capítulo letra morta — além de
        // impedir que quem administra a regra de reconhecimento veja o documento
        // sobre o qual a regra roda. A vedação está na guarda de escopo
        // profissional, adiante, que é onde o capítulo a coloca.
        CONCEDIDAS.put(Papel.ADMIN_SISTEMA, EnumSet.of(
                Permissao.CONFIGURAR_SISTEMA,
                Permissao.VER_PAINEL,
                Permissao.VER_CONTEUDO_DOCUMENTO));

        // "Cadastro de contratos/regras; publicar versão da matriz; publicar e
        // enviar book". Não aprova exceção nem altera criticidade bloqueante.
        CONCEDIDAS.put(Papel.CURADOR_MATRIZ, EnumSet.of(
                Permissao.VER_PAINEL,
                Permissao.VER_CONTEUDO_DOCUMENTO,
                Permissao.CADASTRAR,
                Permissao.PUBLICAR_MATRIZ,
                Permissao.PUBLICAR_BOOK,
                Permissao.SOLICITAR_EXCECAO,
                Permissao.TRIAR));

        // "Ver e acompanhar exigências trabalhistas dos seus contratos; triagem
        // desses tipos." O recorte por contrato e por tipo não cabe num papel:
        // é escopo, e vai no Ator.
        CONCEDIDAS.put(Papel.PUBLICADOR_AP, EnumSet.of(
                Permissao.VER_PAINEL,
                Permissao.VER_CONTEUDO_DOCUMENTO,
                Permissao.VER_DOCUMENTO_PROFISSIONAL,
                Permissao.TRIAR,
                Permissao.SOLICITAR_EXCECAO));

        // Idem para tipos fiscais e comprovantes. NÃO vê escopo profissional.
        CONCEDIDAS.put(Papel.PUBLICADOR_FIN, EnumSet.of(
                Permissao.VER_PAINEL,
                Permissao.VER_CONTEUDO_DOCUMENTO,
                Permissao.TRIAR,
                Permissao.SOLICITAR_EXCECAO));

        // "Registrar ateste; acompanhar seu ciclo; depositar documentos
        // técnicos." Não vê dado pessoal além do agregado.
        CONCEDIDAS.put(Papel.GESTOR_CONTRATO, EnumSet.of(
                Permissao.VER_PAINEL,
                Permissao.REGISTRAR_ATESTE,
                Permissao.SOLICITAR_EXCECAO));

        // "Aprovar exceções e criticidade; visão global."
        CONCEDIDAS.put(Papel.APROVADOR_DAF, EnumSet.of(
                Permissao.VER_PAINEL,
                Permissao.VER_CONTEUDO_DOCUMENTO,
                Permissao.APROVAR_EXCECAO,
                Permissao.ALTERAR_CRITICIDADE));

        // "Leitura global + trilha + exportações." Qualquer escrita é negada.
        CONCEDIDAS.put(Papel.AUDITORIA, EnumSet.of(
                Permissao.VER_PAINEL,
                Permissao.VER_CONTEUDO_DOCUMENTO,
                Permissao.VER_DOCUMENTO_PROFISSIONAL,
                Permissao.AUDITAR));

        // Contas de serviço: exatamente uma operação cada.
        CONCEDIDAS.put(Papel.SVC_LEITURA, EnumSet.of(Permissao.VER_CONTEUDO_DOCUMENTO));
        CONCEDIDAS.put(Papel.SVC_PUBLICA, EnumSet.of(Permissao.PUBLICAR_BOOK));
    }

    private Autorizador() {}

    /** Decisão simples, sem alvo — vale para operações que não são sobre um objeto. */
    public static Decisao pode(Ator ator, Permissao permissao) {
        return pode(ator, permissao, Alvo.nenhum());
    }

    /**
     * Decide, considerando o alvo.
     *
     * <p>O alvo carrega o que a decisão precisa saber além do papel: de que
     * contrato é o objeto, se ele é de escopo profissional, e quem solicitou a
     * exceção que se quer aprovar.
     */
    public static Decisao pode(Ator ator, Permissao permissao, Alvo alvo) {
        if (ator == null) {
            return Decisao.negada("sem ator autenticado");
        }
        if (ator.papeis().isEmpty()) {
            return Decisao.negada("o ator não tem papel reconhecido: "
                    + "um grupo do diretório que não corresponde a papel do cap. 15.1 "
                    + "não vira acesso de leitura por omissão");
        }

        boolean concedida = ator.papeis().stream()
                .anyMatch(p -> CONCEDIDAS.getOrDefault(p, EnumSet.noneOf(Permissao.class))
                        .contains(permissao));
        if (!concedida) {
            return Decisao.negada("nenhum dos papéis " + ator.papeis()
                    + " concede " + permissao);
        }

        // --- a coluna "não pode" do cap. 15.1 --------------------------------

        // SoD: o aprovador nunca é o solicitante. Está imposto no banco também
        // (restrição excecao_sod); aqui é onde a tentativa é barrada antes de
        // chegar lá, com uma mensagem que explica.
        if (permissao == Permissao.APROVAR_EXCECAO
                && alvo.solicitante() != null
                && alvo.solicitante().equals(ator.identificador())) {
            return Decisao.negada("segregação de funções: quem solicitou a exceção não a "
                    + "aprova (cap. 15.1). Outro APROVADOR_DAF precisa decidir");
        }

        // Escopo profissional carrega dado pessoal. Ver que a exigência existe
        // é uma coisa; abrir o documento é outra.
        if (alvo.escopoProfissional()
                && permissao == Permissao.VER_CONTEUDO_DOCUMENTO
                && !temAlguma(ator, Permissao.VER_DOCUMENTO_PROFISSIONAL)) {
            return Decisao.negada("documento de escopo profissional: os papéis "
                    + ator.papeis() + " veem o painel, não o conteúdo com dado pessoal");
        }

        // Recorte por contrato: PUBLICADOR e GESTOR só enxergam os seus.
        if (alvo.contrato() != null && !ator.enxergaTodosOsContratos()
                && !ator.contratos().contains(alvo.contrato())) {
            return Decisao.negada("o contrato " + alvo.contrato()
                    + " está fora do escopo do ator");
        }

        return Decisao.concedida();
    }

    private static boolean temAlguma(Ator ator, Permissao permissao) {
        return ator.papeis().stream()
                .anyMatch(p -> CONCEDIDAS.getOrDefault(p, EnumSet.noneOf(Permissao.class))
                        .contains(permissao));
    }

    /** As permissões de um papel — para a tela mostrar só o que o ator pode fazer. */
    public static Set<Permissao> de(Papel papel) {
        return Set.copyOf(CONCEDIDAS.getOrDefault(papel, EnumSet.noneOf(Permissao.class)));
    }

    /**
     * Sobre o que a operação incide.
     *
     * @param contrato            identificador do contrato-serviço, quando há
     * @param escopoProfissional  se o objeto carrega dado pessoal de trabalhador
     * @param solicitante         quem pediu a exceção que se quer aprovar
     */
    public record Alvo(UUID contrato, boolean escopoProfissional, String solicitante) {

        public static Alvo nenhum() {
            return new Alvo(null, false, null);
        }

        public static Alvo doContrato(UUID contrato) {
            return new Alvo(contrato, false, null);
        }

        public static Alvo profissional(UUID contrato) {
            return new Alvo(contrato, true, null);
        }

        public static Alvo excecaoDe(String solicitante) {
            return new Alvo(null, false, solicitante);
        }
    }

    /**
     * O resultado, com o motivo.
     *
     * <p>O motivo não é para o usuário final — é para o log e para quem
     * investiga um 403. Um "acesso negado" sem motivo transforma cada dúvida de
     * permissão numa sessão de depuração.
     */
    public record Decisao(boolean permitida, String motivo) {

        static Decisao concedida() {
            return new Decisao(true, null);
        }

        static Decisao negada(String motivo) {
            return new Decisao(false, motivo);
        }

        public boolean negada() {
            return !permitida;
        }
    }
}
