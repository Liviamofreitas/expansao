package br.com.engesoftware.sgdf.extracao;

/**
 * Retângulo em coordenadas de página do PDF (origem no canto inferior esquerdo,
 * unidade em pontos).
 *
 * <p>As coordenadas ficam no sistema do PDF, sem conversão para pixels. A tela
 * precisa saber a escala de renderização para desenhar o destaque, e converter
 * aqui embutiria uma resolução arbitrária no dado persistido.
 */
public record Retangulo(float x, float y, float largura, float altura) {

    public Retangulo unir(Retangulo outro) {
        float x1 = Math.min(x, outro.x);
        float y1 = Math.min(y, outro.y);
        float x2 = Math.max(x + largura, outro.x + outro.largura);
        float y2 = Math.max(y + altura, outro.y + outro.altura);
        return new Retangulo(x1, y1, x2 - x1, y2 - y1);
    }

    public boolean contem(float px, float py) {
        return px >= x && px <= x + largura && py >= y && py <= y + altura;
    }
}
