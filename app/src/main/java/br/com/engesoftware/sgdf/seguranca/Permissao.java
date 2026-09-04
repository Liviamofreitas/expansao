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

    /** Configuração técnica: integrações, parâmetros, regras de reconhecimento. */
    CONFIGURAR_SISTEMA,

    /** Ler a trilha de auditoria e exportar. */
    AUDITAR
}
