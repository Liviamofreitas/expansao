package br.com.engesoftware.sgdf.notificacao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * O e-mail do dia para uma pessoa — <b>um</b>, consolidado.
 *
 * <p>Cap. 11.2: "máximo 1 e-mail por área por ciclo por dia (consolidação
 * forçada)". A consolidação é por PESSOA, não por momento da régua: quem tem
 * algo vencendo em 48 h, algo vencido hoje e algo escalado recebe um aviso com
 * as três coisas, e não três avisos. O {@link #momento()} do aviso é o mais
 * grave presente — informação para quem lê a linha, não chave.
 */
public record Aviso(UUID cicloId, Destinatario destinatario, LocalDate dataReferencia,
                    List<Item> itens) {

    public Aviso {
        if (itens.isEmpty()) {
            throw new IllegalArgumentException(
                    "aviso sem item é e-mail sem assunto: a régua não deve produzi-lo");
        }
        List<Item> ordenados = new ArrayList<>(itens);
        // Ordem estável: o hash do conteúdo tem de ser reproduzível, e duas
        // execuções da mesma régua sobre os mesmos dados precisam dar o mesmo
        // hash — senão a calibragem compara ruído.
        ordenados.sort(Comparator.comparing((Item i) -> i.momento().name())
                .thenComparing(Item::tipoCodigo)
                .thenComparing(i -> i.prazo() == null ? LocalDate.MIN : i.prazo()));
        itens = List.copyOf(ordenados);
    }

    /** O momento mais grave presente — é o que vai para {@code notificacao.tipo}. */
    public Momento momento() {
        Momento pior = itens.get(0).momento();
        for (Item i : itens) {
            if (i.momento().maisGraveQue(pior)) {
                pior = i.momento();
            }
        }
        return pior;
    }

    /**
     * Uma linha do aviso.
     *
     * <p>{@code quantidade} em vez de identificação: exigência de escopo
     * profissional é uma por trabalhador, e "3 contracheques pendentes" diz o
     * que a pessoa precisa fazer sem mandar nome de gente para a caixa da área.
     *
     * @param papelRecebido por que esta pessoa está recebendo este item — é o
     *                      que distingue "é sua responsabilidade" de "cópia"
     */
    public record Item(Momento momento, String tipoCodigo, String familia, LocalDate prazo,
                       int quantidade, PapelNoAviso papelRecebido) {

        public Item {
            if (quantidade < 1) {
                throw new IllegalArgumentException("item de aviso sem quantidade");
            }
        }

        public boolean copia() {
            return momento == Momento.ESCALONAMENTO_N1 && papelRecebido == PapelNoAviso.TITULAR
                    || momento == Momento.ESCALONAMENTO_N2 && papelRecebido == PapelNoAviso.GESTOR;
        }
    }
}
