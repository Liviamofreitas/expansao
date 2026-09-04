package br.com.engesoftware.sgdf.coleta;

/** Porta de verificação antivírus. Cap. 8.1: ICAP ou clamd, antes da extração. */
public interface Antivirus {

    VeredictoAntivirus verificar(String rotulo, byte[] conteudo);
}
