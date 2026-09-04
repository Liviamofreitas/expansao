package br.com.engesoftware.sgdf.extracao;

import java.util.Locale;

/**
 * Tipo real do arquivo pela assinatura binária — cap. 8.2.
 *
 * <p>"mime_real por assinatura binária; divergência extensão × conteúdo gera
 * rejeição com motivo". A extensão é escolhida por quem depositou o arquivo e é
 * entrada não confiável (R-03): um executável renomeado para .pdf passaria por
 * qualquer verificação baseada em nome.
 */
public final class DetectorDeMime {

    public enum Tipo {
        PDF("application/pdf", "pdf"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        XLS("application/vnd.ms-excel", "xls"),
        ZIP("application/zip", "zip"),
        TEXTO("text/plain", "csv", "txt"),
        DESCONHECIDO("application/octet-stream");

        public final String mime;
        private final String[] extensoes;

        Tipo(String mime, String... extensoes) {
            this.mime = mime;
            this.extensoes = extensoes;
        }

        boolean aceitaExtensao(String extensao) {
            for (String e : extensoes) {
                if (e.equals(extensao)) {
                    return true;
                }
            }
            return false;
        }
    }

    private DetectorDeMime() {}

    public static Tipo detectar(byte[] conteudo) {
        if (comeca(conteudo, "%PDF-")) {
            return Tipo.PDF;
        }
        if (conteudo.length >= 4 && conteudo[0] == 0x50 && conteudo[1] == 0x4B
                && (conteudo[2] == 3 || conteudo[2] == 5 || conteudo[2] == 7)) {
            // Um xlsx é um zip; o que o distingue é o conteúdo interno. Procurar
            // a marca em vez de descompactar evita abrir arquivo hostil (zip
            // bomb) só para classificá-lo.
            boolean planilha = contem(conteudo, "xl/workbook.xml")
                    || (contem(conteudo, "[Content_Types].xml") && contem(conteudo, "xl/"));
            return planilha ? Tipo.XLSX : Tipo.ZIP;
        }
        // OLE2 Compound File: .xls, .doc antigos
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                       (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        if (comecaCom(conteudo, ole2)) {
            return Tipo.XLS;
        }
        return pareceTexto(conteudo) ? Tipo.TEXTO : Tipo.DESCONHECIDO;
    }

    /**
     * Confere o tipo real contra a extensão declarada.
     *
     * @throws ExtracaoInvalida quando divergem
     */
    public static Tipo conferir(byte[] conteudo, String nomeArquivo) {
        Tipo tipo = detectar(conteudo);
        String extensao = extensao(nomeArquivo);
        if (tipo == Tipo.DESCONHECIDO) {
            throw new ExtracaoInvalida("MIME_DESCONHECIDO",
                    "assinatura binária não reconhecida em " + nomeArquivo);
        }
        if (!tipo.aceitaExtensao(extensao)) {
            throw new ExtracaoInvalida("MIME_DIVERGENTE",
                    nomeArquivo + " declara ." + extensao + " mas o conteúdo é " + tipo.mime);
        }
        return tipo;
    }

    static String extensao(String nomeArquivo) {
        int ponto = nomeArquivo.lastIndexOf('.');
        return ponto < 0 ? "" : nomeArquivo.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean comeca(byte[] dados, String marca) {
        return comecaCom(dados, marca.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static boolean comecaCom(byte[] dados, byte[] marca) {
        if (dados.length < marca.length) {
            return false;
        }
        for (int i = 0; i < marca.length; i++) {
            if (dados[i] != marca[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contem(byte[] dados, String marca) {
        byte[] alvo = marca.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int limite = Math.min(dados.length, 64 * 1024);   // só o começo do zip
        for (int i = 0; i + alvo.length <= limite; i++) {
            int j = 0;
            while (j < alvo.length && dados[i + j] == alvo[j]) {
                j++;
            }
            if (j == alvo.length) {
                return true;
            }
        }
        return false;
    }

    /** Heurística para CSV e TXT, que não têm assinatura. */
    private static boolean pareceTexto(byte[] dados) {
        if (dados.length == 0) {
            return false;
        }
        int amostra = Math.min(dados.length, 8192);
        int controle = 0;
        for (int i = 0; i < amostra; i++) {
            int b = dados[i] & 0xFF;
            if (b == 0) {
                return false;   // NUL não aparece em texto
            }
            if (b < 0x09 || (b > 0x0D && b < 0x20)) {
                controle++;
            }
        }
        return controle * 100 / amostra < 1;
    }
}
