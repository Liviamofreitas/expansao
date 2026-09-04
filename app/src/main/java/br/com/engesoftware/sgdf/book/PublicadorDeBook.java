package br.com.engesoftware.sgdf.book;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Publica o book no armazenamento imutável.
 *
 * <p>Cap. 10: pré-condição é o ciclo em {@code PRONTO} — toda exigência
 * bloqueante em {@code CONCILIADO} ou {@code DISPENSADO}. Esta classe não
 * consulta o estado do ciclo: recebe-o, para que a regra de quem pode publicar
 * fique num lugar só e para que a publicação seja testável sem banco.
 *
 * <p><b>A ordem de gravação importa.</b> As peças primeiro, o índice e o
 * manifesto por último. O manifesto é o que declara o conjunto completo; gravá-lo
 * antes das peças criaria, por um instante, um book que se declara completo e não
 * está — e num bucket imutável esse instante não se corrige, só se versiona.
 */
public final class PublicadorDeBook {

    private final ArmazenamentoImutavel armazenamento;
    private final GeradorDeIndice indice;

    public PublicadorDeBook(ArmazenamentoImutavel armazenamento) {
        this.armazenamento = armazenamento;
        this.indice = new GeradorDeIndice();
    }

    /**
     * @param book        já montado e numerado
     * @param conteudos   bytes de cada peça, na mesma ordem de {@code book.pecas()}
     * @param cicloPronto se o ciclo satisfaz a pré-condição do cap. 10
     * @param cicloId     para o manifesto
     * @param retencao    modo de object lock — decisão da A08
     */
    public Publicado publicar(Book book, List<byte[]> conteudos, boolean cicloPronto,
                              UUID cicloId, ArmazenamentoImutavel.Retencao retencao) {
        if (!cicloPronto) {
            throw new CicloNaoPronto(book.contrato(), book.competencia());
        }
        if (conteudos.size() != book.pecas().size()) {
            throw new IllegalArgumentException("o book tem " + book.pecas().size()
                    + " peça(s) e vieram " + conteudos.size() + " conteúdo(s)");
        }

        String prefixo = book.caminhoBucket();
        for (int i = 0; i < book.pecas().size(); i++) {
            Peca p = book.pecas().get(i);
            byte[] conteudo = conteudos.get(i);
            // O hash foi calculado na montagem; conferir aqui é barato e pega
            // a troca de conteúdo entre montar e publicar.
            String hashAgora = Hash.de(conteudo);
            if (!hashAgora.equals(p.hashSha256())) {
                throw new IllegalStateException("a peça " + p.sequencia() + " (" + p.tipo()
                        + ") mudou entre a montagem e a publicação: o manifesto declara "
                        + p.hashSha256() + " e o conteúdo é " + hashAgora);
            }
            armazenamento.gravar(prefixo + p.arquivo(), conteudo, retencao);
        }

        byte[] pdfDoIndice = indice.gerar(book);
        armazenamento.gravar(prefixo + GeradorDeIndice.NOME, pdfDoIndice, retencao);

        String json = Manifesto.json(book, cicloId);
        armazenamento.gravar(prefixo + Manifesto.NOME,
                json.getBytes(StandardCharsets.UTF_8), retencao);

        return new Publicado(book, prefixo, json, pdfDoIndice.length);
    }

    /** O que a publicação produziu. */
    public record Publicado(Book book, String prefixo, String manifestoJson, int bytesDoIndice) {
    }

    /** O ciclo não satisfaz a pré-condição do cap. 10. */
    public static final class CicloNaoPronto extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CicloNaoPronto(String contrato, String competencia) {
            super("o ciclo de " + contrato + " em " + competencia + " não está PRONTO: "
                    + "publicar antes entregaria ao cliente um book que afirma uma "
                    + "regularidade que ainda não foi verificada");
        }
    }
}
