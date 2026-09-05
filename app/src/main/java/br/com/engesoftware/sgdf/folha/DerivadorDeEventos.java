package br.com.engesoftware.sgdf.folha;

import br.com.engesoftware.sgdf.documento.FolhaDeCompetencia;
import br.com.engesoftware.sgdf.documento.ItemDaFolha;
import br.com.engesoftware.sgdf.documento.Rubrica;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deriva eventos da folha — cap. 7.2, história F2-02.
 *
 * <p>Critério de aceite: <i>"rescisão na folha de teste instancia as 6
 * exigências do conjunto, só para aquela matrícula"</i>.
 *
 * <p><b>A garantia da D-04 está na assinatura.</b> O cap. 7.2 abre com "executada
 * quando a folha estruturada da competência chega. <b>Nunca por calendário</b>",
 * e o cap. 5.2 repete no comentário da coluna {@code exigencia.origem}. Este
 * método recebe a folha, o de-para e a movimentação — e <b>não recebe data
 * nenhuma</b>. Não há relógio, não há {@code LocalDate.now()}, não há como
 * derivar sem uma folha na mão. Acrescentar derivação por calendário exigiria
 * mudar a assinatura, o que quebra a compilação de quem chama; um sinalizador
 * booleano não daria essa proteção.
 *
 * <p>A competência sai da própria folha, e as datas de evento saem da
 * movimentação — ambas são dado, não relógio.
 */
public final class DerivadorDeEventos {

    /** Os eventos do cap. 7.2. Os nomes casam com {@code tipo_documental.evento}. */
    public enum Tipo {
        DECIMO_TERCEIRO("13O"),
        FERIAS("FERIAS"),
        RESCISAO("RESCISAO"),
        ADMISSAO("ADMISSAO"),
        NAO_ADESAO_VT("EVENTUAL");

        private final String eventoDoCadastro;

        Tipo(String eventoDoCadastro) {
            this.eventoDoCadastro = eventoDoCadastro;
        }

        /** O valor de {@code tipo_documental.evento} que este evento instancia. */
        public String eventoDoCadastro() {
            return eventoDoCadastro;
        }
    }

    /**
     * Um evento detectado, com a razão.
     *
     * <p>O motivo não é decoração: a exigência derivada aparece no painel ao lado
     * das da matriz, e quem a vê precisa saber por que ela existe. "Rescisão"
     * sozinho não permite conferir; "rubrica RESCISAO: MULTA FGTS 40%" permite.
     *
     * @param data a data do evento, quando a movimentação a fornece — é a âncora
     *             EVENTO do cap. 7.3; nula quando só a rubrica denunciou
     */
    public record Evento(String matricula, Tipo tipo, LocalDate data, String motivo) {}

    private DerivadorDeEventos() {}

    /**
     * @param movimentacoes admissão e desligamento por matrícula; pode ser vazio
     */
    public static List<Evento> detectar(FolhaDeCompetencia folha, DeParaDeRubricas dePara,
                                        Map<String, Movimentacao> movimentacoes) {
        YearMonth competencia = competenciaDe(folha);
        List<Evento> eventos = new ArrayList<>();

        for (ItemDaFolha item : folha.itens()) {
            Set<PapelDaRubrica> papeis = papeisDe(item, dePara);
            Movimentacao mov = movimentacoes.get(item.matricula());

            // 13º antecipado: rubrica de adiantamento presente na competência.
            adicionar(eventos, item, Tipo.DECIMO_TERCEIRO, null, papeis,
                    PapelDaRubrica.ADIANTAMENTO_13, dePara);

            // Férias: rubrica de férias ou abono.
            adicionar(eventos, item, Tipo.FERIAS, null, papeis, PapelDaRubrica.FERIAS, dePara);

            // Rescisão: a movimentação é a fonte forte; a rubrica é o sinal que
            // aparece mesmo quando o cadastro ainda não foi atualizado — e é
            // comum a folha sair antes de alguém registrar o desligamento.
            if (mov != null && mov.desligadoEm(competencia)) {
                eventos.add(new Evento(item.matricula(), Tipo.RESCISAO, mov.desligamento(),
                        "desligamento em " + mov.desligamento() + " (movimentação)"));
            } else {
                adicionar(eventos, item, Tipo.RESCISAO, null, papeis, PapelDaRubrica.RESCISAO,
                        dePara);
            }

            // Admissão: só a movimentação sabe. O contracheque não imprime a data
            // de admissão, e sem ela não há como afirmar que ela caiu NESTA
            // competência — supor pela primeira aparição na folha erraria em toda
            // migração de sistema, que faz todo mundo "aparecer" de uma vez.
            if (mov != null && mov.admitidoEm(competencia)) {
                eventos.add(new Evento(item.matricula(), Tipo.ADMISSAO, mov.admissao(),
                        "admissão em " + mov.admissao() + " (movimentação)"));
            }

            // Não adesão a VT: AUSÊNCIA de rubrica de VT para alocado ativo. É o
            // único gatilho por ausência, e o único que não instancia exigência
            // nova — abre a alternativa condicional do cap. 7.5 (F2-05).
            if (!papeis.contains(PapelDaRubrica.VALE_TRANSPORTE)) {
                eventos.add(new Evento(item.matricula(), Tipo.NAO_ADESAO_VT, null,
                        "sem rubrica de vale-transporte na competência"));
            }
        }
        return List.copyOf(eventos);
    }

    /** As matrículas com um evento — o recorte do "só para aquela matrícula". */
    public static List<String> matriculasCom(List<Evento> eventos, Tipo tipo) {
        Set<String> matriculas = new LinkedHashSet<>();
        for (Evento e : eventos) {
            if (e.tipo() == tipo) {
                matriculas.add(e.matricula());
            }
        }
        return List.copyOf(matriculas);
    }

    private static void adicionar(List<Evento> eventos, ItemDaFolha item, Tipo tipo,
                                  LocalDate data, Set<PapelDaRubrica> papeis,
                                  PapelDaRubrica gatilho, DeParaDeRubricas dePara) {
        if (!papeis.contains(gatilho)) {
            return;
        }
        String qual = primeiraCom(item, gatilho, dePara);
        eventos.add(new Evento(item.matricula(), tipo, data,
                "rubrica " + gatilho + ": " + qual));
    }

    private static Set<PapelDaRubrica> papeisDe(ItemDaFolha item, DeParaDeRubricas dePara) {
        Set<PapelDaRubrica> papeis = EnumSet.noneOf(PapelDaRubrica.class);
        for (Rubrica r : item.proventos()) {
            papeis.add(dePara.papelDe(r));
        }
        for (Rubrica r : item.descontos()) {
            papeis.add(dePara.papelDe(r));
        }
        return papeis;
    }

    private static String primeiraCom(ItemDaFolha item, PapelDaRubrica papel,
                                      DeParaDeRubricas dePara) {
        for (Rubrica r : item.proventos()) {
            if (dePara.papelDe(r) == papel) {
                return r.descricao();
            }
        }
        for (Rubrica r : item.descontos()) {
            if (dePara.papelDe(r) == papel) {
                return r.descricao();
            }
        }
        return "(não localizada)";
    }

    /** A folha traz MM/AAAA; o resto do sistema usa AAAA-MM. */
    static YearMonth competenciaDe(FolhaDeCompetencia folha) {
        String bruta = folha.competencia();
        if (bruta == null) {
            throw new IllegalArgumentException("folha sem competência não deriva evento");
        }
        if (bruta.matches("\\d{2}/\\d{4}")) {
            return YearMonth.of(Integer.parseInt(bruta.substring(3)),
                    Integer.parseInt(bruta.substring(0, 2)));
        }
        return YearMonth.parse(bruta);
    }
}
