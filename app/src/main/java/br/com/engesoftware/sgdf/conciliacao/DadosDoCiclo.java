package br.com.engesoftware.sgdf.conciliacao;

import br.com.engesoftware.sgdf.documento.FolhaDeCompetencia;
import br.com.engesoftware.sgdf.matriz.Alocacao;
import br.com.engesoftware.sgdf.documento.RelacaoDeBeneficio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * O que um ciclo de faturamento tem, no momento em que a conciliação roda.
 *
 * <p>Deliberadamente um agregado simples: as regras não vão ao banco nem ao
 * repositório. Recebem o que existe e devolvem um veredito — é o que torna cada
 * regra testável com documentos e sem infraestrutura, e é o que permite
 * reexecutar uma conciliação de seis meses atrás sobre os mesmos documentos e
 * obter o mesmo resultado (cap. 16).
 *
 * @param competencia    competência do ciclo, MM/AAAA
 * @param centroDeCusto  recorte do contrato dentro da folha da empresa
 * @param folha          a folha da competência
 * @param escopoDaFolha  se {@code folha} é a folha inteira da empresa ou o
 *                       recorte de um contrato — ver {@link EscopoDaFolha}
 * @param obrigacoes     guias e tributos devidos
 * @param comprovantes   o que o banco atesta ter sido pago
 * @param alocacoes       quem está alocado e em que período — a base da janela
 *                        pró-rata da R05 (história F2-04)
 * @param comContracheque matrículas com FOL.CONTRACHEQUE vinculado
 * @param eventos         eventos derivados (cap. 7.2) e o conjunto documental de
 *                        cada um — insumo da R06
 * @param relacoes       relações de benefício por tipo documental
 */
public record DadosDoCiclo(String competencia, String centroDeCusto, FolhaDeCompetencia folha,
                           EscopoDaFolha escopoDaFolha,
                           List<Obrigacao> obrigacoes,
                           List<ComprovanteDePagamento> comprovantes,
                           Map<String, RelacaoDeBeneficio> relacoes,
                           List<Alocacao> alocacoes, Set<String> comContracheque,
                           List<ConjuntoDoEvento> eventos) {

    /**
     * O conjunto documental de um evento derivado — insumo da R06.
     *
     * @param prazo    o prazo do EVENTO (cap. 7.3, âncora EVENTO)
     * @param faltando tipos do conjunto que ainda não chegaram
     * @param aso      se o conjunto inclui o ASO demissional, que tem prazo
     *                 próprio: +10 dias corridos (tolerância do cap. 9)
     */
    public record ConjuntoDoEvento(String matricula, String evento, java.time.LocalDate prazo,
                                   List<String> esperados, List<String> faltando,
                                   boolean aso) {

        public ConjuntoDoEvento {
            esperados = List.copyOf(esperados);
            faltando = List.copyOf(faltando);
        }

        public boolean completo() {
            return faltando.isEmpty();
        }
    }

    /**
     * Que população a folha do ciclo cobre.
     *
     * <p>A distinção não é burocracia: as obrigações do bloco corporativo são
     * da EMPRESA inteira. A guia do FGTS de 06/2026 declara 157 trabalhadores e
     * R$ 119.301,51; a folha do contrato DOCAS tem 5 colaboradores e R$
     * 84.456,43 de base. Rodar R09 sobre o recorte devolveria divergência de
     * R$ 115 mil <b>com os dois documentos corretos</b> — o falso positivo do
     * risco P01, produzido por comparar populações diferentes.
     */
    public enum EscopoDaFolha {
        /** Todos os colaboradores da empresa na competência. */
        EMPRESA,
        /** Só os de um centro de custo — o recorte de um contrato-serviço. */
        CONTRATO
    }

    public DadosDoCiclo {
        obrigacoes = List.copyOf(obrigacoes);
        comprovantes = List.copyOf(comprovantes);
        relacoes = Map.copyOf(relacoes);
        alocacoes = List.copyOf(alocacoes);
        comContracheque = Set.copyOf(comContracheque);
        eventos = List.copyOf(eventos);
    }

    public static Construtor de(String competencia) {
        return new Construtor(competencia);
    }

    public List<Obrigacao> obrigacoesDoTipo(String tipo) {
        return obrigacoes.stream().filter(o -> o.tipo().equals(tipo)).toList();
    }

    /** Construtor legível — um ciclo real tem muitas partes e quase todas opcionais. */
    public static final class Construtor {
        private final String competencia;
        private String centroDeCusto;
        private FolhaDeCompetencia folha;
        private EscopoDaFolha escopoDaFolha;
        private final List<Obrigacao> obrigacoes = new ArrayList<>();
        private final List<ComprovanteDePagamento> comprovantes = new ArrayList<>();
        private final Map<String, RelacaoDeBeneficio> relacoes = new LinkedHashMap<>();
        private final List<Alocacao> alocacoes = new ArrayList<>();
        private final Set<String> comContracheque = new LinkedHashSet<>();
        private final List<ConjuntoDoEvento> eventos = new ArrayList<>();

        private Construtor(String competencia) {
            this.competencia = competencia;
        }

        public Construtor noCentroDeCusto(String centro) {
            this.centroDeCusto = centro;
            return this;
        }

        /** A folha inteira da empresa — o que as regras corporativas exigem. */
        public Construtor comFolhaDaEmpresa(FolhaDeCompetencia folha) {
            this.folha = folha;
            this.escopoDaFolha = EscopoDaFolha.EMPRESA;
            return this;
        }

        /** O recorte de um contrato — o que as regras de contrato exigem. */
        public Construtor comFolhaDoContrato(FolhaDeCompetencia folha) {
            this.folha = folha;
            this.escopoDaFolha = EscopoDaFolha.CONTRATO;
            return this;
        }

        public Construtor comObrigacao(Obrigacao o) {
            obrigacoes.add(o);
            return this;
        }

        public Construtor comComprovante(ComprovanteDePagamento c) {
            comprovantes.add(c);
            return this;
        }

        public Construtor comRelacao(String tipo, RelacaoDeBeneficio r) {
            relacoes.put(tipo, r);
            return this;
        }

        public Construtor comAlocacao(Alocacao a) {
            alocacoes.add(a);
            return this;
        }

        public Construtor comContrachequeDe(String matricula) {
            comContracheque.add(matricula);
            return this;
        }

        public Construtor comEvento(ConjuntoDoEvento evento) {
            eventos.add(evento);
            return this;
        }

        public DadosDoCiclo construir() {
            return new DadosDoCiclo(competencia, centroDeCusto, folha, escopoDaFolha,
                    obrigacoes, comprovantes, relacoes, alocacoes, comContracheque, eventos);
        }
    }
}
