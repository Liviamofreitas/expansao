package br.com.engesoftware.sgdf.triagem;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O padrão que a confirmação em triagem aprende do nome do arquivo — cap. 8.3.
 *
 * <p><b>O que precisa sobreviver e o que precisa sumir.</b> O alias é gravado
 * uma vez e vale para sempre; o nome do arquivo muda todo mês. Guardar
 * {@code 1041601__CONTRACHEQUE.pdf} inteiro não ensina nada, porque o arquivo do
 * mês seguinte é {@code 1041602__CONTRACHEQUE.pdf} e o padrão não voltaria a
 * casar — que é exatamente o critério de aceite da F1-06: <i>"o mesmo padrão não
 * retorna"</i>. O que sobrevive é a parte estável: {@code contracheque}.
 *
 * <p><b>Por que TODO dígito cai.</b> Matrícula, competência, CNPJ e CPF são
 * dígitos, e todos variam. Mas há uma razão mais forte que a variação: a massa
 * real trouxe {@code 052.190.471-40 folha.pdf} — nome de arquivo digitado por
 * gente, com CPF dentro. Um alias é um dado de configuração, consultado em toda
 * classificação e listado em toda tela de cadastro; ninguém procuraria dado
 * pessoal ali. Se o padrão preservasse dígitos, {@code tipo_alias} viraria um
 * repositório silencioso de CPF, fora de qualquer inventário do cap. 17.
 *
 * <p>Dígitos soltos dentro de palavras também caem, senão
 * {@code folha05219047140.pdf} passaria inteiro.
 */
public final class PadraoDeNome {

    /**
     * Abaixo disto o padrão não discrimina nada.
     *
     * <p>Um alias de duas ou três letras casaria com quase tudo, e o bônus de
     * nome seria concedido por acidente. Cinco caracteres é o menor tamanho dos
     * códigos reais da massa ({@code fopag}) — descer abaixo disso seria
     * escolher aprender ruído.
     */
    static final int MINIMO_SIGNIFICATIVO = 5;

    private final String original;
    private final String normalizado;
    private final String recusa;

    private PadraoDeNome(String original, String normalizado, String recusa) {
        this.original = original;
        this.normalizado = normalizado;
        this.recusa = recusa;
    }

    /** Extrai o padrão estável do nome do arquivo. */
    public static PadraoDeNome de(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return new PadraoDeNome(nomeArquivo, null, "nome de arquivo vazio");
        }
        String semExtensao = semExtensao(nomeArquivo);
        String semAcento = Normalizer.normalize(semExtensao, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String soLetras = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", "_");

        List<String> tokens = new ArrayList<>();
        for (String token : soLetras.split("_")) {
            // Sobra de um dígito removido, ou o "a" e o "o" que a origem deixou
            // em RELAC_A_O. Não discriminam nada sozinhos.
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        String padrao = String.join("_", tokens);
        if (padrao.replace("_", "").length() < MINIMO_SIGNIFICATIVO) {
            return new PadraoDeNome(nomeArquivo, null, "o que sobra do nome do arquivo depois "
                    + "de tirar números e separadores — \"" + padrao + "\" — é curto demais "
                    + "para discriminar tipo algum");
        }
        return new PadraoDeNome(nomeArquivo, padrao, null);
    }

    /** O nome como veio, para {@code tipo_alias.texto_original}. */
    public String original() {
        return original;
    }

    /** O padrão aprendido, para {@code tipo_alias.texto_normalizado}. Nulo se recusado. */
    public String normalizado() {
        return normalizado;
    }

    public boolean aprendivel() {
        return normalizado != null;
    }

    /**
     * Por que não dá para aprender com este nome.
     *
     * <p>Vai para a resposta da confirmação, não para um log silencioso: o
     * cap. 12 manda que confirmar <i>"registra alias e informa"</i>, e "não
     * registrei, porque" é a informação mais útil das duas — sem ela, quem
     * confirma acha que ensinou o sistema e volta a ver o mesmo arquivo na fila
     * no mês seguinte.
     */
    public String recusa() {
        return recusa;
    }

    /** Tira só a última extensão, e só se ela parecer extensão. */
    private static String semExtensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        if (ponto <= 0 || ponto == nome.length() - 1) {
            return nome;
        }
        String cauda = nome.substring(ponto + 1);
        return cauda.length() <= 5 && cauda.chars().allMatch(Character::isLetterOrDigit)
                ? nome.substring(0, ponto) : nome;
    }
}
