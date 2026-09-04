package br.com.engesoftware.sgdf.book;

/**
 * O bucket onde o book é publicado, com object lock.
 *
 * <p>Cap. 10: <i>"bucket com object lock … nenhuma versão é alterada ou removida
 * fora do expurgo formal"</i>. A interface existe para que a imutabilidade seja
 * uma propriedade <b>testável</b> e não uma configuração de infraestrutura que
 * ninguém confere: {@link #gravar} recusa sobrescrever, e não existe método de
 * remover.
 *
 * <p>A ausência de {@code remover()} é deliberada. Uma interface com um método
 * de apagar e um comentário dizendo "não use" é um convite; uma interface sem
 * ele é uma garantia estrutural.
 */
public interface ArmazenamentoImutavel {

    /**
     * Grava o objeto, com o modo de retenção decidido na pendência A08.
     *
     * @throws ObjetoJaExiste quando a chave já foi gravada — republicar cria
     *                        {@code v{N+1}}, nunca sobrescreve {@code v{N}}
     */
    void gravar(String chave, byte[] conteudo, Retencao retencao);

    boolean existe(String chave);

    byte[] ler(String chave);

    /**
     * Modo e prazo de retenção.
     *
     * <p>Decisão da A08, registrada em V006: o padrão é {@code LEGAL_HOLD} —
     * proteção indefinida e reversível por papel autorizado. {@code COMPLIANCE}
     * exige data e é irreversível: nem a conta raiz remove antes do prazo. Por
     * isso ele só entra quando existir tabela de temporalidade.
     */
    record Retencao(Modo modo, java.time.OffsetDateTime ate) {

        public enum Modo { LEGAL_HOLD, GOVERNANCE, COMPLIANCE }

        public Retencao {
            if (modo == Modo.LEGAL_HOLD && ate != null) {
                throw new IllegalArgumentException(
                        "LEGAL_HOLD não tem prazo: é proteção indefinida");
            }
            if (modo != Modo.LEGAL_HOLD && ate == null) {
                throw new IllegalArgumentException(
                        modo + " exige data até quando o objeto fica protegido");
            }
        }

        /** O padrão da A08 enquanto não houver tabela de temporalidade. */
        public static Retencao semPrazo() {
            return new Retencao(Modo.LEGAL_HOLD, null);
        }
    }

    /** Tentativa de sobrescrever um objeto que já existe. */
    class ObjetoJaExiste extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ObjetoJaExiste(String chave) {
            super("o objeto '" + chave + "' já existe e o bucket é imutável: "
                    + "republicar cria uma versão nova, nunca substitui a anterior");
        }
    }
}
