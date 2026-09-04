package br.com.engesoftware.sgdf.matriz;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolve quais exigências um ciclo tem — cap. 7.1, histórias F0-04 e F0-07.
 *
 * <p>Porte de produção de {@code especificacao/materializacao/referencia.py},
 * verificado contra a MESMA suíte normativa (18 casos).
 *
 * <p><b>Por que isto é função pura e a gravação está noutro lugar.</b> A abertura
 * de ciclo se decompõe em RESOLVER (quais exigências existem, com que prazo) e
 * PERSISTIR (gravar, congelar a versão da matriz, abrir pendências). A primeira
 * concentra toda a regra de negócio; a segunda é mecânica. Misturá-las produziria
 * a situação em que a única forma de testar a regra é subir a infraestrutura
 * inteira — e a regra é a parte que precisa de dezenas de combinações de
 * vigência, escopo e alocação.
 */
public final class Resolvedor {

    static final Set<String> ESCOPOS = Set.of("CORPORATIVO", "CONTRATO", "PROFISSIONAL");

    private Resolvedor() {}

    /** O contrato-serviço, no que a resolução precisa dele. */
    public record Contrato(String numero, String modalidade) {}

    /**
     * O resultado: o que criar, e o que o operador precisa saber.
     *
     * <p>Os alertas viajam junto com as exigências porque não são erro nem
     * sucesso: "escopo profissional sem alocado ativo" é uma abertura válida que
     * alguém precisa olhar. Separá-los num log faria a abertura parecer limpa.
     */
    public record Abertura(List<ExigenciaResolvida> exigencias, List<String> alertas) {

        public Abertura {
            exigencias = List.copyOf(exigencias);
            alertas = List.copyOf(alertas);
        }
    }

    /**
     * Resolve as exigências do ciclo.
     *
     * @param competencia AAAA-MM
     * @param tipos       o cadastro, por código
     * @param regras      as regras da VERSÃO DA MATRIZ do ciclo — nunca as atuais
     * @param contexto    datas de âncora de evento já conhecidas
     */
    public static Abertura abrirCiclo(Contrato contrato, String competencia,
                                      Map<String, TipoDoCadastro> tipos, List<Regra> regras,
                                      List<Alocacao> alocacoes, Map<String, String> contexto,
                                      Calendario calendario) {
        if (contrato == null) {
            throw new AberturaInvalida("CAMPO_AUSENTE", "contrato");
        }
        if (tipos == null) {
            throw new AberturaInvalida("CAMPO_AUSENTE", "tipos");
        }
        if (regras == null) {
            throw new AberturaInvalida("CAMPO_AUSENTE", "matriz");
        }
        YearMonth mes = mes(competencia);
        LocalDate inicio = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();

        Map<String, String> comCompetencia = new HashMap<>(
                contexto == null ? Map.of() : contexto);
        comCompetencia.put("competencia", competencia);

        // Passo 1: regras vigentes que alcançam este contrato.
        List<Regra> aplicaveis = new ArrayList<>();
        for (Regra r : regras) {
            boolean alcanca = "MODALIDADE".equals(r.alvo())
                    && contrato.modalidade().equals(r.alvoId())
                    || "CONTRATO".equals(r.alvo()) && contrato.numero().equals(r.alvoId());
            if (alcanca && r.vigenteEm(inicio, fim)) {
                aplicaveis.add(r);
            }
        }

        // Passo 2: desempate por especificidade.
        List<Regra> escolhidas = resolverConflitos(aplicaveis, contrato.numero());
        escolhidas.sort(Comparator.comparing(Regra::tipo)
                .thenComparing(r -> r.evento() == null ? "" : r.evento()));

        List<String> alocados = alocados(alocacoes, inicio, fim);
        List<ExigenciaResolvida> exigencias = new ArrayList<>();
        List<String> alertas = new ArrayList<>();

        for (Regra regra : escolhidas) {
            if ("DISPENSADO".equals(regra.obrigatoriedade())) {
                continue;
            }
            TipoDoCadastro tipo = tipos.get(regra.tipo());
            if (tipo == null) {
                throw new AberturaInvalida("TIPO_DESCONHECIDO", regra.tipo());
            }
            if (!ESCOPOS.contains(tipo.escopo())) {
                throw new AberturaInvalida("ESCOPO_DESCONHECIDO",
                        regra.tipo() + ": " + tipo.escopo());
            }

            LocalDate prazo;
            List<String> avisos;
            try {
                Prazo.Resolucao r = Prazo.resolver(regra.prazo(), comCompetencia, calendario);
                prazo = r.data();
                avisos = r.avisos();
            } catch (PrazoInvalido e) {
                if (!"ANCORA_SEM_EVENTO".equals(e.codigo())) {
                    throw new AberturaInvalida("PRAZO_INVALIDO",
                            regra.tipo() + ": " + e.codigo());
                }
                // Cap. 7.3: documento "sob faturamento" existe, mas ainda não tem
                // prazo. A exigência é criada SEM prazo e fica fora da régua até
                // o evento ocorrer. Suprimi-la esconderia o que falta.
                prazo = null;
                avisos = List.of("PRAZO_AGUARDA_EVENTO");
            }

            String criticidade = regra.criticidade() != null ? regra.criticidade()
                    : tipo.criticidade();

            if ("PROFISSIONAL".equals(tipo.escopo())) {
                if (alocados.isEmpty()) {
                    alertas.add(regra.tipo()
                            + ": escopo profissional sem alocado ativo na competência");
                    continue;
                }
                for (String matricula : alocados) {
                    exigencias.add(new ExigenciaResolvida(regra.tipo(), tipo.escopo(),
                            tipo.evento(), criticidade, regra.responsavel(), prazo, matricula,
                            tipo.condicionalGrupo(), avisos));
                }
            } else {
                // CORPORATIVO e CONTRATO geram uma exigência cada. A diferença
                // está no endereçamento na persistência (V004), não aqui.
                exigencias.add(new ExigenciaResolvida(regra.tipo(), tipo.escopo(),
                        tipo.evento(), criticidade, regra.responsavel(), prazo, null,
                        tipo.condicionalGrupo(), avisos));
            }
        }

        if (exigencias.isEmpty()) {
            alertas.add("nenhuma exigência materializada para a competência");
        }
        return new Abertura(exigencias, alertas);
    }

    /**
     * Cap. 7.1 passo 2: contrato sobrepõe modalidade; a mais específica vence.
     *
     * <p>O desempate é por (tipo, evento), não por tipo: um mesmo tipo documental
     * pode ter regras distintas para eventos distintos, e colapsá-las
     * descartaria exigências legítimas.
     *
     * <p>Empate no MESMO alvo é erro de cadastro e vira exceção. Escolher uma
     * das duas em silêncio esconderia uma matriz ambígua — e a matriz é o
     * documento que define o que o cliente pode cobrar.
     */
    static List<Regra> resolverConflitos(List<Regra> regras, String contrato) {
        Map<String, Regra> escolhidas = new LinkedHashMap<>();
        for (Regra r : regras) {
            Regra atual = escolhidas.get(r.chaveDeConflito());
            if (atual == null) {
                escolhidas.put(r.chaveDeConflito(), r);
                continue;
            }
            if (r.alvo().equals(atual.alvo())) {
                throw new AberturaInvalida("REGRA_AMBIGUA", "duas regras de alvo " + r.alvo()
                        + " para " + r.chaveDeConflito() + " no contrato " + contrato);
            }
            if ("CONTRATO".equals(r.alvo())) {
                escolhidas.put(r.chaveDeConflito(), r);
            }
        }
        return new ArrayList<>(escolhidas.values());
    }

    /** Matrículas com ao menos um dia de alocação dentro da competência. */
    static List<String> alocados(List<Alocacao> alocacoes, LocalDate inicio, LocalDate fim) {
        Set<String> ativos = new TreeSet<>();
        if (alocacoes != null) {
            for (Alocacao a : alocacoes) {
                if (a.ativoEm(inicio, fim)) {
                    ativos.add(a.matricula());
                }
            }
        }
        return List.copyOf(ativos);
    }

    /**
     * O prazo de uma exigência corporativa compartilhada: o MENOR entre os ciclos.
     *
     * <p>Ela é uma só, compartilhada entre contratos (V004), mas o prazo vem de
     * uma regra que pode variar por contrato. Se um cliente quer a CND no 5º dia
     * útil e outro no 10º, a CND compartilhada precisa estar lá no 5º — o maior
     * prazo faria o primeiro cliente receber tarde. Ver ERRATA E-10.
     *
     * <p>Prazos ausentes (aguardando evento) não contam: um prazo que ainda não
     * existe não é menor que nada.
     */
    public static LocalDate prazoCorporativo(List<LocalDate> prazos) {
        LocalDate menor = null;
        for (LocalDate p : prazos) {
            if (p != null && (menor == null || p.isBefore(menor))) {
                menor = p;
            }
        }
        return menor;
    }

    static YearMonth mes(String competencia) {
        try {
            return YearMonth.parse(competencia);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new AberturaInvalida("COMPETENCIA_INVALIDA", String.valueOf(competencia));
        }
    }
}
