package br.com.engesoftware.sgdf.persistencia;

import java.time.LocalDate;

/**
 * Um documento como a tabela {@code documento} o guarda.
 *
 * <p>Deliberadamente separado das classes de domínio: {@code TextoExtraido} e
 * companhia não conhecem banco, e é o que os torna testáveis sem
 * infraestrutura (ADR-002).
 */
public record Documento(String origem, String caminho, String nomeArquivo, String hashSha256,
                        long tamanho, String mimeReal, String formato, String statusTriagem,
                        boolean ocr, String competenciaExtraida, LocalDate validadeExtraida,
                        String cnpjExtraido, String natureza, String versaoOrigem,
                        String criadoPor) {

    public Documento {
        if (hashSha256 == null || !hashSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "documento sem SHA-256 hexadecimal: o hash é a identidade do conteúdo "
                            + "e o que V7 usa para não contar duas vezes o mesmo arquivo");
        }
    }

    /** O mínimo que a varredura conhece antes de classificar e validar. */
    public static Documento recemColetado(String caminho, String nomeArquivo, String hash,
                                          long tamanho, String mimeReal, String formato,
                                          String etag, String ator) {
        return new Documento("OWNCLOUD", caminho, nomeArquivo, hash, tamanho, mimeReal,
                formato, "PENDENTE", false, null, null, null, null, etag, ator);
    }
}
