package br.com.engesoftware.sgdf.extracao;

/**
 * Porta de extração de texto com posições — cap. 8.2.
 *
 * <p>Existe para que o OCR (contingência do mesmo capítulo) e a leitura de
 * planilhas entrem como outras implementações, sem que o localizador de campos
 * ou o motor de classificação saibam de onde o texto veio.
 */
public interface ExtratorDeTexto {

    TextoExtraido extrair(byte[] conteudo);
}
