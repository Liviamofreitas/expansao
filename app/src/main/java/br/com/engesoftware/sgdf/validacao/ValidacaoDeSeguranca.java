package br.com.engesoftware.sgdf.validacao;

import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import java.util.List;
import java.util.Map;

/**
 * V1 — antivírus limpo, MIME coerente com a extensão, tamanho dentro do limite.
 *
 * <p>Cap. 8.4: falha gera REJEITADO por segurança. É a primeira porta, e a
 * única cuja falha não admite exceção aprovada: um arquivo infectado não entra
 * no book porque alguém autorizou.
 *
 * <p><b>Antivírus indisponível não é antivírus limpo.</b> O cap. 8.1 exige
 * verificação antes da extração; quando o serviço não responde, o documento
 * fica sem veredito — nem aprovado nem reprovado. Tratar indisponibilidade como
 * aprovação abriria a porta exatamente quando a porta não está sendo vigiada.
 */
public final class ValidacaoDeSeguranca {

    public static final String CODIGO = "V1";

    /** MIME que cada extensão aceita. Cap. 8.1: pdf, xlsx, xls, csv. */
    private static final Map<String, List<String>> MIME_POR_EXTENSAO = Map.of(
            "pdf", List.of("application/pdf"),
            "xlsx", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/zip"),
            "xls", List.of("application/vnd.ms-excel", "application/x-ole-storage"),
            "csv", List.of("text/csv", "text/plain"));

    private final long tamanhoMaximo;

    public ValidacaoDeSeguranca(long tamanhoMaximo) {
        this.tamanhoMaximo = tamanhoMaximo;
    }

    /**
     * @param nomeDoArquivo nome como veio do repositório, de onde sai a extensão
     * @param mimeReal      obtido por assinatura binária, não pela extensão
     * @param tamanho       em bytes
     * @param antivirus     veredito do clamd
     */
    public ResultadoDeValidacao validar(String nomeDoArquivo, String mimeReal, long tamanho,
                                        VeredictoAntivirus antivirus) {
        if (antivirus == null || antivirus.situacao() == VeredictoAntivirus.Situacao.INDISPONIVEL) {
            return new ResultadoDeValidacao(CODIGO, Veredito.NAO_APLICAVEL,
                    "o antivírus não respondeu: o documento fica sem veredito de segurança "
                            + "até a verificação ser refeita — indisponível não é limpo",
                    Map.of());
        }
        if (antivirus.situacao() == VeredictoAntivirus.Situacao.INFECTADO) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o antivírus acusou a ameaça " + antivirus.assinatura()
                            + ": o arquivo é descartado e o evento registrado",
                    Map.of("antivirus", List.of(antivirus.assinatura())));
        }
        if (tamanho <= 0) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o arquivo está vazio (" + tamanho + " bytes)", Map.of());
        }
        if (tamanho > tamanhoMaximo) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o arquivo tem " + tamanho + " bytes e o limite é " + tamanhoMaximo,
                    Map.of("tamanho", List.of(String.valueOf(tamanho))));
        }

        String extensao = extensaoDe(nomeDoArquivo);
        List<String> aceitos = MIME_POR_EXTENSAO.get(extensao);
        if (aceitos == null) {
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "extensão '" + extensao + "' não está entre as aceitas "
                            + MIME_POR_EXTENSAO.keySet(),
                    Map.of());
        }
        if (mimeReal == null || aceitos.stream().noneMatch(mimeReal::startsWith)) {
            // Cap. 8.2: divergência extensão × conteúdo gera rejeição com motivo.
            // O nome do arquivo é o que o usuário escolheu; o MIME é o que o
            // arquivo é. Quando discordam, o nome é que está errado.
            return new ResultadoDeValidacao(CODIGO, Veredito.REPROVADO,
                    "o arquivo se chama '." + extensao + "' mas o conteúdo é '" + mimeReal
                            + "': extensão e conteúdo divergem",
                    Map.of("mime_real", List.of(String.valueOf(mimeReal)),
                            "esperado", aceitos));
        }
        return new ResultadoDeValidacao(CODIGO, Veredito.APROVADO, null, Map.of());
    }

    static String extensaoDe(String nome) {
        if (nome == null) {
            return "";
        }
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
