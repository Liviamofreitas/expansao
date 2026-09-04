package br.com.engesoftware.sgdf.seguranca;

/**
 * Os papéis do cap. 15.1.
 *
 * <p>São exatamente os que a tabela do capítulo define, com os mesmos nomes.
 * Acrescentar um papel é decisão de governança, não de código — por isso o
 * enum é fechado: um papel novo tem de passar por revisão, e não aparecer
 * porque alguém escreveu uma string diferente no diretório.
 */
public enum Papel {

    ADMIN_SISTEMA,
    CURADOR_MATRIZ,
    PUBLICADOR_AP,
    PUBLICADOR_FIN,
    GESTOR_CONTRATO,
    APROVADOR_DAF,
    AUDITORIA,

    /** Conta de serviço: lê as origens. Login interativo negado. */
    SVC_LEITURA,

    /** Conta de serviço: escreve no bucket de evidências. Login interativo negado. */
    SVC_PUBLICA;

    /** Contas de serviço não fazem login interativo (cap. 15.1). */
    public boolean deServico() {
        return this == SVC_LEITURA || this == SVC_PUBLICA;
    }

    /**
     * Traduz o grupo do diretório para o papel.
     *
     * <p>Um grupo desconhecido devolve nulo, e quem chama trata como "sem
     * papel". Mapear o desconhecido para o papel de menor privilégio parece
     * seguro e não é: daria acesso de leitura a quem a organização não
     * autorizou, e esconderia o erro de configuração que precisa aparecer.
     */
    public static Papel doGrupo(String grupo) {
        if (grupo == null) {
            return null;
        }
        try {
            return valueOf(grupo.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
