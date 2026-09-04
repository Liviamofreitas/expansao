// =============================================================================
// SGDF — prazo estruturado em Java 21 (decisão A13: Java 21 + Spring Boot)
//
// Porta a implementação de referência (especificacao/prazo/referencia.py) para a
// stack escolhida e roda contra a MESMA suíte de conformidade:
//
//     python3 especificacao/prazo/verificar.py \
//         --comando 'java especificacao/prazo/java/Prazo.java'
//
// O propósito é provar que casos.json é de fato agnóstico de linguagem, e dar ao
// time o ponto de partida em Java. Não é código de produção: numa aplicação
// Spring Boot isto vira um componente com Jackson no lugar do parser de JSON
// caseiro daqui, que existe só para o arquivo rodar sem dependências.
//
// Contrato: lê {"prazo":…, "contexto":…, "feriados":[…]} no stdin e escreve
// {"data":"AAAA-MM-DD","avisos":[…]} ou {"erro":"CODIGO"} no stdout.
// =============================================================================

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Prazo {

    static final Set<String> ANCORAS_ORDINAIS = Set.of("INICIO_COMPETENCIA");
    static final Set<String> ANCORAS_FIM = Set.of("FIM_COMPETENCIA");
    static final Map<String, String> ANCORAS_EVENTO = Map.of(
            "ATESTE", "ateste",
            "SOLICITACAO_FATURAMENTO", "solicitacao_faturamento",
            "EVENTO", "evento");
    static final Set<String> TIPOS_DIA = Set.of("UTIL", "CORRIDO");

    /** Cadastro de prazo malformado ou insatisfazível, com código estável. */
    static final class PrazoInvalido extends RuntimeException {
        final String codigo;
        PrazoInvalido(String codigo) { super(codigo); this.codigo = codigo; }
    }

    record Resolucao(LocalDate data, List<String> avisos) {}

    /** Feriados já resolvidos para a UF e o município do contrato. */
    static final class Calendario {
        private final Set<LocalDate> feriados;
        Calendario(Set<LocalDate> feriados) { this.feriados = feriados; }

        boolean eUtil(LocalDate d) {
            return d.getDayOfWeek() != DayOfWeek.SATURDAY
                && d.getDayOfWeek() != DayOfWeek.SUNDAY
                && !feriados.contains(d);
        }
        LocalDate proximoUtil(LocalDate d) {
            while (!eUtil(d)) d = d.plusDays(1);
            return d;
        }
        LocalDate anteriorUtil(LocalDate d) {
            while (!eUtil(d)) d = d.minusDays(1);
            return d;
        }
    }

    // -------------------------------------------------------------------------
    // Resolução — mesma semântica do módulo Python, caso a caso.
    // -------------------------------------------------------------------------

    static Resolucao ordinal(LocalDate inicio, LocalDate fim, int offset,
                             String tipoDia, Calendario cal) {
        if (offset < 1) throw new PrazoInvalido("OFFSET_ORDINAL_INVALIDO");
        int contados = 0;
        LocalDate d = inicio, ultimo = null;
        while (!d.isAfter(fim)) {
            if (tipoDia.equals("CORRIDO") || cal.eUtil(d)) {
                contados++;
                ultimo = d;
                if (contados == offset) return new Resolucao(d, List.of());
            }
            d = d.plusDays(1);
        }
        if (ultimo == null) throw new PrazoInvalido("PERIODO_SEM_DIA_UTIL");
        return new Resolucao(ultimo, List.of("AJUSTE_FIM_DE_PERIODO"));
    }

    static Resolucao aditivo(LocalDate base, int offset, String tipoDia, Calendario cal) {
        if (offset < 0) throw new PrazoInvalido("OFFSET_NEGATIVO");
        if (tipoDia.equals("CORRIDO")) return new Resolucao(base.plusDays(offset), List.of());
        LocalDate d = cal.proximoUtil(base);
        List<String> avisos = d.equals(base) ? List.of() : List.of("AJUSTE_BASE_NAO_UTIL");
        for (int i = 0; i < offset; i++) d = cal.proximoUtil(d.plusDays(1));
        return new Resolucao(d, avisos);
    }

    /** Em dia útil esta âncora rola para TRÁS: o último dia útil de abril fica em abril. */
    static Resolucao fimCompetencia(LocalDate fim, int offset, String tipoDia, Calendario cal) {
        if (offset < 0) throw new PrazoInvalido("OFFSET_NEGATIVO");
        if (tipoDia.equals("CORRIDO")) return new Resolucao(fim.plusDays(offset), List.of());
        LocalDate d = cal.anteriorUtil(fim);
        for (int i = 0; i < offset; i++) d = cal.proximoUtil(d.plusDays(1));
        return new Resolucao(d, List.of());
    }

    static Resolucao resolver(Map<String, Object> prazo, Map<String, Object> contexto,
                              Calendario cal) {
        for (String campo : List.of("ancora", "tipo_dia", "offset")) {
            if (!prazo.containsKey(campo)) throw new PrazoInvalido("CAMPO_AUSENTE");
        }
        Object ancoraBruta = prazo.get("ancora");
        Object tipoDiaBruto = prazo.get("tipo_dia");
        if (!(ancoraBruta instanceof String ancora) || !ANCORAS_ORDINAIS.contains(ancora)
                && !ANCORAS_FIM.contains(ancora) && !ANCORAS_EVENTO.containsKey(ancora)) {
            throw new PrazoInvalido("ANCORA_DESCONHECIDA");
        }
        if (!(tipoDiaBruto instanceof String tipoDia) || !TIPOS_DIA.contains(tipoDia)) {
            throw new PrazoInvalido("TIPO_DIA_DESCONHECIDO");
        }
        // Booleano não é inteiro: em linguagens onde true vale 1, aceitá-lo
        // produziria um prazo silenciosamente errado.
        Object offsetBruto = prazo.get("offset");
        if (!(offsetBruto instanceof Double) && !(offsetBruto instanceof Integer)) {
            throw new PrazoInvalido("OFFSET_NAO_INTEIRO");
        }
        double offsetD = ((Number) offsetBruto).doubleValue();
        if (offsetD != Math.floor(offsetD)) throw new PrazoInvalido("OFFSET_NAO_INTEIRO");
        int offset = (int) offsetD;

        if (ANCORAS_ORDINAIS.contains(ancora) || ANCORAS_FIM.contains(ancora)) {
            Object comp = contexto.get("competencia");
            if (!(comp instanceof String competencia) || competencia.isEmpty()) {
                throw new PrazoInvalido("CONTEXTO_AUSENTE");
            }
            YearMonth ym;
            try {
                ym = YearMonth.parse(competencia);
            } catch (DateTimeParseException e) {
                throw new PrazoInvalido("COMPETENCIA_INVALIDA");
            }
            return ANCORAS_ORDINAIS.contains(ancora)
                    ? ordinal(ym.atDay(1), ym.atEndOfMonth(), offset, tipoDia, cal)
                    : fimCompetencia(ym.atEndOfMonth(), offset, tipoDia, cal);
        }

        Object bruto = contexto.get(ANCORAS_EVENTO.get(ancora));
        if (!(bruto instanceof String texto) || texto.isEmpty()) {
            throw new PrazoInvalido("ANCORA_SEM_EVENTO");
        }
        try {
            return aditivo(LocalDate.parse(texto), offset, tipoDia, cal);
        } catch (DateTimeParseException e) {
            throw new PrazoInvalido("DATA_BASE_INVALIDA");
        }
    }

    // -------------------------------------------------------------------------
    // Entrada e saída. O parser abaixo cobre só o subconjunto de JSON que a
    // suíte usa; em produção, Jackson.
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        String entrada = lerTudo(System.in);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raiz = (Map<String, Object>) new Json(entrada).valor();
            @SuppressWarnings("unchecked")
            Map<String, Object> prazo = (Map<String, Object>) raiz.get("prazo");
            @SuppressWarnings("unchecked")
            Map<String, Object> contexto = raiz.get("contexto") == null
                    ? Map.of() : (Map<String, Object>) raiz.get("contexto");
            @SuppressWarnings("unchecked")
            List<Object> feriados = raiz.get("feriados") == null
                    ? List.of() : (List<Object>) raiz.get("feriados");

            Set<LocalDate> datas = new HashSet<>();
            for (Object f : feriados) datas.add(LocalDate.parse((String) f));

            Resolucao r = resolver(prazo, contexto, new Calendario(datas));
            StringBuilder sb = new StringBuilder("{\"data\":\"").append(r.data()).append("\",\"avisos\":[");
            for (int i = 0; i < r.avisos().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(r.avisos().get(i)).append('"');
            }
            System.out.println(sb.append("]}"));
        } catch (PrazoInvalido e) {
            System.out.println("{\"erro\":\"" + e.codigo + "\"}");
        }
    }

    static String lerTudo(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Parser mínimo do subconjunto de JSON usado pela suíte. */
    static final class Json {
        private final String s;
        private int i;
        Json(String s) { this.s = s; }

        Object valor() {
            pular();
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> objeto();
                case '[' -> lista();
                case '"' -> texto();
                case 't' -> { i += 4; yield Boolean.TRUE; }
                case 'f' -> { i += 5; yield Boolean.FALSE; }
                case 'n' -> { i += 4; yield null; }
                default -> numero();
            };
        }
        private void pular() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; pular();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                pular();
                String chave = texto();
                pular(); i++;               // ':'
                m.put(chave, valor());
                pular();
                if (s.charAt(i) == ',') { i++; continue; }
                i++; return m;              // '}'
            }
        }
        private List<Object> lista() {
            List<Object> l = new ArrayList<>();
            i++; pular();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(valor());
                pular();
                if (s.charAt(i) == ',') { i++; continue; }
                i++; return l;              // ']'
            }
        }
        private String texto() {
            StringBuilder sb = new StringBuilder();
            i++;                            // abre aspas
            while (s.charAt(i) != '"') {
                if (s.charAt(i) == '\\') {
                    i++;
                    char e = s.charAt(i++);
                    sb.append(switch (e) {
                        case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r';
                        case 'u' -> { char u = (char) Integer.parseInt(s.substring(i, i + 4), 16);
                                      i += 4; yield u; }
                        default -> e;
                    });
                } else {
                    sb.append(s.charAt(i++));
                }
            }
            i++;                            // fecha aspas
            return sb.toString();
        }
        private Double numero() {
            int ini = i;
            while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
            return Double.valueOf(s.substring(ini, i));
        }
    }

    private Prazo() {}
}
