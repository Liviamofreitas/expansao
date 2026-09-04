package br.com.engesoftware.sgdf.triagem;

import java.util.UUID;

/**
 * O que uma pessoa decidiu sobre um candidato — cap. 12, tela de triagem.
 *
 * <p>São três decisões, e elas não são variações da mesma coisa:
 *
 * <ul>
 *   <li><b>Confirmar</b> diz "este documento é o que esta exigência espera".
 *       Gera vínculo, move a exigência para RECEBIDO e ensina um alias.</li>
 *   <li><b>Reclassificar</b> diz "este documento existe, mas é <i>outra</i>
 *       coisa". Não gera vínculo com <i>esta</i> exigência — ela volta a
 *       PENDENTE, porque continua sem o documento que esperava.</li>
 *   <li><b>Ilegível</b> diz "não dá para saber". Devolve a exigência a PENDENTE
 *       com motivo, e não ensina nada — aprender com um arquivo que ninguém
 *       conseguiu ler é gravar um palpite como se fosse conhecimento.</li>
 * </ul>
 *
 * <p>O tipo selado existe para que acrescentar uma quarta decisão quebre a
 * compilação de quem as trata, em vez de cair num ramo {@code default} que
 * ninguém escreveu.
 */
public sealed interface PedidoDeTriagem {

    UUID candidaturaId();

    /**
     * Confirma o tipo proposto.
     *
     * <p>Não carrega tipo: confirmar é aceitar o que o motor propôs. Deixar
     * escolher o tipo aqui apagaria a diferença entre concordar e corrigir, e a
     * medição de acerto do motor (P01, modo sombra) depende exatamente dessa
     * diferença.
     */
    record Confirmar(UUID candidaturaId) implements PedidoDeTriagem {
        public Confirmar {
            exigirId(candidaturaId);
        }
    }

    /** Corrige o tipo. Cap. 12: "reclassificar exige escolher tipo". */
    record Reclassificar(UUID candidaturaId, UUID tipoEscolhidoId, String motivo)
            implements PedidoDeTriagem {

        public Reclassificar {
            exigirId(candidaturaId);
            if (tipoEscolhidoId == null) {
                throw new IllegalArgumentException(
                        "reclassificar exige escolher o tipo (cap. 12). Sem tipo, "
                        + "a correção não diz o que o documento é — só que não é aquilo");
            }
            motivo = exigirMotivo(motivo, "reclassificar");
        }
    }

    /** Devolve a exigência a PENDENTE. Cap. 12: "ilegível devolve a PENDENTE com motivo". */
    record Ilegivel(UUID candidaturaId, String motivo) implements PedidoDeTriagem {
        public Ilegivel {
            exigirId(candidaturaId);
            motivo = exigirMotivo(motivo, "marcar como ilegível");
        }
    }

    private static void exigirId(UUID candidaturaId) {
        if (candidaturaId == null) {
            throw new IllegalArgumentException("decisão de triagem sem candidatura");
        }
    }

    /**
     * Motivo substantivo, não um caractere para satisfazer a validação.
     *
     * <p>Quem entrega de novo lê este texto para saber o que corrigir. "x" ou
     * "erro" devolvem a exigência para a fila sem dizer nada, e o ciclo se
     * repete no mês seguinte com o mesmo arquivo.
     */
    private static String exigirMotivo(String motivo, String acao) {
        String limpo = motivo == null ? "" : motivo.strip();
        if (limpo.length() < 10) {
            throw new IllegalArgumentException(acao + " exige motivo (cap. 12). "
                    + "Quem entrega de novo precisa saber o que corrigir — "
                    + "um motivo de menos de 10 caracteres não diz isso");
        }
        return limpo;
    }
}
