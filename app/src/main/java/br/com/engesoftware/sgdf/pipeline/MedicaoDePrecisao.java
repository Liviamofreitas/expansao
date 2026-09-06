package br.com.engesoftware.sgdf.pipeline;

import br.com.engesoftware.sgdf.classificacao.Decisao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Precisão de classificação do cap. 19 — meta ≥ 95% no bloco corporativo.
 *
 * <p>Fórmula do capítulo: <i>"corretos / total de arquivos com gabarito
 * humano; medir por tipo documental"</i>.
 *
 * <p><b>Três resultados, não dois — e é aqui que a conta pode ficar bonita e
 * falsa.</b> Um arquivo com gabarito pode terminar em três lugares, e juntá-los
 * produz números diferentes que se parecem:
 *
 * <ul>
 *   <li><b>Acerto</b> — o sistema disse o tipo do gabarito.</li>
 *   <li><b>Erro</b> — disse outro tipo. É o caso caro: um documento vinculado à
 *       obrigação errada satisfaz uma exigência que não era a dele.</li>
 *   <li><b>Abstenção</b> — não reconheceu, ou mandou para triagem. O sistema
 *       <i>não afirmou nada</i>.</li>
 * </ul>
 *
 * <p>Contar abstenção como acerto daria 100% a um sistema que manda tudo para
 * triagem. Contá-la como erro puniria a resposta certa do cap. 8.3 — "na
 * dúvida, pergunte" — e empurraria os limiares para baixo, que é o caminho
 * direto para o erro caro. Por isso as três são reportadas separadamente, e a
 * precisão do capítulo é medida <b>sobre o que o sistema afirmou</b>:
 * {@code acertos / (acertos + erros)}, com a cobertura ao lado dizendo sobre
 * quanto da massa ele se dispôs a afirmar.
 *
 * <p>As duas juntas não se enganam: 100% de precisão com 20% de cobertura é um
 * sistema que não serve, e a tabela mostra isso na cara. Uma só delas, não.
 */
public final class MedicaoDePrecisao {

    /** Meta do cap. 19 para o bloco corporativo, antes da fase 1b. */
    public static final double META_CORPORATIVA = 0.95;

    private final Map<String, Contagem> porTipo = new LinkedHashMap<>();
    private final List<Divergencia> divergencias = new ArrayList<>();
    private int total;

    /**
     * Registra um arquivo conferido.
     *
     * @param esperado tipo do gabarito humano — nunca nulo, é o que faz o
     *                 arquivo "ter gabarito"
     * @param obtido   tipo que o sistema afirmou, ou nulo quando se absteve
     */
    public void registrar(String arquivo, String esperado, String obtido,
                          Decisao decisao, Pipeline.Encaminhamento encaminhamento) {
        if (esperado == null || esperado.isBlank()) {
            throw new IllegalArgumentException(arquivo + ": arquivo sem gabarito não entra na "
                    + "conta — o denominador do cap. 19 é 'arquivos com gabarito humano'");
        }
        total++;
        Contagem c = porTipo.computeIfAbsent(esperado, t -> new Contagem());

        // ABSTENÇÃO É A DECISÃO DE CLASSIFICAÇÃO, NÃO O ENCAMINHAMENTO.
        //
        // A primeira versão lia o encaminhamento, e a massa real mostrou o
        // defeito: o comprovante de INSS foi classificado CMP.DARF com
        // AUTOMATICA e mesmo assim caiu em TRIAGEM — porque V2 reprovou uma
        // página quase em branco entre as oito. Contar isso como abstenção
        // debitava do classificador uma decisão que foi da legibilidade, e
        // escondia um acerto. O cap. 19 mede "precisão de classificação";
        // quem afirmou o tipo foi o classificador.
        //
        // Ler só o tipo também não serve: a classificação pode ter um melhor
        // candidato e ainda mandar para triagem por margem insuficiente sobre o
        // segundo — e o sistema seria cobrado por um palpite que não deu.
        boolean afirmou = decisao == Decisao.AUTOMATICA;
        if (!afirmou) {
            c.abstencoes++;
            divergencias.add(new Divergencia(arquivo, esperado, obtido, encaminhamento,
                    Situacao.ABSTENCAO));
            return;
        }
        if (esperado.equals(obtido)) {
            c.acertos++;
        } else {
            c.erros++;
            divergencias.add(new Divergencia(arquivo, esperado, obtido, encaminhamento,
                    Situacao.ERRO));
        }
    }

    /** Precisão sobre o que o sistema afirmou. Nula quando ele não afirmou nada. */
    public Double precisao() {
        int a = porTipo.values().stream().mapToInt(c -> c.acertos).sum();
        int e = porTipo.values().stream().mapToInt(c -> c.erros).sum();
        return a + e == 0 ? null : (double) a / (a + e);
    }

    /** Fração da massa sobre a qual ele se dispôs a afirmar. */
    public double cobertura() {
        if (total == 0) {
            return 0;
        }
        int afirmados = porTipo.values().stream().mapToInt(c -> c.acertos + c.erros).sum();
        return (double) afirmados / total;
    }

    /**
     * A meta do cap. 19.
     *
     * <p>Sem afirmação nenhuma não se atinge: um sistema que manda tudo para
     * triagem não tem precisão de 100%, tem precisão indefinida.
     */
    public boolean atingeAMeta() {
        Double p = precisao();
        return p != null && p >= META_CORPORATIVA;
    }

    public int total() {
        return total;
    }

    public Map<String, Contagem> porTipo() {
        return Map.copyOf(porTipo);
    }

    /** Os arquivos que erraram ou sobre os quais o sistema se absteve. */
    public List<Divergencia> divergencias() {
        return List.copyOf(divergencias);
    }

    /** Uma tabela legível — o relatório que o cap. 19 pede por tipo documental. */
    public String relatorio() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-28s %7s %7s %11s %9s%n",
                "tipo (gabarito)", "acertos", "erros", "abstenções", "precisão"));
        porTipo.forEach((tipo, c) -> sb.append(String.format("%-28s %7d %7d %11d %9s%n",
                tipo, c.acertos, c.erros, c.abstencoes, percentual(c.precisao()))));
        sb.append(String.format("%n%-28s %7d arquivos%n", "total com gabarito", total));
        sb.append(String.format("%-28s %8s  (acertos / afirmações)%n",
                "precisão", percentual(precisao())));
        sb.append(String.format("%-28s %8s  (afirmações / total)%n",
                "cobertura", percentual(cobertura())));
        sb.append(String.format("%-28s %8s%n", "meta do cap. 19",
                atingeAMeta() ? "ATINGIDA" : "NÃO ATINGIDA"));
        return sb.toString();
    }

    private static String percentual(Double v) {
        return v == null ? "—" : String.format("%.1f%%", v * 100);
    }

    /** Contagem de um tipo do gabarito. */
    public static final class Contagem {
        int acertos;
        int erros;
        int abstencoes;

        public int acertos() {
            return acertos;
        }

        public int erros() {
            return erros;
        }

        public int abstencoes() {
            return abstencoes;
        }

        /** Nula quando o sistema não afirmou nada sobre este tipo. */
        public Double precisao() {
            return acertos + erros == 0 ? null : (double) acertos / (acertos + erros);
        }
    }

    public enum Situacao { ERRO, ABSTENCAO }

    /** Um caso que não fechou — com o suficiente para ir olhar o documento. */
    public record Divergencia(String arquivo, String esperado, String obtido,
                              Pipeline.Encaminhamento encaminhamento, Situacao situacao) {
    }
}
