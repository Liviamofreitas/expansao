package br.com.engesoftware.sgdf.coleta;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;

/**
 * Varredura de uma pasta de contrato — história F1-01, cap. 8.1.
 *
 * <p>Ordem das etapas, e por que ela é essa:
 *
 * <ol>
 *   <li><b>Listar</b> com recursão limitada a partir de {@code pasta_origem/AAAA/MM},
 *       incluindo a raiz do mês, porque documento solto é comum na base real.</li>
 *   <li><b>Filtrar</b> pela política antes de baixar qualquer byte. Baixar para
 *       depois descartar gasta rede e amplia a janela de exposição a arquivo
 *       hostil.</li>
 *   <li><b>Delta por ETag</b> antes de baixar. É o que faz a varredura
 *       incremental de 30 em 30 minutos ser barata.</li>
 *   <li><b>Antivírus</b> antes de qualquer parsing. A validação V1 do cap. 8.4
 *       exige antivírus limpo, e o parser é justamente o alvo do arquivo
 *       malicioso.</li>
 *   <li><b>Hash</b> por último, sobre o conteúdo recebido.</li>
 * </ol>
 *
 * <p>A varredura é <b>somente leitura na origem</b> (cap. 8.1): não move, não
 * renomeia, não apaga. Não há caminho de código aqui capaz de escrever.
 */
public final class Varredura {

    /** Limites do cap. 8.1. Todos são parâmetro. */
    public record Limites(int profundidadeMaxima, int maximoDeArquivos, Duration duracaoMaxima) {
        public static Limites padrao() {
            return new Limites(3, 2_000, Duration.ofMinutes(30));
        }
    }

    private final ClienteWebDav webdav;
    private final Antivirus antivirus;
    private final PoliticaDeArquivos politica;
    private final Limites limites;

    public Varredura(ClienteWebDav webdav, Antivirus antivirus,
                     PoliticaDeArquivos politica, Limites limites) {
        this.webdav = webdav;
        this.antivirus = antivirus;
        this.politica = politica;
        this.limites = limites;
    }

    /**
     * Varre {@code raiz/AAAA/MM} de um contrato.
     *
     * @param raiz        {@code contrato_servico.pasta_origem}
     * @param competencia {@code AAAA-MM}
     * @param conhecido   versões já registradas, para o delta
     */
    public ResultadoVarredura varrer(String raiz, String competencia, EstadoConhecido conhecido) {
        String[] partes = competencia.split("-");
        if (partes.length != 2) {
            throw new IllegalArgumentException("competência deve ser AAAA-MM: " + competencia);
        }
        String inicio = CaminhoRemoto.juntar(raiz, partes[0], partes[1]);
        return varrerCaminho(raiz, inicio, conhecido);
    }

    ResultadoVarredura varrerCaminho(String raiz, String inicio, EstadoConhecido conhecido) {
        ResultadoVarredura resultado = new ResultadoVarredura();
        long prazo = System.nanoTime() + limites.duracaoMaxima().toNanos();

        Deque<Nivel> fila = new ArrayDeque<>();
        fila.add(new Nivel(CaminhoRemoto.canonicalizar(inicio + "/"), 0));

        while (!fila.isEmpty()) {
            if (System.nanoTime() > prazo) {
                truncar(resultado, "duração máxima de " + limites.duracaoMaxima() + " atingida");
                break;
            }
            if (resultado.visitados() >= limites.maximoDeArquivos()) {
                truncar(resultado, "limite de " + limites.maximoDeArquivos() + " arquivos atingido");
                break;
            }

            Nivel nivel = fila.poll();
            List<EntradaRemota> entradas;
            try {
                entradas = webdav.listar(nivel.caminho(), 1);
            } catch (IOException e) {
                resultado.falhas.add(new ResultadoVarredura.Falha(nivel.caminho(), e.getMessage()));
                continue;
            }

            for (EntradaRemota entrada : entradas) {
                // A própria coleção volta no PROPFIND de profundidade 1.
                if (entrada.caminho().equals(nivel.caminho())) {
                    continue;
                }
                // R-03: o href é do servidor. Fora da raiz do contrato, nada é lido.
                if (!CaminhoRemoto.dentroDe(raiz, entrada.caminho())) {
                    resultado.falhas.add(new ResultadoVarredura.Falha(
                            entrada.caminho(), "fora da raiz do contrato — entrada descartada"));
                    continue;
                }
                if (entrada.colecao()) {
                    if (nivel.profundidade() < limites.profundidadeMaxima()) {
                        fila.add(new Nivel(entrada.caminho(), nivel.profundidade() + 1));
                    }
                    continue;
                }
                processar(entrada, conhecido, resultado);
            }
        }
        return resultado;
    }

    private void processar(EntradaRemota entrada, EstadoConhecido conhecido,
                           ResultadoVarredura resultado) {
        PoliticaDeArquivos.Decisao decisao = politica.avaliar(entrada);

        // A CÓPIA DE CONFLITO É A ÚNICA EXCEÇÃO A "FILTRAR ANTES DE BAIXAR".
        //
        // Para tudo o mais — temporário, oculto, extensão errada, tamanho — o
        // nome basta e baixar seria desperdício. Para a cópia de conflito não
        // basta, e o cap. 8.1 diz isso na própria definição: o critério é
        // "padrão de nome + hash duplicado". Só o nome erra na direção
        // perigosa — "Relatório (conflicted copy 2026-06-30).pdf" pode ser a
        // ÚNICA versão que sobrou, se a sincronização substituiu o original por
        // uma cópia vazia ou antiga. Descartá-la pelo nome perderia o documento
        // em silêncio.
        //
        // Então ela é baixada, passa pelo antivírus como qualquer outra, é
        // hasheada — e mesmo assim NUNCA vira documento.
        boolean conflito = decisao.motivo() == PoliticaDeArquivos.Motivo.COPIA_DE_CONFLITO;
        if (!decisao.aceito() && !conflito) {
            resultado.ignorados.add(new ResultadoVarredura.Ignorado(
                    entrada.caminho(), decisao.motivo(), decisao.detalhe()));
            return;
        }

        // Delta: mesma versão que a última vez, nada a fazer.
        String versao = entrada.versao();
        String anterior = conhecido.versaoDe(entrada.caminho());
        if (versao != null && versao.equals(anterior)) {
            resultado.inalterados.add(entrada.caminho());
            return;
        }

        byte[] conteudo;
        try {
            conteudo = webdav.baixar(entrada.caminho(), politica.tamanhoMaximo());
        } catch (IOException e) {
            resultado.falhas.add(new ResultadoVarredura.Falha(entrada.caminho(), e.getMessage()));
            return;
        }

        VeredictoAntivirus veredicto = antivirus.verificar(entrada.caminho(), conteudo);
        if (veredicto.situacao() == VeredictoAntivirus.Situacao.INFECTADO) {
            resultado.infectados.add(new ResultadoVarredura.Rejeitado(
                    entrada.caminho(), veredicto.assinatura(), null));
            return;
        }
        if (veredicto.situacao() == VeredictoAntivirus.Situacao.INDISPONIVEL) {
            // Antivírus fora do ar não aprova arquivo: vira falha, e o arquivo
            // volta na próxima varredura porque nada foi registrado como visto.
            resultado.falhas.add(new ResultadoVarredura.Falha(
                    entrada.caminho(), "antivírus indisponível: " + veredicto.detalhe()));
            return;
        }

        if (conflito) {
            // O conteúdo é descartado aqui de propósito: nada além do hash
            // precisa dele, e carregar os bytes adiante criaria o caminho pelo
            // qual alguém acabaria publicando a cópia.
            resultado.conflitos.add(new ResultadoVarredura.Conflito(
                    entrada.caminho(), versao, sha256(conteudo), conteudo.length));
            return;
        }

        resultado.coletados.add(new ArquivoColetado(
                entrada.caminho(), versao, sha256(conteudo), conteudo.length, conteudo));
    }

    private static void truncar(ResultadoVarredura resultado, String motivo) {
        resultado.truncada = true;
        resultado.motivoTruncamento = motivo;
    }

    static String sha256(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private record Nivel(String caminho, int profundidade) {}
}
