package br.com.engesoftware.sgdf.documento;

import java.math.BigDecimal;

/**
 * Uma linha de relação de benefício — quem recebeu e quanto.
 *
 * @param matricula matrícula na folha, sem zeros à esquerda; a chave de junção
 * @param cpf       CPF, quando a relação o traz; nulo quando não
 * @param nome      nome como impresso
 * @param valor     valor atribuído ao beneficiário
 */
public record BeneficiarioDaRelacao(String matricula, String cpf, String nome, BigDecimal valor) {
}
