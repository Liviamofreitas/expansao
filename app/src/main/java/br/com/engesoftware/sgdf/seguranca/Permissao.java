package br.com.engesoftware.sgdf.seguranca;

/**
 * O que se pode pedir ao sistema.
 *
 * <p>Uma permissão por operação de negócio, não por endpoint: endpoints mudam
 * de nome e se dividem, e a autorização não deve mudar quando isso acontece.
 */
public enum Permissao {

    /** Ver o painel da competência, os ciclos e as exigências. */
    VER_PAINEL,

    /** Ver o CONTEÚDO de um documento — não só que ele existe. */
    VER_CONTEUDO_DOCUMENTO,

    /** Ver documento de escopo profissional, que carrega dado pessoal. */
    VER_DOCUMENTO_PROFISSIONAL,

    /** Confirmar ou rejeitar candidato na fila de triagem. */
    TRIAR,

    /** Cadastrar contratos, tipos e regras. */
    CADASTRAR,

    /** Publicar versão da matriz de exigibilidade. */
    PUBLICAR_MATRIZ,

    /** Publicar e enviar o book. */
    PUBLICAR_BOOK,

    /** Solicitar exceção para uma exigência. */
    SOLICITAR_EXCECAO,

    /** Aprovar exceção — nunca a própria (SoD). */
    APROVAR_EXCECAO,

    /** Alterar a criticidade bloqueante de um tipo documental. */
    ALTERAR_CRITICIDADE,

    /** Registrar o ateste do contrato. */
    REGISTRAR_ATESTE,

    /**
     * Registrar a emissão da NF e mover o ciclo pelo cap. 6.2.
     *
     * <p><b>Leitura declarada, não literal.</b> O cap. 15.1 nomeia
     * REGISTRAR_ATESTE e não nomeia o registro da NF nem as transições do
     * ciclo. Deixá-las sem permissão as tornaria impossíveis — negar é o padrão
     * do {@code Autorizador} —, e concedê-las a todos contradiria o capítulo.
     * A leitura conservadora é a adotada: quem publica o faturamento
     * (PUBLICADOR_FIN) e quem tem visão global de aprovação (APROVADOR_DAF).
     * Registrado em PENDENCIAS para confirmação da DAF.
     */
    CONDUZIR_CICLO,

    /** Configuração técnica: integrações, parâmetros, regras de reconhecimento. */
    CONFIGURAR_SISTEMA,

    /** Ler a trilha de auditoria e exportar. */
    AUDITAR
}
