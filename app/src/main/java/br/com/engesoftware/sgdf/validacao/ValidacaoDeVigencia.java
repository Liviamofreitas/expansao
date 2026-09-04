package br.com.engesoftware.sgdf.validacao;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * V5 — a certidão está vigente na data prevista da NF, e a natureza é aceita.
 *
 * <p>Cap. 8.4: {@code validade ≥ data prevista da NF} e
 * {@code natureza ∈ {negativa, positiva c/ efeito de negativa}}.
 *
 * <p><b>Duas correções que a massa real impôs.</b>
 *
 * <p>A primeira é o achado A10: a natureza tem <b>três</b> valores. A certidão
 * cível e criminal real é POSITIVA, e reprová-la automaticamente travaria o
 * faturamento de um contrato regular. O cadastro decide, por tipo documental,
 * se POSITIVA é aceitável; quando é, o resultado sai aprovado <b>com ressalva
 * registrada</b>, para que a decisão humana tenha o que ver.
 *
 * <p>A segunda é o achado A9: o CRF do FGTS vale <b>30 dias</b> (11/07 a
 * 09/08). A janela é curta o bastante para o documento vencer dentro da própria
 * competência, e por isso V5 avisa quando a validade está por vencer mesmo
 * aprovando — a régua de cobrança precisa saber antes de o prazo estourar.
 */
public final class ValidacaoDeVigencia {

    public static final String CODIGO = "V5";

    /** Dias de antecedência a partir dos quais o vencimento próximo é avisado. */
    public static final int AVISO_DE_VENCIMENTO_EM_DIAS = 15;

    private final boolean aceitandoPositiva;

    /** Padrão conservador: certidão positiva não passa sem decisão do cadastro. */
    public ValidacaoDeVigencia() {
        this(false);
    }

    /**
     * @param aceitandoPositiva o que o cadastro decidiu para este tipo documental
     *                          quanto ao achado A10
     */
    public ValidacaoDeVigencia(boolean aceitandoPositiva) {
        this.aceitandoPositiva = aceitandoPositiva;
    }

    /**
     * @param validade      até quando a certidão vale
     * @param natureza      lida do título
     * @param dataPrevistaDaNf data em que a nota fiscal deve ser emitida
     */
    public ResultadoDeValidacao validar(LocalDate validade, NaturezaDaCertidao natureza,
                                        LocalDate dataPrevistaDaNf) {
        if (dataPrevistaDaNf == null) {
            return new ResultadoDeValidacao(CODIGO, Veredito.NAO_APLICAVEL,
                    "o ciclo não tem data prevista de emissão da NF: "
                            + "V5 não tem contra o que conferir a vigência",
                    Map.of());
        }
        if (validade == null) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "a validade não foi extraída do documento: "
                            + "não há como afirmar que a certidão está vigente",
                    Map.of());
        }

        if (validade.isBefore(dataPrevistaDaNf)) {
            long dias = ChronoUnit.DAYS.between(validade, dataPrevistaDaNf);
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "a certidão vence em " + validade + " e a NF está prevista para "
                            + dataPrevistaDaNf + " — " + dias + " dia(s) depois. "
                            + "É preciso reemitir a certidão",
                    Map.of("validade", List.of(validade.toString()),
                            "prevista_nf", List.of(dataPrevistaDaNf.toString())));
        }

        if (natureza == null) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "a natureza da certidão não foi extraída: não há como saber se é "
                            + "negativa, positiva com efeito de negativa, ou positiva",
                    Map.of());
        }

        if (natureza == NaturezaDaCertidao.POSITIVA && !aceitandoPositiva) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "a certidão é POSITIVA e o cadastro deste tipo não aceita positiva. "
                            + "Vigente até " + validade + ". Se a exigência contratual é "
                            + "apresentar a certidão e não que ela seja negativa, "
                            + "o cadastro do tipo precisa dizer isso (achado A10)",
                    Map.of("natureza", List.of(natureza.name())));
        }

        java.util.Map<String, List<String>> detalhe = new java.util.LinkedHashMap<>();
        detalhe.put("validade", List.of(validade.toString()));
        detalhe.put("natureza", List.of(natureza.name()));

        if (natureza == NaturezaDaCertidao.POSITIVA) {
            detalhe.put("ressalva", List.of("certidão POSITIVA aceita por decisão de cadastro: "
                    + "a conferência documental está satisfeita e o juízo sobre o conteúdo "
                    + "é humano (cap. 1, princípio 7)"));
        }

        long diasRestantes = ChronoUnit.DAYS.between(dataPrevistaDaNf, validade);
        if (diasRestantes <= AVISO_DE_VENCIMENTO_EM_DIAS) {
            detalhe.put("aviso", List.of("a certidão vence " + diasRestantes
                    + " dia(s) depois da NF prevista: a próxima competência já precisa "
                    + "de reemissão"));
        }
        return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null, detalhe);
    }
}
