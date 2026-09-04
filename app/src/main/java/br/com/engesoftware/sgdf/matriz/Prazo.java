package br.com.engesoftware.sgdf.matriz;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prazo estruturado — cap. 7.3, história F0-06.
 *
 * <p>Porte de produção de {@code especificacao/prazo/referencia.py}, verificado
 * contra a MESMA suíte normativa ({@code casos.json}, 32 casos). A suíte é o
 * oráculo: divergir dela é divergir da especificação, não do meu porte.
 *
 * <p><b>Três âncoras, três aritméticas diferentes, e a do meio é a que engana.</b>
 *
 * <ul>
 *   <li>{@code INICIO_COMPETENCIA} é <b>ordinal</b>: o N-ésimo dia do mês. "5º
 *       dia útil" conta dias úteis a partir do dia 1.</li>
 *   <li>{@code ATESTE}, {@code SOLICITACAO_FATURAMENTO} e {@code EVENTO} são
 *       <b>aditivas</b>: base mais N dias, rolando para <b>frente</b>.</li>
 *   <li>{@code FIM_COMPETENCIA} é aditiva sobre o último dia do mês, mas a base
 *       rola para <b>TRÁS</b>. "Último dia útil de abril" tem de cair em abril;
 *       rolar para frente cairia em maio e mudaria a competência do prazo. É o
 *       inverso das âncoras de evento, e é intencional — achado E-08.</li>
 * </ul>
 */
public final class Prazo {

    static final Set<String> ORDINAIS = Set.of("INICIO_COMPETENCIA");
    static final Set<String> FIM = Set.of("FIM_COMPETENCIA");
    static final Map<String, String> DE_EVENTO = Map.of(
            "ATESTE", "ateste",
            "SOLICITACAO_FATURAMENTO", "solicitacao_faturamento",
            "EVENTO", "evento");
    static final Set<String> TIPOS_DIA = Set.of("UTIL", "CORRIDO");

    private Prazo() {}

    /**
     * A data e os ajustes que precisaram ser feitos.
     *
     * <p>Devolver os avisos junto com a data é deliberado. Um ajuste que não
     * aparece em lugar nenhum vira uma data inexplicável na tela do operador
     * seis meses depois; quem chama grava os avisos na trilha e os exibe ao lado
     * do prazo.
     */
    public record Resolucao(LocalDate data, List<String> avisos) {

        public Resolucao {
            avisos = List.copyOf(avisos);
        }

        static Resolucao de(LocalDate data) {
            return new Resolucao(data, List.of());
        }
    }

    /** O prazo como está no cadastro — {@code regra_exigibilidade.prazo}. */
    public record Cadastrado(String ancora, String tipoDia, Integer offset) {

        /**
         * Constrói a partir de valores ainda sem tipo — o que sai de um
         * {@code jsonb} ou de um parser de JSON.
         *
         * <p><b>É aqui que "booleano não é inteiro" precisa ser dito.</b> Java
         * não confunde {@code Boolean} com {@code Integer}, então a assinatura
         * tipada acima já protege quem chama com valores tipados. O perigo está
         * na fronteira: {@code regra_exigibilidade.prazo} é jsonb, e um leitor
         * distraído que chame {@code asInt()} sobre {@code true} recebe 1 e
         * calcula um prazo silenciosamente errado — exatamente o que o caso
         * ERRO-07 da suíte normativa existe para impedir.
         *
         * <p>{@code 5.0} também é recusado, e não convertido: a suíte trata
         * fracionário como cadastro malformado, e converter esconderia que
         * alguém digitou um número onde a coluna espera outro.
         */
        public static Cadastrado deValores(Object ancora, Object tipoDia, Object offset) {
            if (offset == null) {
                throw new PrazoInvalido("CAMPO_AUSENTE", "offset");
            }
            if (!(offset instanceof Integer || offset instanceof Long
                    || offset instanceof java.math.BigInteger)) {
                throw new PrazoInvalido("OFFSET_NAO_INTEIRO", String.valueOf(offset));
            }
            long valor = ((Number) offset).longValue();
            if (valor < Integer.MIN_VALUE || valor > Integer.MAX_VALUE) {
                throw new PrazoInvalido("OFFSET_NAO_INTEIRO", String.valueOf(offset));
            }
            return new Cadastrado(texto(ancora, "ancora"), texto(tipoDia, "tipo_dia"),
                    (int) valor);
        }

        private static String texto(Object valor, String campo) {
            if (valor == null) {
                throw new PrazoInvalido("CAMPO_AUSENTE", campo);
            }
            if (!(valor instanceof String s)) {
                throw new PrazoInvalido(
                        "ancora".equals(campo) ? "ANCORA_DESCONHECIDA" : "TIPO_DIA_DESCONHECIDO",
                        String.valueOf(valor));
            }
            return s;
        }
    }

    /**
     * Resolve. Função pura: sem relógio, sem banco.
     *
     * @param contexto a competência e as datas de evento conhecidas
     */
    public static Resolucao resolver(Cadastrado prazo, Map<String, String> contexto,
                                     Calendario calendario) {
        if (prazo == null) {
            throw new PrazoInvalido("CAMPO_AUSENTE", "prazo");
        }
        if (prazo.ancora() == null) {
            throw new PrazoInvalido("CAMPO_AUSENTE", "ancora");
        }
        if (prazo.tipoDia() == null) {
            throw new PrazoInvalido("CAMPO_AUSENTE", "tipo_dia");
        }
        if (prazo.offset() == null) {
            throw new PrazoInvalido("CAMPO_AUSENTE", "offset");
        }
        String ancora = prazo.ancora();
        String tipoDia = prazo.tipoDia();
        int offset = prazo.offset();

        if (!ORDINAIS.contains(ancora) && !FIM.contains(ancora)
                && !DE_EVENTO.containsKey(ancora)) {
            throw new PrazoInvalido("ANCORA_DESCONHECIDA", ancora);
        }
        if (!TIPOS_DIA.contains(tipoDia)) {
            throw new PrazoInvalido("TIPO_DIA_DESCONHECIDO", tipoDia);
        }

        if (ORDINAIS.contains(ancora)) {
            YearMonth mes = competencia(contexto);
            return ordinal(mes.atDay(1), mes.atEndOfMonth(), offset, tipoDia, calendario);
        }
        if (FIM.contains(ancora)) {
            return fimDaCompetencia(competencia(contexto).atEndOfMonth(), offset, tipoDia,
                    calendario);
        }

        String chave = DE_EVENTO.get(ancora);
        String bruto = contexto.get(chave);
        if (bruto == null || bruto.isBlank()) {
            // Cap. 7.3: documentos "sob faturamento" só entram na régua APÓS o
            // evento. Sem o evento não há prazo — e isso não é erro de cadastro,
            // é ausência de gatilho. Quem chama trata como "sem prazo ainda".
            throw new PrazoInvalido("ANCORA_SEM_EVENTO", chave);
        }
        LocalDate base;
        try {
            base = LocalDate.parse(bruto);
        } catch (DateTimeParseException e) {
            throw new PrazoInvalido("DATA_BASE_INVALIDA", bruto);
        }
        return aditivo(base, offset, tipoDia, calendario);
    }

    static YearMonth competencia(Map<String, String> contexto) {
        String bruto = contexto == null ? null : contexto.get("competencia");
        if (bruto == null || bruto.isBlank()) {
            throw new PrazoInvalido("CONTEXTO_AUSENTE", "competencia");
        }
        try {
            return YearMonth.parse(bruto);
        } catch (DateTimeParseException e) {
            throw new PrazoInvalido("COMPETENCIA_INVALIDA", bruto);
        }
    }

    /**
     * N-ésimo dia (útil ou corrido) do intervalo.
     *
     * <p>Se o período não tiver {@code offset} dias do tipo pedido, devolve o
     * último disponível com {@code AJUSTE_FIM_DE_PERIODO}. Oito linhas do
     * Anexo 1 pedem o dia 30 ou 31 da competência, e em fevereiro esses dias não
     * existem: sob leitura ordinal estrita a abertura do ciclo de fevereiro
     * falharia para seis contratos, e o princípio 1 do cap. 1 diz que o sistema
     * nunca trava o faturamento por falta de configuração própria.
     */
    static Resolucao ordinal(LocalDate inicio, LocalDate fim, int offset, String tipoDia,
                             Calendario cal) {
        if (offset < 1) {
            throw new PrazoInvalido("OFFSET_ORDINAL_INVALIDO",
                    "âncora ordinal exige offset >= 1, recebido " + offset);
        }
        int contados = 0;
        LocalDate ultimo = null;
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            if ("CORRIDO".equals(tipoDia) || cal.eUtil(d)) {
                contados++;
                ultimo = d;
                if (contados == offset) {
                    return Resolucao.de(d);
                }
            }
        }
        if (ultimo == null) {
            throw new PrazoInvalido("PERIODO_SEM_DIA_UTIL",
                    "o período " + inicio + ".." + fim + " não tem nenhum dia do tipo "
                            + tipoDia);
        }
        return new Resolucao(ultimo, List.of("AJUSTE_FIM_DE_PERIODO"));
    }

    /** base + offset dias. Em dia útil, a própria base rola para o próximo útil. */
    static Resolucao aditivo(LocalDate base, int offset, String tipoDia, Calendario cal) {
        if (offset < 0) {
            throw new PrazoInvalido("OFFSET_NEGATIVO", "offset deve ser >= 0, recebido "
                    + offset);
        }
        if ("CORRIDO".equals(tipoDia)) {
            return Resolucao.de(base.plusDays(offset));
        }
        LocalDate d = cal.proximoUtil(base);
        List<String> avisos = d.equals(base) ? List.of() : List.of("AJUSTE_BASE_NAO_UTIL");
        for (int i = 0; i < offset; i++) {
            d = cal.proximoUtil(d.plusDays(1));
        }
        return new Resolucao(d, avisos);
    }

    /** Último dia da competência, mais offset. Em dia útil a base rola para TRÁS. */
    static Resolucao fimDaCompetencia(LocalDate fim, int offset, String tipoDia,
                                      Calendario cal) {
        if (offset < 0) {
            throw new PrazoInvalido("OFFSET_NEGATIVO", "offset deve ser >= 0, recebido "
                    + offset);
        }
        if ("CORRIDO".equals(tipoDia)) {
            return Resolucao.de(fim.plusDays(offset));
        }
        LocalDate d = cal.anteriorUtil(fim);
        for (int i = 0; i < offset; i++) {
            d = cal.proximoUtil(d.plusDays(1));
        }
        return Resolucao.de(d);
    }
}
