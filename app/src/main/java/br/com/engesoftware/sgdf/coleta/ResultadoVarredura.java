package br.com.engesoftware.sgdf.coleta;

import java.util.ArrayList;
import java.util.List;

/**
 * O que uma varredura produziu.
 *
 * <p>Todas as listas são visíveis de propósito. A tela de arquivos desconhecidos
 * (história F1-10) e o alerta de organização do cap. 8.1 dependem de os
 * descartes chegarem até a interface em vez de morrerem no log do worker.
 */
public final class ResultadoVarredura {

    public record Ignorado(String caminho, PoliticaDeArquivos.Motivo motivo, String detalhe) {}

    public record Rejeitado(String caminho, String assinatura, String detalhe) {}

    public record Falha(String caminho, String erro) {}

    public final List<ArquivoColetado> coletados = new ArrayList<>();
    public final List<String> inalterados = new ArrayList<>();
    public final List<Ignorado> ignorados = new ArrayList<>();
    public final List<Rejeitado> infectados = new ArrayList<>();
    public final List<Falha> falhas = new ArrayList<>();
    public boolean truncada;
    public String motivoTruncamento;

    public int visitados() {
        return coletados.size() + inalterados.size() + ignorados.size()
                + infectados.size() + falhas.size();
    }

    @Override
    public String toString() {
        return "coletados=%d inalterados=%d ignorados=%d infectados=%d falhas=%d truncada=%s"
                .formatted(coletados.size(), inalterados.size(), ignorados.size(),
                        infectados.size(), falhas.size(), truncada);
    }
}
