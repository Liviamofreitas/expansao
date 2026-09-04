package br.com.engesoftware.sgdf.persistencia;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Ponto de acesso ao banco — uma conexão, uma transação, sem framework.
 *
 * <p>Ver ADR-002 para por que não há ORM aqui. Esta classe é o mínimo que
 * permite aos repositórios existirem sem que cada um abra a própria conexão.
 */
public final class Sgdf {

    private final Connection conexao;

    public Sgdf(Connection conexao) {
        this.conexao = conexao;
    }

    public Connection conexao() {
        return conexao;
    }

    /**
     * Roda o trabalho numa transação, com rollback em qualquer falha.
     *
     * <p>O rollback não é zelo: a gravação de um documento envolve várias
     * tabelas — documento, campo_extraido, validacao_documento — e um documento
     * meio gravado é pior que nenhum. Um campo extraído apontando para um
     * documento que não existe quebra a auditoria do cap. 16 exatamente onde ela
     * é mais necessária.
     */
    public <T> T emTransacao(Function<Connection, T> trabalho) {
        boolean autoCommitAnterior;
        try {
            autoCommitAnterior = conexao.getAutoCommit();
        } catch (SQLException e) {
            throw new FalhaDePersistencia("não foi possível ler o estado da conexão", e);
        }

        // JÁ ESTAMOS DENTRO DE UMA TRANSAÇÃO: participa dela e não comita.
        //
        // Sem isto, um repositório chamado de dentro de outra unidade de
        // trabalho comitaria o trabalho de quem o chamou. Um documento gravado
        // dentro de um bloco que depois falha ficaria no banco — e o rollback
        // externo não teria o que desfazer. É a diferença entre "tudo ou nada"
        // e "quase tudo", e a segunda não sustenta a auditoria do cap. 16.
        if (!autoCommitAnterior) {
            return trabalho.apply(conexao);
        }

        try {
            conexao.setAutoCommit(false);
        } catch (SQLException e) {
            throw new FalhaDePersistencia("não foi possível iniciar a transação", e);
        }
        try {
            T resultado = trabalho.apply(conexao);
            conexao.commit();
            return resultado;
        } catch (RuntimeException e) {
            // DESFAZ E REPASSA COMO VEIO.
            //
            // Uma recusa do domínio levantada dentro da unidade de trabalho — "esta
            // candidatura já foi decidida", "reclassificar exige escolher tipo" —
            // precisa do rollback e precisa chegar a quem chamou COM O SEU TIPO.
            // Envolvê-la em FalhaDePersistencia transformava "você não pode fazer
            // isso" em "o banco falhou": a camada web devolveria 500 onde o certo
            // é 409 ou 422, e quem investigasse o erro procuraria defeito no banco.
            desfazer(e);
            throw e;
        } catch (SQLException e) {
            desfazer(e);
            throw new FalhaDePersistencia("transação desfeita", e);
        } finally {
            try {
                conexao.setAutoCommit(autoCommitAnterior);
            } catch (SQLException ignorado) {
                // A conexão está sendo descartada de qualquer forma.
            }
        }
    }

    private void desfazer(Exception causa) {
        try {
            conexao.rollback();
        } catch (SQLException falhaNoRollback) {
            causa.addSuppressed(falhaNoRollback);
        }
    }

    /** Falha de gravação ou leitura, com a causa preservada. */
    public static final class FalhaDePersistencia extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FalhaDePersistencia(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
