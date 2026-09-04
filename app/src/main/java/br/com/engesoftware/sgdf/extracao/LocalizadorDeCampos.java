package br.com.engesoftware.sgdf.extracao;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Aplica padrões de campo sobre o texto extraído e devolve cada valor
 * <b>com a região de onde foi lido</b> — critério de aceite da história F1-02.
 */
public final class LocalizadorDeCampos {

    private LocalizadorDeCampos() {}

    /**
     * Procura o padrão em todas as páginas.
     *
     * <p>Devolve todas as ocorrências, não só a primeira: um CNPJ aparece várias
     * vezes numa guia, e escolher qual vale é decisão da regra de
     * reconhecimento, não do localizador.
     */
    public static List<CampoExtraido> localizar(TextoExtraido texto, PadraoDeCampo padrao,
                                                double confianca) {
        List<CampoExtraido> achados = new ArrayList<>();
        for (PaginaExtraida pagina : texto.paginas()) {
            TextoNormalizado normalizado = TextoNormalizado.de(pagina.texto());
            Matcher m = padrao.padrao().matcher(normalizado.texto());
            while (m.find()) {
                int inicioNorm = m.start(padrao.grupo());
                int fimNorm = m.end(padrao.grupo());
                if (inicioNorm < 0 || fimNorm <= inicioNorm) {
                    continue;   // grupo opcional que não casou
                }
                int inicio = normalizado.origemDe(inicioNorm);
                int fim = normalizado.origemDoFim(fimNorm);

                String valor = pagina.texto().substring(inicio, Math.min(fim, pagina.texto().length()));
                RegiaoNoDocumento regiao = regiaoDe(pagina, inicio, fim);
                if (regiao != null) {
                    achados.add(new CampoExtraido(padrao.nome(), valor.trim(), confianca, regiao));
                }
            }
        }
        return achados;
    }

    /** Primeira ocorrência, ou vazio. Atalho para campos que o cadastro marca como únicos. */
    public static CampoExtraido primeiro(TextoExtraido texto, PadraoDeCampo padrao,
                                         double confianca) {
        List<CampoExtraido> achados = localizar(texto, padrao, confianca);
        return achados.isEmpty() ? null : achados.get(0);
    }

    /**
     * Agrupa os glifos de {@code [inicio, fim)} em retângulos por linha.
     *
     * <p>Um retângulo por trecho contínuo: ver a justificativa em
     * {@link RegiaoNoDocumento}.
     */
    static RegiaoNoDocumento regiaoDe(PaginaExtraida pagina, int inicio, int fim) {
        List<Retangulo> retangulos = new ArrayList<>();
        Retangulo atual = null;
        Glifo anterior = null;

        for (int i = inicio; i < Math.min(fim, pagina.glifos().size()); i++) {
            Glifo g = pagina.glifos().get(i);
            if (Character.isWhitespace(g.caractere())) {
                continue;   // espaço não desenha nada que valha destacar
            }
            if (atual == null || !g.mesmaLinhaQue(anterior)) {
                if (atual != null) {
                    retangulos.add(atual);
                }
                atual = g.retangulo();
            } else {
                atual = atual.unir(g.retangulo());
            }
            anterior = g;
        }
        if (atual != null) {
            retangulos.add(atual);
        }
        return retangulos.isEmpty() ? null : new RegiaoNoDocumento(pagina.numero(), retangulos);
    }
}
