package br.com.engesoftware.sgdf.extracao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Extração de PDF nativo com posição por caractere — história F1-02, cap. 8.2.
 *
 * <p>{@link PDFTextStripper#writeString(String, List)} entrega, junto com o
 * texto, a {@link TextPosition} de cada glifo. É essa sobrecarga que sustenta o
 * critério de aceite: sem ela, sobraria só o texto, e o campo extraído não teria
 * como apontar de onde veio.
 *
 * <p><b>O PDF é entrada hostil (R-03).</b> Um PDF pode ter milhares de páginas,
 * profundidade de objetos patológica ou fluxos que expandem demais. As defesas
 * aqui são o limite de páginas e o de bytes; o limite de tempo e o de memória
 * ficam no worker, que roda em processo separado justamente para que um estouro
 * não derrube a API (cap. 4.1).
 */
public final class ExtratorPdfBox implements ExtratorDeTexto {

    /** Padrões do cap. 16 (volumetria) e 8.1. Todos são parâmetro. */
    public record Limites(int maximoDePaginas, int minimoDeCaracteresPorPagina) {
        public static Limites padrao() {
            return new Limites(500, 50);
        }
    }

    private final Limites limites;

    public ExtratorPdfBox(Limites limites) {
        this.limites = limites;
    }

    public ExtratorPdfBox() {
        this(Limites.padrao());
    }

    @Override
    public TextoExtraido extrair(byte[] conteudo) {
        try (PDDocument documento = Loader.loadPDF(conteudo)) {
            if (documento.isEncrypted()) {
                // Cap. 8.2 / V1: arquivo cifrado não é extraível. Tentar senha
                // vazia e seguir esconderia que o documento tem restrição.
                throw new ExtracaoInvalida("PDF_CIFRADO",
                        "documento protegido por senha ou com restrição de extração");
            }
            int paginas = documento.getNumberOfPages();
            if (paginas == 0) {
                throw new ExtracaoInvalida("PDF_SEM_PAGINAS", "documento sem páginas");
            }
            if (paginas > limites.maximoDePaginas()) {
                throw new ExtracaoInvalida("PDF_PAGINAS_DEMAIS",
                        paginas + " páginas excedem o limite de " + limites.maximoDePaginas());
            }

            List<PaginaExtraida> extraidas = new ArrayList<>(paginas);
            List<Integer> paraOcr = new ArrayList<>();
            List<Integer> semRecuperacao = new ArrayList<>();

            for (int n = 1; n <= paginas; n++) {
                ColetorDeGlifos coletor = new ColetorDeGlifos(n);
                coletor.setSortByPosition(true);   // ordem de leitura, não de desenho
                coletor.setStartPage(n);
                coletor.setEndPage(n);
                coletor.getText(documento);

                PaginaExtraida pagina = coletor.pagina();
                extraidas.add(pagina);
                if (!pagina.temTexto(limites.minimoDeCaracteresPorPagina())) {
                    // Sem texto: é folha em branco ou folha digitalizada? A
                    // presença de imagem decide. Ver a nota de TextoExtraido.
                    if (temImagem(documento.getPage(n - 1))) {
                        paraOcr.add(n);
                    } else {
                        semRecuperacao.add(n);
                    }
                }
            }

            TextoExtraido.Origem origem = paraOcr.size() + semRecuperacao.size() == paginas
                    ? TextoExtraido.Origem.OCR      // nada nativo: só o OCR resolve
                    : TextoExtraido.Origem.PDF_NATIVO;
            return new TextoExtraido(extraidas, origem, paraOcr, semRecuperacao);

        } catch (InvalidPasswordException e) {
            throw new ExtracaoInvalida("PDF_CIFRADO", "documento protegido por senha");
        } catch (IOException e) {
            throw new ExtracaoInvalida("PDF_CORROMPIDO", e.getMessage());
        }
    }

    /**
     * A página desenha alguma imagem?
     *
     * <p>Erro de leitura de recurso não interrompe a extração: na dúvida, a
     * página é tratada como digitalizada, que é o lado conservador — manda para
     * OCR em vez de descartar conteúdo possivelmente existente.
     */
    private static boolean temImagem(PDPage pagina) {
        try {
            PDResources recursos = pagina.getResources();
            if (recursos == null) {
                return false;
            }
            for (var nome : recursos.getXObjectNames()) {
                PDXObject objeto = recursos.getXObject(nome);
                if (objeto instanceof PDImageXObject) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Acumula texto e glifos em paralelo, no mesmo índice.
     *
     * <p>O alinhamento é a invariante que {@link PaginaExtraida} valida no
     * construtor: se um caractere entrar no texto sem o glifo correspondente,
     * todas as regiões depois dele apontam para o lugar errado.
     */
    private static final class ColetorDeGlifos extends PDFTextStripper {

        private final int numeroDaPagina;
        private final StringBuilder texto = new StringBuilder();
        private final List<Glifo> glifos = new ArrayList<>();

        ColetorDeGlifos(int numeroDaPagina) throws IOException {
            this.numeroDaPagina = numeroDaPagina;
        }

        @Override
        protected void writeString(String textoDaLinha, List<TextPosition> posicoes) {
            for (TextPosition p : posicoes) {
                String unicode = p.getUnicode();
                for (int i = 0; i < unicode.length(); i++) {
                    texto.append(unicode.charAt(i));
                    glifos.add(new Glifo(
                            unicode.charAt(i), numeroDaPagina,
                            p.getXDirAdj(),
                            // PDFBox devolve Y a partir do TOPO; o PDF conta a
                            // partir da BASE. Converter aqui evita que a tela
                            // desenhe o destaque espelhado na vertical.
                            p.getPageHeight() - p.getYDirAdj(),
                            p.getWidthDirAdj(),
                            p.getHeightDir()));
                }
            }
        }

        @Override
        protected void writeLineSeparator() {
            acrescentarSeparador('\n');
        }

        @Override
        protected void writeWordSeparator() {
            acrescentarSeparador(' ');
        }

        /**
         * Separadores entram no texto e precisam de um glifo para manter o
         * alinhamento. Herdam a posição do caractere anterior, com largura zero:
         * eles não desenham nada, e o localizador já os descarta ao montar os
         * retângulos.
         */
        private void acrescentarSeparador(char c) {
            texto.append(c);
            Glifo ultimo = glifos.isEmpty() ? null : glifos.get(glifos.size() - 1);
            glifos.add(ultimo == null
                    ? new Glifo(c, numeroDaPagina, 0, 0, 0, 0)
                    : new Glifo(c, numeroDaPagina, ultimo.x() + ultimo.largura(),
                                ultimo.y(), 0, ultimo.altura()));
        }

        PaginaExtraida pagina() {
            return new PaginaExtraida(numeroDaPagina, texto.toString(), glifos);
        }
    }
}
