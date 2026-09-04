package br.com.engesoftware.sgdf.extracao;

/**
 * Um caractere com a posição em que foi desenhado na página.
 *
 * <p>Existe para que o domínio não dependa de tipos do PDFBox: trocar a
 * biblioteca de extração, ou acrescentar o OCR como segunda fonte, não deve
 * exigir mexer no localizador de campos nem no que é persistido.
 */
public record Glifo(char caractere, int pagina, float x, float y, float largura, float altura) {

    public Retangulo retangulo() {
        return new Retangulo(x, y, largura, altura);
    }

    /** Verdadeiro quando este glifo está na mesma linha do anterior. */
    public boolean mesmaLinhaQue(Glifo outro) {
        if (outro == null || outro.pagina != pagina) {
            return false;
        }
        float tolerancia = Math.max(altura, outro.altura) * 0.5f;
        return Math.abs(y - outro.y) <= tolerancia;
    }
}
