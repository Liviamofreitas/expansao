package br.com.engesoftware.sgdf.validacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V8 — completude de campos essenciais.
 *
 * <p><b>Por que existe.</b> Nenhuma das sete validações do cap. 8.4 pergunta se
 * o documento tem CONTEÚDO. V1 pergunta se é seguro, V2 se é legível, V3 de
 * quem é, V4 de quando é, V5 até quando vale, V6 se os formatos estão
 * completos, V7 se é inédito. Um documento vazio responde bem a todas.
 *
 * <p>O achado A12 encontrou dois: comprovantes do Itaú (SISPAG SALÁRIOS) com
 * 603 caracteres de texto nativo que são apenas os rótulos — "Nome da empresa:",
 * "Agência:", "Conta corrente:", "Valor:" — e nenhum valor. Passariam por todo
 * o portão e entrariam no book como prova de pagamento.
 *
 * <p><b>O que V8 não faz.</b> Não confere se o valor está CERTO — isso é
 * conciliação, e depende de outro documento. V8 confere se o valor EXISTE e tem
 * a forma de um valor. É a diferença entre "este comprovante não bate com a
 * guia" e "este arquivo não é um comprovante".
 */
public final class ValidacaoDeCompletude {

    public static final String CODIGO = "V8";

    /**
     * @param essenciais o que a regra de reconhecimento declara indispensável
     * @param extraidos  o que a extração encontrou, por nome de campo
     */
    public ResultadoDeValidacao validar(List<CampoEssencial> essenciais,
                                        Map<String, String> extraidos) {
        if (essenciais.isEmpty()) {
            // Não reprova: o cap. 1, princípio 1, proíbe travar faturamento por
            // falta de configuração própria. Mas fica registrado, porque um tipo
            // sem campos essenciais declarados é um tipo que V8 não protege.
            return new ResultadoDeValidacao(CODIGO, Veredito.NAO_APLICAVEL,
                    "a regra de reconhecimento não declara campos essenciais para este tipo: "
                            + "V8 não confere completude aqui",
                    Map.of());
        }

        List<String> ausentes = new ArrayList<>();
        List<String> malFormados = new ArrayList<>();
        List<String> motivos = new ArrayList<>();

        for (CampoEssencial e : essenciais) {
            String valor = extraidos.get(e.campo());
            if (valor == null || valor.isBlank()) {
                ausentes.add(e.campo());
                motivos.add(e.campo() + " não foi encontrado — " + e.motivo());
            } else if (!e.formato().aceita(valor)) {
                malFormados.add(e.campo() + "=" + valor);
                motivos.add(e.campo() + " veio como \"" + valor.trim()
                        + "\", que não é " + e.formato().descricao() + " — " + e.motivo());
            }
        }

        if (ausentes.isEmpty() && malFormados.isEmpty()) {
            return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null, Map.of());
        }

        Map<String, List<String>> detalhe = new LinkedHashMap<>();
        if (!ausentes.isEmpty()) {
            detalhe.put("ausentes", List.copyOf(ausentes));
        }
        if (!malFormados.isEmpty()) {
            detalhe.put("mal_formados", List.copyOf(malFormados));
        }
        return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                resumo(ausentes.size() + malFormados.size(), essenciais.size(), motivos), detalhe);
    }

    /**
     * A mensagem que o usuário lê.
     *
     * <p>Cap. 12: acionável. Diz quantos campos faltaram, de quantos, e depois
     * cada um pelo nome com o porquê — quem recebe precisa saber o que reenviar,
     * não que "o documento foi reprovado".
     */
    private static String resumo(int falhas, int total, List<String> motivos) {
        return "documento incompleto: " + falhas + " de " + total
                + " campo(s) essencial(is) não pôde(puderam) ser lido(s). "
                + String.join("; ", motivos) + ".";
    }
}
