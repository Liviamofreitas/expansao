package br.com.engesoftware.sgdf.validacao;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V7 — o hash é inédito para a exigência.
 *
 * <p>Cap. 8.4: evita contar duas vezes o mesmo arquivo renomeado; a falha é
 * <b>ignorada com registro</b>. É a única validação cuja falha não muda o estado
 * de nada: o documento simplesmente não é vinculado outra vez.
 *
 * <p>Isso não é detalhe. A varredura é recursiva a partir da raiz do mês e
 * inclui subpastas (cap. 8.1); o mesmo PDF aparece solto na raiz e dentro da
 * pasta do cliente, com nomes diferentes. Contá-lo duas vezes faria uma
 * exigência de dois formatos parecer satisfeita por um arquivo só.
 *
 * <p>É a mesma armadilha do achado A17 na folha, e do pareamento de
 * comprovantes: <b>contar duas vezes o mesmo documento produz um resultado
 * bonito e falso</b>.
 */
public final class ValidacaoDeUnicidade {

    public static final String CODIGO = "V7";

    /**
     * @param hash             SHA-256 do arquivo, em hexadecimal
     * @param hashesJaVinculados os que a exigência já tem
     */
    public ResultadoDeValidacao validar(String hash, Set<String> hashesJaVinculados) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o hash do documento não foi calculado ou não é um SHA-256 hexadecimal: "
                            + "sem ele não há como saber se o arquivo é inédito",
                    Map.of());
        }
        if (hashesJaVinculados.contains(hash)) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "este arquivo já está vinculado à exigência com outro nome: "
                            + "o vínculo é ignorado e o fato registrado (cap. 8.4)",
                    Map.of("hash", List.of(hash)));
        }
        return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null,
                Map.of("hash", List.of(hash)));
    }
}
