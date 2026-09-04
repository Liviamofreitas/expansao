package br.com.engesoftware.sgdf.coleta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Cliente clamd pelo comando INSTREAM.
 *
 * <p>INSTREAM envia o conteúdo em blocos precedidos do tamanho em 4 bytes
 * big-endian, terminados por um bloco de tamanho zero. A resposta é
 * {@code stream: OK} ou {@code stream: <assinatura> FOUND}.
 *
 * <p>O conteúdo nunca toca o disco desta máquina: o worker recebe bytes do
 * WebDAV e os repassa ao clamd. Gravar em disco para escanear criaria uma
 * janela em que arquivo hostil (R-03) existe no sistema de arquivos do worker.
 */
public final class AntivirusClamd implements Antivirus {

    private static final int BLOCO = 32 * 1024;

    private final String host;
    private final int porta;
    private final int timeoutMs;

    public AntivirusClamd(String host, int porta, int timeoutMs) {
        this.host = host;
        this.porta = porta;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public VeredictoAntivirus verificar(String rotulo, byte[] conteudo) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, porta), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            try (OutputStream saida = socket.getOutputStream();
                 InputStream entrada = socket.getInputStream()) {

                saida.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                for (int i = 0; i < conteudo.length; i += BLOCO) {
                    int tamanho = Math.min(BLOCO, conteudo.length - i);
                    saida.write(ByteBuffer.allocate(4).putInt(tamanho).array());
                    saida.write(conteudo, i, tamanho);
                }
                saida.write(new byte[] {0, 0, 0, 0});   // fim do fluxo
                saida.flush();

                String resposta = new String(entrada.readAllBytes(), StandardCharsets.UTF_8)
                        .replace("\0", "").trim();
                return interpretar(rotulo, resposta);
            }
        } catch (IOException e) {
            // Não é "limpo": ver VeredictoAntivirus.
            return VeredictoAntivirus.indisponivel(
                    "clamd em " + host + ":" + porta + " — " + e.getMessage());
        }
    }

    static VeredictoAntivirus interpretar(String rotulo, String resposta) {
        if (resposta.endsWith("OK")) {
            return VeredictoAntivirus.limpo();
        }
        if (resposta.endsWith("FOUND")) {
            // "stream: Eicar-Signature FOUND"
            int inicio = resposta.indexOf(':');
            String assinatura = resposta.substring(inicio + 1, resposta.length() - "FOUND".length()).trim();
            return VeredictoAntivirus.infectado(assinatura.isEmpty() ? "DESCONHECIDA" : assinatura);
        }
        // ERROR, resposta truncada ou protocolo inesperado: indisponível, não limpo.
        return VeredictoAntivirus.indisponivel("resposta inesperada para " + rotulo + ": " + resposta);
    }
}
