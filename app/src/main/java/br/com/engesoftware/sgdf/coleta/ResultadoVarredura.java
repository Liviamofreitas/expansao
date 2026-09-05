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

    /**
     * Uma cópia de conflito de sincronização, já com o hash — história F1-10.
     *
     * <p>Separada de {@code coletados} e de {@code ignorados} porque não é nem
     * uma coisa nem outra: <b>nunca</b> vira documento (cap. 8.1: "nunca
     * publicadas"), e também não pode sumir — o capítulo manda sinalizá-la como
     * alerta de organização.
     *
     * <p>Traz o hash porque o critério do cap. 8.1 é "padrão de nome <b>+</b>
     * hash duplicado", e as duas metades decidem coisas diferentes: com hash
     * conhecido é cópia redundante; com hash inédito pode ser a única versão que
     * sobrou.
     */
    public record Conflito(String caminho, String versao, String hashSha256, long tamanho) {}

    public final List<ArquivoColetado> coletados = new ArrayList<>();
    public final List<String> inalterados = new ArrayList<>();
    public final List<Ignorado> ignorados = new ArrayList<>();
    public final List<Rejeitado> infectados = new ArrayList<>();
    public final List<Falha> falhas = new ArrayList<>();
    public final List<Conflito> conflitos = new ArrayList<>();
    public boolean truncada;
    public String motivoTruncamento;

    public int visitados() {
        return coletados.size() + inalterados.size() + ignorados.size()
                + infectados.size() + falhas.size() + conflitos.size();
    }

    @Override
    public String toString() {
        return ("coletados=%d inalterados=%d ignorados=%d conflitos=%d infectados=%d "
                + "falhas=%d truncada=%s")
                .formatted(coletados.size(), inalterados.size(), ignorados.size(),
                        conflitos.size(), infectados.size(), falhas.size(), truncada);
    }
}
