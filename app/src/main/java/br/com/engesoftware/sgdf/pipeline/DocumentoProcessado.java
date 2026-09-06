package br.com.engesoftware.sgdf.pipeline;

import br.com.engesoftware.sgdf.classificacao.Classificacao;
import br.com.engesoftware.sgdf.classificacao.Decisao;
import br.com.engesoftware.sgdf.extracao.DetectorDeMime;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import java.util.List;
import java.util.Optional;

/**
 * O que o {@link Pipeline} sabe sobre um arquivo depois de processá-lo.
 *
 * <p><b>Um arquivo barrado não tem texto nem classificação, e o tipo diz
 * isso.</b> {@code texto} e {@code classificacao} são nulos quando o documento
 * não chegou lá — e os acessores devolvem {@link Optional}, para que nenhum
 * chamador leia "sem classificação" como "classificação vazia". A diferença
 * importa: um arquivo infectado não é um arquivo não reconhecido; ele nem foi
 * aberto.
 */
public record DocumentoProcessado(String nomeArquivo, String hashSha256, long tamanho,
                                  DetectorDeMime.Tipo mime, TextoExtraido texto,
                                  Classificacao classificacao,
                                  List<ResultadoDeValidacao> vereditos) {

    public DocumentoProcessado {
        vereditos = List.copyOf(vereditos);
    }

    /** Reprovado antes de ser aberto, ou ilegível a ponto de não se extrair nada. */
    static DocumentoProcessado barrado(String nomeArquivo, String hash, long tamanho,
                                       DetectorDeMime.Tipo mime,
                                       List<ResultadoDeValidacao> vereditos) {
        return new DocumentoProcessado(nomeArquivo, hash, tamanho, mime, null, null, vereditos);
    }

    public boolean barrado() {
        return classificacao == null;
    }

    public Optional<TextoExtraido> textoExtraido() {
        return Optional.ofNullable(texto);
    }

    public Optional<Classificacao> tipoReconhecido() {
        return Optional.ofNullable(classificacao);
    }

    /**
     * O tipo, ou nulo quando não há um.
     *
     * <p>Nulo e não {@code "DESCONHECIDO"}: uma string sentinela acabaria
     * comparada com um código de tipo documental em alguma consulta, e o dia em
     * que alguém cadastrar um tipo com esse código o sistema passa a tratar
     * arquivos não reconhecidos como se fossem dele.
     */
    public String tipo() {
        return classificacao == null || classificacao.melhor().isEmpty()
                ? null : classificacao.tipo();
    }

    public Decisao decisao() {
        return classificacao == null ? null : classificacao.decisao();
    }

    /** Nenhuma unitária reprovou. NAO_APLICAVEL não reprova (cap. 1, princípio 1). */
    public boolean aprovadoNasUnitarias() {
        return vereditos.stream().noneMatch(ResultadoDeValidacao::reprovado);
    }

    public List<ResultadoDeValidacao> reprovacoes() {
        return vereditos.stream().filter(ResultadoDeValidacao::reprovado).toList();
    }
}
