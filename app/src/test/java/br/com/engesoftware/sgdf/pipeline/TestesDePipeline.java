package br.com.engesoftware.sgdf.pipeline;

import br.com.engesoftware.sgdf.classificacao.CargaDeRegras;
import br.com.engesoftware.sgdf.classificacao.Classificador;
import br.com.engesoftware.sgdf.classificacao.Decisao;
import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeSeguranca;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * O pipeline ponta a ponta, sem banco e sem a massa real.
 *
 * <p>A medicao contra os documentos reais vive em {@link MedirPrecisao} e exige
 * {@code -Dsgdf.massa}. Aqui verifica-se o ENCADEAMENTO: a ordem das etapas, o
 * que cada uma faz com o resultado da anterior, e as decisoes que so aparecem
 * quando elas estao ligadas.
 */
public final class TestesDePipeline {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    /** Trecho da CND da Receita real — o mesmo texto usado nos fixtures da F1-03. */
    static final String CND_RFB = "MINISTERIO DA FAZENDA Secretaria da Receita Federal do "
            + "Brasil Procuradoria-Geral da Fazenda Nacional CERTIDAO POSITIVA COM EFEITOS "
            + "DE NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS FEDERAIS E A DIVIDA ATIVA DA "
            + "UNIAO Nome: ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 Ressalvado "
            + "o direito de a Fazenda Nacional cobrar e inscrever quaisquer dividas de "
            + "responsabilidade do sujeito passivo acima identificada que vierem a ser "
            + "apuradas. Validade: 20/12/2026";

    public static void main(String[] args) {
        executar("aOrdemProtegeAExtracao", TestesDePipeline::aOrdemProtegeAExtracao);
        executar("oCaminhoCompleto", TestesDePipeline::oCaminhoCompleto);
        executar("classificaMesmoReprovado", TestesDePipeline::classificaMesmoReprovado);
        executar("oPdfCorrompidoNaoDerrubaAVarredura",
                TestesDePipeline::oPdfCorrompidoNaoDerrubaAVarredura);
        executar("oNomeDoArquivoNaoClassifica", TestesDePipeline::oNomeDoArquivoNaoClassifica);
        executar("aMedicaoSeparaAbstencaoDeErro",
                TestesDePipeline::aMedicaoSeparaAbstencaoDeErro);
        executar("aMedicaoNaoAfirmaSobreNada", TestesDePipeline::aMedicaoNaoAfirmaSobreNada);
        executar("aMedicaoCobraGabarito", TestesDePipeline::aMedicaoCobraGabarito);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    /**
     * O arquivo infectado nao chega ao PDFBox.
     *
     * <p>"Validar depois de processar" e validar depois do dano. A prova nao e
     * que o veredito sai REPROVADO — e que o texto NAO foi extraido: um
     * documento barrado sai do pipeline sem classificacao e sem paginas.
     */
    static void aOrdemProtegeAExtracao() {
        DocumentoProcessado d = pipeline().processar("cnd.pdf", pdf(CND_RFB),
                VeredictoAntivirus.infectado("Eicar-Test-Signature"), Set.of(), null);

        ok("Cap. 8.2 . o arquivo infectado e barrado", d.barrado());
        ok("Cap. 8.2 . e NAO foi aberto — sem texto e sem classificacao",
                d.textoExtraido().isEmpty() && d.tipoReconhecido().isEmpty());
        ok("Cap. 8.2 . com o motivo nomeando a assinatura",
                d.reprovacoes().stream().anyMatch(
                        v -> v.motivo().contains("Eicar-Test-Signature")));
        ok("Cap. 8.3 . e o encaminhamento e DESCARTADO, nao ORGANIZACAO — um "
                        + "arquivo infectado nao e um arquivo por organizar",
                Pipeline.Encaminhamento.DESCARTADO == Pipeline.encaminhamentoDe(d));

        // O ANTIVIRUS QUE NAO RESPONDEU TAMBEM NAO ABRE O ARQUIVO... nao.
        // Indisponivel e NAO_APLICAVEL, nao reprovacao: o documento segue, e o
        // veredito fica registrado para a verificacao ser refeita. Travar aqui
        // faria o clamd fora do ar parar o faturamento (cap. 1, principio 1).
        DocumentoProcessado semAv = pipeline().processar("cnd.pdf", pdf(CND_RFB),
                VeredictoAntivirus.indisponivel("timeout"), Set.of(), null);
        ok("Cap. 1 . antivirus indisponivel nao trava o documento",
                !semAv.barrado() && "CER.CND_RFB".equals(semAv.tipo()));
        ok("Cap. 16 . mas o veredito fica registrado como NAO_APLICAVEL",
                semAv.vereditos().stream().anyMatch(
                        v -> "V1".equals(v.codigo()) && !v.aprovado() && !v.reprovado()));
    }

    static void oCaminhoCompleto() {
        // OS MESMOS BYTES, E NAO DUAS CHAMADAS A pdf().
        //
        // O PDFBox carimba data de criacao e ID no documento, entao dois PDFs
        // com o mesmo texto tem hashes diferentes. Isso e correto — o hash e do
        // ARQUIVO, nao do texto —, mas significa que verificar V7 com duas
        // chamadas verificaria que dois arquivos distintos sao distintos, o que
        // e sempre verdade e nao prova nada.
        byte[] bytes = pdf(CND_RFB);
        DocumentoProcessado d = pipeline().processar("qualquer-nome.pdf", bytes,
                VeredictoAntivirus.limpo(), Set.of(), null);

        ok("F1-02 . o texto foi extraido", d.textoExtraido().isPresent());
        ok("F1-03 . e o tipo reconhecido a partir dele", "CER.CND_RFB".equals(d.tipo()));
        ok("F1-03 . com decisao automatica", Decisao.AUTOMATICA == d.decisao());
        ok("F1-04 . as tres unitarias que se decidem so com o arquivo rodaram",
                d.vereditos().stream().map(ResultadoDeValidacao::codigo).sorted().toList()
                        .equals(List.of("V1", "V2", "V7")));
        ok("Cap. 8.1 . e o hash tem 64 hexadecimais — a identidade do conteudo",
                d.hashSha256().matches("[0-9a-f]{64}"));
        ok("Cap. 8.3 . o encaminhamento e VINCULAVEL",
                Pipeline.Encaminhamento.VINCULAVEL == Pipeline.encaminhamentoDe(d));

        // V7: o mesmo conteudo ja vinculado a esta exigencia.
        DocumentoProcessado repetido = pipeline().processar("copia.pdf", bytes,
                VeredictoAntivirus.limpo(), Set.of(d.hashSha256()), null);
        ok("V7 . o mesmo hash ja vinculado reprova", !repetido.aprovadoNasUnitarias());
        ok("Cap. 8.3 . e o duplicado vai a TRIAGEM, nao vincula",
                Pipeline.Encaminhamento.TRIAGEM == Pipeline.encaminhamentoDe(repetido));
    }

    /**
     * Reprovado numa unitaria ainda e de algum tipo, e saber qual importa.
     *
     * <p>Sem isto, a mensagem para a area seria "um arquivo chegou reprovado"
     * em vez de "a CND da Receita chegou reprovada" — e ninguem sabe o que
     * reenviar.
     */
    static void classificaMesmoReprovado() {
        byte[] bytes = pdf(CND_RFB);
        DocumentoProcessado d = pipeline().processar("cnd.pdf", bytes,
                VeredictoAntivirus.limpo(), Set.of(Pipeline.sha256(bytes)), null);
        ok("Cap. 12 . o documento reprovado continua identificado",
                "CER.CND_RFB".equals(d.tipo()) && !d.aprovadoNasUnitarias());
        ok("Cap. 8.3 . classificado com confianca e reprovado vai a TRIAGEM, "
                        + "nao ao painel de desconhecidos",
                Pipeline.Encaminhamento.TRIAGEM == Pipeline.encaminhamentoDe(d));
    }

    /** Uma varredura de 300 arquivos nao morre no primeiro PDF corrompido. */
    static void oPdfCorrompidoNaoDerrubaAVarredura() {
        byte[] lixo = "%PDF-1.4\nisto nao e um pdf de verdade".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        DocumentoProcessado d = pipeline().processar("quebrado.pdf", lixo,
                VeredictoAntivirus.limpo(), Set.of(), null);

        ok("F1-02 . o PDF ilegivel produz veredito, nao excecao", d.barrado());
        ok("V2 . e o veredito e de legibilidade, com motivo",
                d.reprovacoes().stream().anyMatch(v -> "V2".equals(v.codigo())));
    }

    /**
     * O nome do arquivo nao chega ao classificador.
     *
     * <p>E a propriedade que sustenta a medicao inteira: um sistema que
     * reconhecesse pelo nome mediria a convencao de nomes da pasta. Verificado
     * dando ao documento um nome que aponta para OUTRO tipo.
     */
    static void oNomeDoArquivoNaoClassifica() {
        byte[] bytes = pdf(CND_RFB);
        DocumentoProcessado a = pipeline().processar("CONTRACHEQUE_DO_FULANO.pdf",
                bytes, VeredictoAntivirus.limpo(), Set.of(), null);
        ok("Cap. 8.3 . o nome mentiroso nao muda o tipo",
                "CER.CND_RFB".equals(a.tipo()));

        DocumentoProcessado b = pipeline().processar("aaaa.pdf", bytes,
                VeredictoAntivirus.limpo(), Set.of(), null);
        ok("Cap. 8.3 . e dois nomes diferentes dao o mesmo tipo e o mesmo hash",
                a.tipo().equals(b.tipo()) && a.hashSha256().equals(b.hashSha256()));
    }

    // --- a medicao do cap. 19 -------------------------------------------------

    /**
     * Abstencao nao e acerto nem erro.
     *
     * <p>Somar abstencao aos acertos daria 100% a um sistema que manda tudo
     * para triagem; soma-la aos erros puniria "na duvida, pergunte" e empurraria
     * os limiares para baixo — que e o caminho direto para o erro caro.
     */
    static void aMedicaoSeparaAbstencaoDeErro() {
        MedicaoDePrecisao m = new MedicaoDePrecisao();
        m.registrar("a.pdf", "CER.CNDT", "CER.CNDT", Decisao.AUTOMATICA,
                Pipeline.Encaminhamento.VINCULAVEL);
        m.registrar("b.pdf", "CER.CNDT", "CER.CND_RFB", Decisao.AUTOMATICA,
                Pipeline.Encaminhamento.VINCULAVEL);
        m.registrar("c.pdf", "CER.CNDT", null, Decisao.NAO_RECONHECIDO,
                Pipeline.Encaminhamento.ORGANIZACAO);
        m.registrar("d.pdf", "CER.CNDT", "CER.CNDT", Decisao.TRIAGEM,
                Pipeline.Encaminhamento.TRIAGEM);

        ok("Cap. 19 . a precisao e sobre o que o sistema AFIRMOU: 1 de 2",
                Math.abs(m.precisao() - 0.5) < 1e-9);
        ok("Cap. 19 . e a cobertura diz sobre quanto ele afirmou: 2 de 4",
                Math.abs(m.cobertura() - 0.5) < 1e-9);
        ok("Cap. 19 . o candidato certo mandado a triagem e abstencao, nao acerto",
                m.porTipo().get("CER.CNDT").abstencoes() == 2);
        ok("Cap. 19 . as divergencias separam ERRO de ABSTENCAO",
                m.divergencias().stream()
                        .filter(x -> x.situacao() == MedicaoDePrecisao.Situacao.ERRO)
                        .count() == 1);

        // O CASO QUE A MASSA REAL REVELOU.
        //
        // O comprovante de INSS foi classificado CMP.DARF com AUTOMATICA e ainda
        // assim caiu em TRIAGEM, porque V2 reprovou uma pagina quase em branco
        // entre as oito. Debitar isso do classificador esconderia um acerto e
        // culparia a classificacao por uma decisao da legibilidade.
        MedicaoDePrecisao inss = new MedicaoDePrecisao();
        inss.registrar("inss.pdf", "CMP.DARF", "CMP.DARF", Decisao.AUTOMATICA,
                Pipeline.Encaminhamento.TRIAGEM);
        ok("Cap. 19 . classificado certo e retido por outra validacao conta como ACERTO",
                inss.precisao() == 1.0 && inss.divergencias().isEmpty());
    }

    static void aMedicaoNaoAfirmaSobreNada() {
        MedicaoDePrecisao m = new MedicaoDePrecisao();
        m.registrar("a.pdf", "CER.CNDT", null, Decisao.NAO_RECONHECIDO,
                Pipeline.Encaminhamento.ORGANIZACAO);

        ok("Cap. 19 . sem afirmacao nenhuma a precisao e nula, nao 100%",
                m.precisao() == null);
        ok("Cap. 19 . e a meta NAO esta atingida — um sistema que so se abstem "
                        + "nao tem precisao perfeita, tem precisao indefinida",
                !m.atingeAMeta());
        ok("Cap. 19 . a cobertura e zero", m.cobertura() == 0.0);

        MedicaoDePrecisao vazia = new MedicaoDePrecisao();
        ok("Cap. 19 . e sem arquivo nenhum tambem nao se declara meta atingida",
                !vazia.atingeAMeta() && vazia.cobertura() == 0.0);
    }

    /** Sem gabarito humano nao ha denominador — o cap. 19 e explicito. */
    static void aMedicaoCobraGabarito() {
        boolean recusou = false;
        try {
            new MedicaoDePrecisao().registrar("a.pdf", null, "CER.CNDT",
                    Decisao.AUTOMATICA, Pipeline.Encaminhamento.VINCULAVEL);
        } catch (IllegalArgumentException e) {
            recusou = true;
        }
        ok("Cap. 19 . arquivo sem gabarito nao entra na conta", recusou);
    }

    // -------------------------------------------------------------------------

    static Pipeline pipeline() {
        return new Pipeline(new ExtratorPdfBox(), new Classificador(CargaDeRegras.todas()),
                new ValidacaoDeSeguranca(50L * 1024 * 1024));
    }

    /** Um PDF de uma pagina com o texto dado — o mesmo fixture da F1-03. */
    static byte[] pdf(String texto) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream saida =
                new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream fluxo = new PDPageContentStream(doc, pagina)) {
                fluxo.beginText();
                fluxo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                fluxo.newLineAtOffset(40, 780);
                fluxo.setLeading(11);
                for (String linha : quebrar(texto)) {
                    fluxo.showText(linha);
                    fluxo.newLine();
                }
                fluxo.endText();
            }
            doc.save(saida);
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("falha ao montar o PDF do fixture", e);
        }
    }

    static List<String> quebrar(String texto) {
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : texto.split(" ")) {
            if (atual.length() + palavra.length() > 90) {
                linhas.add(atual.toString());
                atual = new StringBuilder();
            }
            atual.append(atual.isEmpty() ? "" : " ").append(palavra);
        }
        if (!atual.isEmpty()) {
            linhas.add(atual.toString());
        }
        return linhas;
    }

    interface Teste {
        void executar() throws Exception;
    }

    static void executar(String nome, Teste teste) {
        try {
            teste.executar();
        } catch (Exception | AssertionError e) {
            falhas.add(nome + " lancou " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    private TestesDePipeline() {}
}
