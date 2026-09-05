package br.com.engesoftware.sgdf.folha;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Admissão e desligamento de um profissional — a "movimentação" do cap. 7.2.
 *
 * <p>Vem do cadastro ({@code profissional}), e não de um calendário: é um fato
 * sobre a pessoa, com data. O que a D-04 proíbe é <b>derivar por calendário</b>
 * — abrir exigência de rescisão porque o mês virou. Ler a data de desligamento
 * que alguém registrou é o oposto disso.
 */
public record Movimentacao(String matricula, LocalDate admissao, LocalDate desligamento) {

    public boolean admitidoEm(YearMonth competencia) {
        return admissao != null && YearMonth.from(admissao).equals(competencia);
    }

    public boolean desligadoEm(YearMonth competencia) {
        return desligamento != null && YearMonth.from(desligamento).equals(competencia);
    }
}
