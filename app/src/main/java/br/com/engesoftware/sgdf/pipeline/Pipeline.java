package br.com.engesoftware.sgdf.pipeline;

import br.com.engesoftware.sgdf.classificacao.Bonus;
import br.com.engesoftware.sgdf.classificacao.Classificacao;
import br.com.engesoftware.sgdf.classificacao.Classificador;
import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import br.com.engesoftware.sgdf.extracao.DetectorDeMime;
import br.com.engesoftware.sgdf.extracao.ExtracaoInvalida;
import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeLegibilidade;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeSeguranca;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeUnicidade;
import br.com.engesoftware.sgdf.validacao.Veredito;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encadeia extração → classificação → validações que não precisam de ciclo.
 *
 * <p><b>Por que só agora, e por que só até aqui.</b> Cada etapa existia e era
 * verificada isoladamente; nada as ligava. Ligá-las tem um retorno próprio e
 * imediato: permite rodar o sistema sobre os documentos reais e <b>medir</b> a
 * precisão de classificação que o cap. 19 exige (≥ 95% no bloco corporativo
 * antes da 1b) — que é metade da F3-01 e não depende de conferência manual
 * nova.
 *
 * <p><b>O que este pipeline deliberadamente NÃO faz.</b> Ele para antes de
 * tudo que precisa de um ciclo:
 *
 * <ul>
 *   <li><b>Não vincula a exigência.</b> Vincular é decisão sobre um ciclo, e
 *       depende da matriz materializada. O que sai daqui é <i>o que o documento
 *       é</i>, não <i>a que obrigação ele responde</i>.</li>
 *   <li><b>Não valida competência, vigência nem titularidade.</b> V4, V5 e V3
 *       comparam o documento com o ciclo (competência exigida, data prevista da
 *       NF, CNPJ da empresa emitente). Sem ciclo não há com o que comparar, e
 *       rodá-las contra um valor inventado produziria vereditos falsos —
 *       exatamente o defeito que o cap. 1, princípio 1, proíbe.</li>
 *   <li><b>Não grava.</b> Persistir é de quem chama, que
 *       recebe o resultado pronto. Separado porque a medição de precisão precisa
 *       rodar sem banco.</li>
 * </ul>
 *
 * <p>As três validações que ficam — V1 (segurança), V2 (legibilidade) e V7
 * (unicidade) — são as que se decidem <b>só com o arquivo</b>. Não é uma lista
 * arbitrária: é exatamente o subconjunto que não olha para fora do documento.
 */
public final class Pipeline {

    private final ExtratorPdfBox extrator;
    private final Classificador classificador;
    private final ValidacaoDeSeguranca seguranca;
    private final ValidacaoDeLegibilidade legibilidade;
    private final ValidacaoDeUnicidade unicidade;

    public Pipeline(ExtratorPdfBox extrator, Classificador classificador,
                    ValidacaoDeSeguranca seguranca) {
        this.extrator = extrator;
        this.classificador = classificador;
        this.seguranca = seguranca;
        this.legibilidade = new ValidacaoDeLegibilidade();
        this.unicidade = new ValidacaoDeUnicidade();
    }

    /**
     * Processa um arquivo.
     *
     * @param hashesJaVinculados hashes já ligados à mesma exigência (V7); vazio
     *                           quando o pipeline roda fora de um ciclo
     * @param bonus              alias aprendido na triagem (F1-06); pode ser nulo
     */
    public DocumentoProcessado processar(String nomeArquivo, byte[] conteudo,
                                         VeredictoAntivirus antivirus,
                                         Set<String> hashesJaVinculados, Bonus bonus) {
        String hash = sha256(conteudo);
        DetectorDeMime.Tipo mime = DetectorDeMime.detectar(conteudo);
        List<ResultadoDeValidacao> vereditos = new ArrayList<>();

        // V1 PRIMEIRO, E ANTES DE ABRIR O ARQUIVO.
        //
        // A ordem é a única defesa que existe aqui: extrair texto de um arquivo
        // que o antivírus acusou é exatamente o que não se deve fazer, e
        // "validar depois de processar" seria validar depois do dano. Um
        // arquivo reprovado em V1 sai daqui sem nunca ter passado pelo PDFBox.
        ResultadoDeValidacao v1 = seguranca.validar(nomeArquivo, mime.mime, conteudo.length,
                antivirus);
        vereditos.add(v1);
        if (v1.reprovado()) {
            return DocumentoProcessado.barrado(nomeArquivo, hash, conteudo.length, mime,
                    vereditos);
        }

        TextoExtraido texto;
        try {
            texto = extrator.extrair(conteudo);
        } catch (ExtracaoInvalida e) {
            // A EXTRAÇÃO QUE FALHA É UM RESULTADO, NÃO UMA EXCEÇÃO QUE SOBE.
            //
            // Deixá-la subir faria uma varredura de 300 arquivos morrer no
            // primeiro PDF corrompido, e o operador veria um erro de sistema em
            // vez de um documento ilegível. V2 é o veredito certo: o documento
            // existe, não se consegue ler, e alguém precisa reenviá-lo.
            vereditos.add(new ResultadoDeValidacao("V2", Veredito.REPROVADO,
                    "não foi possível extrair texto: " + e.getMessage(),
                    Map.of("erro", List.of(e.getClass().getSimpleName()))));
            return DocumentoProcessado.barrado(nomeArquivo, hash, conteudo.length, mime,
                    vereditos);
        }

        vereditos.add(legibilidade.validar(texto, false));
        vereditos.add(unicidade.validar(hash, hashesJaVinculados));

        // CLASSIFICA MESMO COM V2 OU V7 REPROVADA, E É DELIBERADO.
        //
        // Um documento ilegível ou duplicado continua sendo de algum tipo, e
        // saber qual é o que permite dizer "o contracheque de fulano chegou
        // ilegível" em vez de "um arquivo chegou ilegível". A reprovação impede
        // que ele satisfaça a exigência; não impede que ele seja identificado.
        Classificacao classificacao = bonus == null
                ? classificador.classificar(texto)
                : classificador.classificar(texto, bonus);

        return new DocumentoProcessado(nomeArquivo, hash, conteudo.length, mime, texto,
                classificacao, List.copyOf(vereditos));
    }

    /** Cap. 8.1: o hash decide se é conteúdo novo, e é a identidade do documento. */
    public static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /** O que o pipeline faz com cada decisão de classificação (cap. 8.3). */
    public static Encaminhamento encaminhamentoDe(DocumentoProcessado processado) {
        if (processado.barrado()) {
            return Encaminhamento.DESCARTADO;
        }
        return switch (processado.classificacao().decisao()) {
            case AUTOMATICA -> processado.aprovadoNasUnitarias()
                    ? Encaminhamento.VINCULAVEL
                    // Classificado com confiança e reprovado numa unitária: ele
                    // é de um tipo conhecido e não serve para satisfazer nada.
                    // Vai para triagem, onde uma pessoa vê o motivo e cobra o
                    // reenvio — em vez de sumir num painel de desconhecidos.
                    : Encaminhamento.TRIAGEM;
            case TRIAGEM -> Encaminhamento.TRIAGEM;
            case NAO_RECONHECIDO -> Encaminhamento.ORGANIZACAO;
        };
    }

    /** Para onde o documento vai depois do pipeline. */
    public enum Encaminhamento {
        /** Classificado com confiança e sem reprovação: pode vincular a exigência. */
        VINCULAVEL,
        /** Fila da F1-06: uma pessoa decide. */
        TRIAGEM,
        /** Painel de organização da F1-10: não é candidato de exigência nenhuma. */
        ORGANIZACAO,
        /** Reprovado em V1, ou ilegível a ponto de não se extrair nada. */
        DESCARTADO
    }
}
