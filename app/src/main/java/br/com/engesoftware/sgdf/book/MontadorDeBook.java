package br.com.engesoftware.sgdf.book;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Monta o book: escolhe a ordem, numera, calcula os hashes.
 *
 * <p>Cap. 10: <i>"numeração sequencial sem lacunas, ordenada por família"</i>.
 *
 * <p><b>A recusa que esta classe faz, e por quê.</b> O cap. 10 manda que tipos
 * com sigilo {@code PESSOAL} ou {@code PESSOAL_SENSIVEL} passem pelo redator
 * antes da cópia para o book do cliente. <b>O redator é a história F2-07 e ainda
 * não existe.</b>
 *
 * <p>Diante disso há três caminhos, e dois são inaceitáveis. Publicar o
 * documento íntegro no book do cliente vaza dado pessoal — a massa real tem um
 * relatório do FGTS com 160 CPFs, nomes e remuneração individual. Omitir a peça
 * em silêncio entrega um book incompleto que <i>parece</i> completo. O terceiro
 * é recusar a publicação e dizer exatamente qual peça exige tarjamento — e é o
 * que esta classe faz.
 *
 * <p>Quando o redator existir, ele marcará {@code tarjado = true} e a recusa
 * deixará de acontecer sozinha. Até lá, ela é a diferença entre um sistema que
 * respeita a LGPD e um que a menciona na documentação.
 */
public final class MontadorDeBook {

    /**
     * Ordem das famílias no book. É cadastro por natureza; está aqui como a
     * ordem do Anexo 1, e o índice entregue ao cliente segue esta sequência.
     */
    private static final List<String> ORDEM_DAS_FAMILIAS = List.of(
            "Certidões e regularidade",
            "INSS e tributos",
            "FGTS",
            "Folha",
            "Benefícios",
            "Segurança e saúde",
            "Operacional");

    /**
     * @param documentos      o que foi aprovado no ciclo
     * @param competencia     AAAA-MM
     * @param paraOCliente    verdadeiro quando o destino é o book do cliente, e
     *                        o tarjamento é exigido; falso para o book interno
     */
    public List<Peca> numerar(List<DocumentoPublicavel> documentos, String competencia,
                              boolean paraOCliente) {
        if (documentos.isEmpty()) {
            throw new IllegalArgumentException(
                    "nenhum documento aprovado: não há book a montar");
        }

        if (paraOCliente) {
            List<String> semTarja = new ArrayList<>();
            for (DocumentoPublicavel d : documentos) {
                if (d.sigilo().exigeTarjamento() && !d.tarjado()) {
                    semTarja.add(d.tipo() + " (" + d.sigilo() + ")");
                }
            }
            if (!semTarja.isEmpty()) {
                throw new TarjamentoPendente(semTarja);
            }
        }

        List<DocumentoPublicavel> ordenados = new ArrayList<>(documentos);
        ordenados.sort(Comparator
                .comparingInt((DocumentoPublicavel d) -> posicaoDaFamilia(d.familia()))
                .thenComparing(DocumentoPublicavel::familia)
                .thenComparing(DocumentoPublicavel::tipo));

        List<Peca> pecas = new ArrayList<>();
        int sequencia = 1;
        for (DocumentoPublicavel d : ordenados) {
            String arquivo = String.format("%02d_%s_%s.pdf", sequencia, d.tipo(), competencia);
            pecas.add(new Peca(sequencia, d.tipo(), d.sigilo(), arquivo,
                    Hash.de(d.conteudo()), d.origem(), d.validadoEm(), d.tarjado()));
            sequencia++;
        }
        return List.copyOf(pecas);
    }

    /**
     * Famílias fora da ordem conhecida vão para o fim, e não para o começo.
     *
     * <p>Uma família nova aparecendo antes das certidões mudaria a numeração de
     * todo o book — e o índice de um book já entregue remete a números que
     * deixariam de corresponder. Ao fim, a numeração das peças existentes fica
     * onde estava.
     */
    private static int posicaoDaFamilia(String familia) {
        int posicao = ORDEM_DAS_FAMILIAS.indexOf(familia);
        return posicao < 0 ? ORDEM_DAS_FAMILIAS.size() : posicao;
    }

    public Book montar(String contrato, String competencia, int versao, String publicadoPor,
                       String versaoMatriz, List<Peca> pecas) {
        return new Book(contrato, competencia, versao, OffsetDateTime.now(), publicadoPor,
                versaoMatriz, Hash.doConjunto(pecas), pecas,
                Book.prefixo(contrato, competencia, versao));
    }

    /** Há peça que exige tarjamento e o redator (F2-07) ainda não existe. */
    public static final class TarjamentoPendente extends RuntimeException {
        private static final long serialVersionUID = 1L;

        // Serializável de propósito: a lista imutável de List.copyOf não é
        // Serializable, e a exceção nunca atravessa processo. Guardar como
        // array evita o aviso sem fingir uma garantia que não existe.
        private final String[] pecas;

        TarjamentoPendente(List<String> pecas) {
            super("o book do cliente não pode ser publicado: " + pecas.size()
                    + " peça(s) exigem tarjamento e não foram tarjadas — "
                    + String.join(", ", pecas)
                    + ". Publicar o original vazaria dado pessoal; omitir a peça entregaria "
                    + "um book incompleto que parece completo (cap. 10, história F2-07)");
            this.pecas = pecas.toArray(new String[0]);
        }

        public List<String> pecas() {
            return List.of(pecas);
        }
    }
}
