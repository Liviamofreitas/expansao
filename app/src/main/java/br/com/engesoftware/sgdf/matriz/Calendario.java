package br.com.engesoftware.sgdf.matriz;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Os feriados de um contrato, já resolvidos para a sua UF e município.
 *
 * <p>A resolução por escopo — nacional + estadual + municipal — acontece na
 * consulta ao cadastro, não aqui: esta classe recebe o conjunto final de datas
 * não úteis. Manter a resolução fora torna o cálculo de prazo uma função pura,
 * testável contra a suíte de conformidade sem banco nenhum.
 */
public record Calendario(Set<LocalDate> feriados) {

    public Calendario {
        feriados = Set.copyOf(feriados);
    }

    public static Calendario vazio() {
        return new Calendario(Set.of());
    }

    public boolean eUtil(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY
                && d.getDayOfWeek() != DayOfWeek.SUNDAY
                && !feriados.contains(d);
    }

    public LocalDate proximoUtil(LocalDate d) {
        LocalDate atual = d;
        while (!eUtil(atual)) {
            atual = atual.plusDays(1);
        }
        return atual;
    }

    public LocalDate anteriorUtil(LocalDate d) {
        LocalDate atual = d;
        while (!eUtil(atual)) {
            atual = atual.minusDays(1);
        }
        return atual;
    }
}
