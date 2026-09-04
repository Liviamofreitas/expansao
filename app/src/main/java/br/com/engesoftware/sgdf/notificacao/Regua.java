package br.com.engesoftware.sgdf.notificacao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A régua do cap. 11.1 — história F1-09.
 *
 * <p>Critério de aceite: <i>"régua registra o que enviaria, nada é enviado"</i>.
 * Esta classe é a primeira metade: dado o dia e as pendências, ela diz o que
 * seria enviado, para quem, consolidado. Ela <b>não envia</b>, e não é por um
 * sinalizador — não existe transporte no pacote inteiro. O modo sombra do
 * cap. 11.2 é hoje uma propriedade estrutural do código, não uma configuração
 * que alguém pode inverter por engano.
 *
 * <p>É pura de propósito: recebe as pendências e a função que resolve
 * destinatário, e devolve os avisos. Sem isso, testar a régua exigiria banco, e
 * a régua é justamente a parte que precisa ser exercitada com dezenas de
 * combinações de data e prazo.
 */
public final class Regua {

    /** Resolve (família, papel) → destinatário. Nulo quando não há cadastro. */
    public interface Cadastro {
        Destinatario quem(String familia, PapelNoAviso papel);
    }

    private Regua() {}

    /**
     * O que a régua enviaria hoje.
     *
     * @param cicloPronto o ciclo alcançou PRONTO nesta data (cap. 6.2)
     */
    public static Resultado avisos(UUID cicloId, LocalDate hoje, List<PendenciaAberta> pendencias,
                                   boolean cicloPronto, Cadastro cadastro) {
        Map<String, Acumulador> porDestinatario = new LinkedHashMap<>();
        List<String> semDestinatario = new ArrayList<>();

        for (PendenciaAberta p : pendencias) {
            for (Momento momento : momentosDe(p, hoje)) {
                for (PapelNoAviso papel : papeisDe(momento)) {
                    Destinatario d = cadastro.quem(p.familia(), papel);
                    if (d == null) {
                        // NÃO É SILÊNCIO ACEITÁVEL.
                        //
                        // Uma pendência cujo titular não está cadastrado
                        // simplesmente não seria cobrada, e ninguém saberia: o
                        // painel a mostra pendente, e a área jura que nunca foi
                        // avisada. As duas versões estariam certas.
                        semDestinatario.add(p.tipoCodigo() + " (" + p.familia() + "): sem "
                                + papel + " cadastrado para " + momento);
                        continue;
                    }
                    porDestinatario
                            .computeIfAbsent(d.email(), e -> new Acumulador(d))
                            .somar(momento, p, papel);
                }
            }
        }

        if (cicloPronto) {
            for (PapelNoAviso papel : List.of(PapelNoAviso.TITULAR, PapelNoAviso.GESTOR,
                    PapelNoAviso.DAF)) {
                for (String familia : familias(pendencias)) {
                    Destinatario d = cadastro.quem(familia, papel);
                    if (d != null) {
                        porDestinatario.computeIfAbsent(d.email(), e -> new Acumulador(d))
                                .fechamento(familia, papel);
                    }
                }
            }
        }

        List<Aviso> avisos = new ArrayList<>();
        for (Acumulador a : porDestinatario.values()) {
            avisos.add(new Aviso(cicloId, a.destinatario, hoje, a.itens()));
        }
        return new Resultado(List.copyOf(avisos), List.copyOf(semDestinatario));
    }

    /**
     * Em que momentos esta pendência dispara hoje.
     *
     * <p>Pode ser mais de um? Não, com os marcos do capítulo: {@code +2}, {@code
     * 0}, {@code -2} e {@code -5} são dias distintos. Devolver lista mesmo assim
     * evita que acrescentar um marco no futuro exija reescrever quem chama.
     */
    static List<Momento> momentosDe(PendenciaAberta p, LocalDate hoje) {
        if (!p.aberta()) {
            return p.resolvidaEm().equals(hoje) ? List.of(Momento.RESOLVIDA) : List.of();
        }
        long dias = java.time.temporal.ChronoUnit.DAYS.between(hoje, p.prazo());
        List<Momento> momentos = new ArrayList<>();
        for (Momento m : Momento.values()) {
            if (m.diasAteOPrazo() != null && m.diasAteOPrazo() == dias) {
                momentos.add(m);
            }
        }
        return momentos;
    }

    /** Quem recebe cada momento — a coluna "destinatário" do cap. 11.1. */
    static List<PapelNoAviso> papeisDe(Momento momento) {
        return switch (momento) {
            case PREVENTIVA -> List.of(PapelNoAviso.TITULAR);
            case COBRANCA -> List.of(PapelNoAviso.TITULAR, PapelNoAviso.SUBSTITUTO);
            case ESCALONAMENTO_N1 -> List.of(PapelNoAviso.GESTOR, PapelNoAviso.TITULAR);
            case ESCALONAMENTO_N2 -> List.of(PapelNoAviso.DAF, PapelNoAviso.GESTOR);
            // "Quem foi cobrado" — titular e substituto receberam a cobrança.
            case RESOLVIDA -> List.of(PapelNoAviso.TITULAR, PapelNoAviso.SUBSTITUTO);
            case FECHAMENTO -> List.of(PapelNoAviso.TITULAR, PapelNoAviso.GESTOR,
                    PapelNoAviso.DAF);
        };
    }

    private static List<String> familias(List<PendenciaAberta> pendencias) {
        List<String> familias = new ArrayList<>();
        for (PendenciaAberta p : pendencias) {
            if (!familias.contains(p.familia())) {
                familias.add(p.familia());
            }
        }
        return familias;
    }

    /**
     * O que sairia, e o que não sairia por falta de cadastro.
     *
     * <p>As duas listas juntas de propósito: quem opera a régua precisa das
     * duas na mesma tela. Uma execução "sem erro" que cobrou 12 pessoas e deixou
     * 4 pendências sem dono parece bem-sucedida e não é.
     */
    public record Resultado(List<Aviso> avisos, List<String> semDestinatario) {

        public boolean completo() {
            return semDestinatario.isEmpty();
        }
    }

    /** Junta os itens de uma pessoa antes de virarem um aviso só. */
    private static final class Acumulador {
        private final Destinatario destinatario;
        private final Map<String, Aviso.Item> itens = new LinkedHashMap<>();

        Acumulador(Destinatario destinatario) {
            this.destinatario = destinatario;
        }

        void somar(Momento momento, PendenciaAberta p, PapelNoAviso papel) {
            // Agrupa por (momento, tipo, prazo): três contracheques vencendo no
            // mesmo dia são uma linha com quantidade 3, não três linhas iguais.
            String chave = momento + "|" + p.tipoCodigo() + "|" + p.prazo();
            Aviso.Item anterior = itens.get(chave);
            itens.put(chave, anterior == null
                    ? new Aviso.Item(momento, p.tipoCodigo(), p.familia(), p.prazo(), 1, papel)
                    : new Aviso.Item(momento, p.tipoCodigo(), p.familia(), p.prazo(),
                            anterior.quantidade() + 1, papel));
        }

        void fechamento(String familia, PapelNoAviso papel) {
            itens.putIfAbsent("FECHAMENTO|" + familia, new Aviso.Item(Momento.FECHAMENTO,
                    "CICLO", familia, null, 1, papel));
        }

        List<Aviso.Item> itens() {
            return List.copyOf(itens.values());
        }
    }
}
