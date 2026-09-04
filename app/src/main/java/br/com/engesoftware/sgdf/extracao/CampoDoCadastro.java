package br.com.engesoftware.sgdf.extracao;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Um campo de extração como o cadastro o declara — corresponde a uma entrada de
 * {@code regra_reconhecimento.campos}.
 *
 * <p>Existe porque o cadastro deixou de saber expressar apenas padrão de texto.
 * O achado A20 mostrou que metade dos campos rotulados dos documentos reais não
 * se extrai por regex de vizinhança: em layout tabular os rótulos vêm todos
 * primeiro e os valores todos depois, e nenhuma janela alcança o valor sem
 * atravessar os outros rótulos.
 *
 * <p>Dois modos, e a escolha é do cadastro, não do código:
 *
 * <dl>
 *   <dt>{@link Modo#TEXTO}</dt>
 *   <dd>Regex sobre o texto normalizado. Serve quando o rótulo é seguido do
 *       valor na mesma linha física: {@code cnpj: 00.681.946/0001-60}.</dd>
 *   <dt>{@link Modo#ABAIXO_DO_ROTULO}</dt>
 *   <dd>Coordenada: o valor é o que fica sob o rótulo, na linha de valores do
 *       cabeçalho. Serve para tudo o que é tabela.</dd>
 * </dl>
 *
 * @param nome         nome do campo
 * @param modo         como localizar
 * @param padrao       TEXTO: onde o valor está. ABAIXO_DO_ROTULO: o que recortar
 *                     da célula, ou nulo para a célula inteira
 * @param grupo        grupo de captura do padrão, no modo TEXTO
 * @param rotulos      ABAIXO_DO_ROTULO: todos os rótulos da linha de cabeçalho,
 *                     da esquerda para a direita
 * @param rotulo       ABAIXO_DO_ROTULO: qual deles se quer
 * @param alinhamentos ABAIXO_DO_ROTULO: alinhamento de cada coluna. Coluna
 *                     numérica é DIREITA, e declarar errado devolve o valor da
 *                     coluna vizinha
 */
public record CampoDoCadastro(String nome, Modo modo, Pattern padrao, int grupo,
                              List<String> rotulos, String rotulo,
                              List<LeitorDeTabela.Alinhamento> alinhamentos) {

    public enum Modo { TEXTO, ABAIXO_DO_ROTULO }

    public CampoDoCadastro {
        if (modo == Modo.ABAIXO_DO_ROTULO) {
            if (rotulos == null || rotulos.isEmpty() || rotulo == null) {
                throw new IllegalArgumentException(
                        "campo '" + nome + "' no modo ABAIXO_DO_ROTULO precisa dos rótulos");
            }
            if (!rotulos.contains(rotulo)) {
                throw new IllegalArgumentException("campo '" + nome + "': o rótulo '" + rotulo
                        + "' não está entre os declarados " + rotulos);
            }
            if (alinhamentos != null && alinhamentos.size() != rotulos.size()) {
                throw new IllegalArgumentException("campo '" + nome
                        + "': um alinhamento por rótulo, ou nenhum");
            }
        } else if (padrao == null) {
            throw new IllegalArgumentException("campo '" + nome + "' no modo TEXTO sem padrão");
        }
        rotulos = rotulos == null ? List.of() : List.copyOf(rotulos);
        alinhamentos = alinhamentos == null
                ? java.util.Collections.nCopies(rotulos.size(), LeitorDeTabela.Alinhamento.ESQUERDA)
                : List.copyOf(alinhamentos);
    }

    public static CampoDoCadastro texto(String nome, String padrao, int grupo) {
        return new CampoDoCadastro(nome, Modo.TEXTO, Pattern.compile(padrao), grupo,
                null, null, null);
    }

    public static CampoDoCadastro abaixoDoRotulo(String nome, List<String> rotulos, String rotulo,
                                                 List<LeitorDeTabela.Alinhamento> alinhamentos,
                                                 String padraoDoValor) {
        return new CampoDoCadastro(nome, Modo.ABAIXO_DO_ROTULO,
                padraoDoValor == null ? null : Pattern.compile(padraoDoValor), 0,
                rotulos, rotulo, alinhamentos);
    }

    /** Extrai o campo do documento, pelo modo declarado. */
    public CampoExtraido extrairDe(TextoExtraido texto) {
        if (modo == Modo.TEXTO) {
            return LocalizadorDeCampos.primeiro(texto,
                    new PadraoDeCampo(nome, padrao, grupo), 1.0);
        }
        return ExtratorPorRotulo.valorAbaixoDe(texto, rotulos, rotulo, nome, alinhamentos, padrao);
    }
}
