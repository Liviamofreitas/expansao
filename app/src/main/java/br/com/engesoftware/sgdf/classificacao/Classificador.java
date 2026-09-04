package br.com.engesoftware.sgdf.classificacao;

import br.com.engesoftware.sgdf.extracao.CampoExtraido;
import br.com.engesoftware.sgdf.extracao.LocalizadorDeCampos;
import br.com.engesoftware.sgdf.extracao.PadraoDeCampo;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import br.com.engesoftware.sgdf.extracao.TextoNormalizado;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Identifica o tipo documental de um arquivo pelo CONTEÚDO (história F1-03).
 *
 * <p>Cap. 8.3: {@code score = Σ peso(âncora presente) + Σ peso(campo extraído e
 * válido) + bônus(pasta compatível) + bônus(alias no nome do arquivo)},
 * normalizado pelo peso total declarado na regra. Acima de {@code limiar_auto}
 * o vínculo é automático; entre os limiares vai para triagem; abaixo do
 * {@code limiar_triagem} o arquivo é desconhecido e não conta como entrega.
 *
 * <p>Três decisões que o texto do capítulo não fixa e que a massa real exigiu:
 *
 * <ol>
 *   <li><b>O bônus nunca resgata.</b> Pasta e nome de arquivo somam, mas um
 *       documento cujo CONTEÚDO não alcança o limiar de triagem é desconhecido
 *       mesmo com o nome perfeito. Sem isso, renomear um arquivo o classificaria
 *       — e o sistema existe justamente para não depender do nome.</li>
 *   <li><b>Margem sobre o segundo colocado.</b> Um score alto não basta se
 *       outro tipo marcou quase o mesmo: dois tipos parecidos que pontuam
 *       igual são uma dúvida, e dúvida vai para triagem, não para o book.</li>
 *   <li><b>Âncora discriminante.</b> Quando existe, a sua ausência zera o tipo.
 *       Sem ela a certidão de falências e a de ações cíveis se classificam uma
 *       como a outra: compartilham o cabeçalho inteiro.</li>
 * </ol>
 */
public final class Classificador {

    /**
     * Diferença mínima para o segundo colocado. Abaixo disso a decisão vai para
     * triagem mesmo que o primeiro passe do limiar automático.
     */
    public static final double MARGEM_MINIMA = 0.10;

    private final List<RegraDeReconhecimento> regras;

    public Classificador(List<RegraDeReconhecimento> regras) {
        this.regras = List.copyOf(regras);
    }

    public Classificacao classificar(TextoExtraido texto) {
        return classificar(texto, Bonus.nenhum());
    }

    public Classificacao classificar(TextoExtraido texto, Bonus bonus) {
        String normalizado = normalizar(texto.textoCompleto());

        List<Candidato> candidatos = new ArrayList<>();
        for (RegraDeReconhecimento regra : regras) {
            pontuar(regra, texto, normalizado, bonus).ifPresent(candidatos::add);
        }
        candidatos.sort(Comparator.comparingDouble(Candidato::score).reversed());

        if (candidatos.isEmpty()) {
            return new Classificacao(Decisao.NAO_RECONHECIDO, Optional.empty(), List.of(),
                    "nenhuma regra de reconhecimento marcou pontos neste arquivo");
        }
        return decidir(candidatos);
    }

    /**
     * Normaliza para o texto sobre o qual as âncoras são escritas.
     *
     * <p>O colapso de separadores é o achado A1: no documento real o título
     * quebra linha, e sem colapsar, praticamente toda âncora de mais de uma
     * palavra falharia.
     */
    public static String normalizar(String texto) {
        return TextoNormalizado.de(texto).texto().replaceAll("\\s+", " ").trim();
    }

    private Optional<Candidato> pontuar(RegraDeReconhecimento regra, TextoExtraido texto,
                                        String normalizado, Bonus bonus) {
        // Discriminante ausente elimina o tipo: não é desconto de peso, é a
        // expressão que separa este tipo de outro que se parece com ele.
        for (Ancora d : regra.discriminantes()) {
            if (!d.ocorreEm(normalizado)) {
                return Optional.empty();
            }
        }

        double marcado = 0;
        List<String> evidencias = new ArrayList<>();
        for (Ancora a : regra.ancoras()) {
            if (a.ocorreEm(normalizado)) {
                marcado += a.peso();
                evidencias.add("âncora " + a.expressao().pattern() + " (peso " + a.peso() + ")");
            }
        }
        for (PadraoDeCampo campo : regra.campos()) {
            CampoExtraido achado = LocalizadorDeCampos.primeiro(texto, campo, 1.0);
            if (achado != null) {
                marcado += regra.pesoPorCampo();
                evidencias.add("campo " + campo.nome() + "=" + achado.valor());
            }
        }
        if (marcado <= 0) {
            return Optional.empty();
        }

        double conteudo = Math.min(1.0, marcado / regra.pesoTotal());
        double comBonus = Math.min(1.0, conteudo + bonus.para(regra.tipo()));
        if (comBonus > conteudo) {
            evidencias.add("bônus de contexto " + bonus.para(regra.tipo()));
        }
        return Optional.of(new Candidato(regra.tipo(), conteudo, comBonus,
                evidencias, regra.versao()));
    }

    private Classificacao decidir(List<Candidato> candidatos) {
        Candidato melhor = candidatos.get(0);
        RegraDeReconhecimento regra = regraDe(melhor.tipo());

        // O bônus não resgata: quem decide se o arquivo é reconhecível é o
        // conteúdo. O nome do arquivo entra para desempatar, nunca para eleger.
        if (melhor.scoreDeConteudo() < regra.limiarTriagem()) {
            return new Classificacao(Decisao.NAO_RECONHECIDO, Optional.of(melhor), candidatos,
                    String.format("o melhor candidato (%s) marcou %.2f de conteúdo, "
                                    + "abaixo do limiar de triagem %.2f",
                            melhor.tipo(), melhor.scoreDeConteudo(), regra.limiarTriagem()));
        }

        double segundo = candidatos.size() > 1 ? candidatos.get(1).score() : 0.0;
        double margem = melhor.score() - segundo;

        if (melhor.score() >= regra.limiarAuto() && margem >= MARGEM_MINIMA) {
            return new Classificacao(Decisao.AUTOMATICA, Optional.of(melhor), candidatos,
                    String.format("%s marcou %.2f, acima do limiar %.2f, com margem %.2f "
                                    + "sobre o segundo colocado",
                            melhor.tipo(), melhor.score(), regra.limiarAuto(), margem));
        }
        if (margem < MARGEM_MINIMA && candidatos.size() > 1) {
            return new Classificacao(Decisao.TRIAGEM, Optional.of(melhor), candidatos,
                    String.format("%s e %s marcaram %.2f e %.2f: a diferença de %.2f é menor "
                                    + "que a margem mínima %.2f, e empate é dúvida",
                            melhor.tipo(), candidatos.get(1).tipo(), melhor.score(), segundo,
                            margem, MARGEM_MINIMA));
        }
        return new Classificacao(Decisao.TRIAGEM, Optional.of(melhor), candidatos,
                String.format("%s marcou %.2f, entre o limiar de triagem %.2f e o automático %.2f",
                        melhor.tipo(), melhor.score(), regra.limiarTriagem(), regra.limiarAuto()));
    }

    private RegraDeReconhecimento regraDe(String tipo) {
        return regras.stream().filter(r -> r.tipo().equals(tipo)).findFirst()
                .orElseThrow(() -> new IllegalStateException("candidato sem regra: " + tipo));
    }
}
