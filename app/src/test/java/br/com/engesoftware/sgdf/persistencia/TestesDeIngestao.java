package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.classificacao.CargaDeRegras;
import br.com.engesoftware.sgdf.classificacao.Classificador;
import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.pipeline.DocumentoProcessado;
import br.com.engesoftware.sgdf.pipeline.Pipeline;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeSeguranca;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Varredura ponta a ponta ate o banco — o que faltava entre o Pipeline e a
 * persistencia.
 *
 * <p>O Pipeline responde o que o documento E; o GravadorDoPipeline responde a
 * que obrigacao ele SERVE, e so aqui ha ciclo para comparar.
 */
public final class TestesDeIngestao {

    static final String MARCA = "teste-ingestao";

    static final String CND_RFB = "MINISTERIO DA FAZENDA Secretaria da Receita Federal do "
            + "Brasil Procuradoria-Geral da Fazenda Nacional CERTIDAO POSITIVA COM EFEITOS "
            + "DE NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS FEDERAIS E A DIVIDA ATIVA DA "
            + "UNIAO Nome: ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 Ressalvado "
            + "o direito de a Fazenda Nacional cobrar e inscrever quaisquer dividas de "
            + "responsabilidade do sujeito passivo acima identificada que vierem a ser "
            + "apuradas. Validade: 20/12/2026";
    /**
     * A MESMA certidao, com menos ancoras — conteudo 0,75, dentro da faixa de
     * triagem (0,70–0,95).
     *
     * <p>Existe porque {@link #CND_RFB} pontua <b>1,0</b> so pelo conteudo, e
     * {@code Math.min(1.0, 1.0 + peso)} e 1,0: sobre ele o bonus e
     * INVISIVEL. Uma assercao de "a confianca subiu" ali seria estruturalmente
     * incapaz de ver o que pretende medir — foi exatamente o que aconteceu na
     * primeira versao deste teste.
     */
    static final String CND_RFB_PARCIAL = "MINISTERIO DA FAZENDA Secretaria da Receita "
            + "Federal do Brasil CERTIDAO POSITIVA COM EFEITOS DE NEGATIVA DE DEBITOS "
            + "RELATIVOS AOS TRIBUTOS FEDERAIS E A DIVIDA ATIVA DA UNIAO Nome: "
            + "ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 Validade: 20/12/2026";

    static final String CNDT = "PODER JUDICIARIO JUSTICA DO TRABALHO CERTIDAO NEGATIVA DE "
            + "DEBITOS TRABALHISTAS Nome: ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 "
            + "Certidao no: 12345678/2026 Expedicao: 01/07/2026 Validade: 28/12/2026 "
            + "Certifica-se que ENGESOFTWARE TECNOLOGIA S/A NAO CONSTA do Banco Nacional de "
            + "Devedores Trabalhistas.";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (pulado: -Dsgdf.jdbc nao informado)");
            System.out.println("0/0 testes passaram.");
            return;
        }
        try (Connection conexao = DriverManager.getConnection(url)) {
            conexao.setAutoCommit(true);
            limpar(conexao);
            try {
                Sgdf sgdf = new Sgdf(conexao);
                executar("oDocumentoChegaEViraCandidatura",
                        () -> oDocumentoChegaEViraCandidatura(sgdf));
                executar("varrerDuasVezesNaoDuplica", () -> varrerDuasVezesNaoDuplica(sgdf));
                executar("oArquivoBarradoNaoViraDocumento",
                        () -> oArquivoBarradoNaoViraDocumento(sgdf));
                executar("oTerceiroDestinoFicaVisivel",
                        () -> oTerceiroDestinoFicaVisivel(sgdf));
                executar("umArquivoQueFalhaNaoDerrubaOLote",
                        () -> umArquivoQueFalhaNaoDerrubaOLote(sgdf));
                executar("aContagemNaoEscondeAsFalhas",
                        () -> aContagemNaoEscondeAsFalhas(sgdf));
                executar("aVarreduraCosturaOrigemAteOBanco",
                        () -> aVarreduraCosturaOrigemAteOBanco(sgdf));
                executar("aVarreduraTruncadaNaoDizSucesso",
                        () -> aVarreduraTruncadaNaoDizSucesso(sgdf));
                executar("aVarreduraAplicaOAliasAprendidoNaTriagem",
                        () -> aVarreduraAplicaOAliasAprendidoNaTriagem(sgdf));
                executar("oAliasDesligadoViaja",
                        () -> oAliasDesligadoViaja(sgdf));
                executar("aFalhaParcialRegistraOQueJaEntrou",
                        () -> aFalhaParcialRegistraOQueJaEntrou(sgdf));
                executar("oAlvoDaVarreduraVemDoCiclo", () -> oAlvoDaVarreduraVemDoCiclo(sgdf));
                executar("oBancoCarregaAsMesmasRegrasQueOCodigoTinha",
                        () -> oBancoCarregaAsMesmasRegrasQueOCodigoTinha(sgdf));
                executar("oDeltaCompraAUltimaVersaoRegistrada",
                        () -> oDeltaCompraAUltimaVersaoRegistrada(sgdf));
            } finally {
                limpar(conexao);
            }
        }

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    static void oDocumentoChegaEViraCandidatura(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        GravadorDoPipeline gravador = new GravadorDoPipeline(sgdf);
        DocumentoProcessado p = processar("cnd.pdf", CND_RFB);

        GravadorDoPipeline.Ingestao i = gravador.gravar(f.ciclo, p, f.arquivo("cnd.pdf"),
                "etag-1", MARCA);

        ok("F1-01+F1-03 . o documento e gravado", i.documentoId() != null && i.inedito());
        ok("Cap. 8.3 . com o tipo que a classificacao achou",
                "CER.CND_RFB".equals(escalar(sgdf, "SELECT t.codigo FROM documento d"
                        + " JOIN tipo_documental t ON t.id = d.tipo_id WHERE d.id = '"
                        + i.documentoId() + "'")));
        ok("F1-04 . com os tres vereditos unitarios gravados",
                3 == contar(sgdf, "validacao_documento WHERE documento_id = '"
                        + i.documentoId() + "'"));
        ok("F1-06 . e vira candidatura na exigencia do tipo",
                i.vinculouAExigencia()
                        && 1 == contar(sgdf, "candidatura WHERE documento_id = '"
                                + i.documentoId() + "' AND situacao = 'ABERTA'"));
        ok("Cap. 6.1 . a exigencia vai a EM_TRIAGEM",
                "EM_TRIAGEM".equals(escalar(sgdf, "SELECT status FROM exigencia WHERE id = '"
                        + f.exigencia + "'")));
    }

    /**
     * A varredura roda de 30 em 30 minutos (cap. 8.1).
     *
     * <p>E a premissa que o Agendador assume: idempotente por construcao, nao
     * por sorte.
     */
    static void varrerDuasVezesNaoDuplica(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        GravadorDoPipeline gravador = new GravadorDoPipeline(sgdf);
        byte[] bytes = pdf(CND_RFB);
        DocumentoProcessado p = processar("cnd.pdf", bytes);

        GravadorDoPipeline.Ingestao primeira = gravador.gravar(f.ciclo, p,
                f.arquivo("cnd.pdf"), "etag-1", MARCA);
        GravadorDoPipeline.Ingestao segunda = gravador.gravar(f.ciclo,
                processar("cnd.pdf", bytes), f.arquivo("cnd.pdf"), "etag-1", MARCA);

        ok("Cap. 8.1 . a segunda varredura devolve o MESMO documento",
                primeira.documentoId().equals(segunda.documentoId()));
        ok("Cap. 8.1 . e diz que nao era inedito", primeira.inedito() && !segunda.inedito());
        ok("Cap. 8.1 . sem duplicar a linha",
                1 == contar(sgdf, "documento WHERE hash_sha256 = '" + p.hashSha256() + "'"));
        ok("F1-06 . nem a candidatura",
                1 == contar(sgdf, "candidatura WHERE documento_id = '"
                        + primeira.documentoId() + "'"));
    }

    /** Um infectado na mesma tabela dos documentos que valem contamina toda consulta. */
    static void oArquivoBarradoNaoViraDocumento(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        Pipeline pipeline = pipeline();
        DocumentoProcessado p = pipeline.processar("cnd.pdf", pdf(CND_RFB),
                VeredictoAntivirus.infectado("Eicar-Test-Signature"), Set.of(), null);

        // CAMINHO PROPRIO DESTE TESTE.
        //
        // A primeira versao usava /f/06.2026/cnd.pdf, que um teste anterior ja
        // tinha gravado com a mesma MARCA — a assercao "nada foi gravado"
        // falhava por ordem de execucao, nao por defeito. Um teste que afirma
        // sobre um estado que nao controla manda procurar no lugar errado.
        String caminho = f.arquivo("infectado.pdf");
        GravadorDoPipeline.Ingestao i = new GravadorDoPipeline(sgdf).gravar(f.ciclo, p,
                caminho, "etag-1", MARCA);

        ok("Cap. 8.2 . o infectado NAO vira linha em documento",
                i.documentoId() == null
                        && Pipeline.Encaminhamento.DESCARTADO == i.destino());
        ok("Cap. 8.2 . e o motivo acompanha",
                i.observacao() != null && i.observacao().contains("Eicar"));
        ok("Cap. 8.2 . nada foi gravado",
                0 == contar(sgdf, "documento WHERE criado_por = '" + MARCA
                        + "' AND caminho = '" + caminho + "'"));
    }

    /**
     * O documento que o sistema RECONHECE e nao sabe a que serve.
     *
     * <p>Sem a consulta, ele cairia em vista nenhuma: o painel de desconhecidos
     * filtra tipo_id IS NULL e ele tem tipo; a fila de triagem lista
     * candidaturas e ele nao tem uma.
     */
    static void oTerceiroDestinoFicaVisivel(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        GravadorDoPipeline gravador = new GravadorDoPipeline(sgdf);

        // A CNDT e reconhecida com confianca e NAO e exigida neste ciclo.
        GravadorDoPipeline.Ingestao i = gravador.gravar(f.ciclo, processar("cndt.pdf", CNDT),
                f.arquivo("cndt.pdf"), "etag-2", MARCA);

        ok("Cap. 8.3 . o documento e gravado e classificado",
                i.documentoId() != null && !i.vinculouAExigencia());
        ok("Pipeline . mas nao vira candidatura, e o motivo diz por que",
                i.semExigencia() && i.observacao().contains("não é exigido"));

        List<ConsultaDoPainel.ClassificadoSemExigencia> vista =
                new ConsultaDoPainel(sgdf).classificadosSemExigencia(f.ciclo, 50);
        var linha = vista.stream().filter(v -> v.documentoId().equals(i.documentoId()))
                .findFirst().orElse(null);
        ok("Painel . e ele APARECE na vista dos classificados sem exigencia",
                linha != null && "CER.CNDT".equals(linha.tipo()));
        ok("Painel . dizendo que o tipo nao e exigido — a acao e do curador",
                linha.exigenciasDoTipo() == 0 && linha.motivo().contains("não é exigido"));

        // E o que virou candidatura NAO aparece aqui.
        gravador.gravar(f.ciclo, processar("cnd.pdf", CND_RFB), f.arquivo("cnd.pdf"),
                "etag-1", MARCA);
        ok("Painel . o que virou candidatura nao aparece na vista",
                new ConsultaDoPainel(sgdf).classificadosSemExigencia(f.ciclo, 50).stream()
                        .noneMatch(v -> "CER.CND_RFB".equals(v.tipo())));
    }

    /** Trezentos documentos que morrem no primeiro PDF corrompido entregam zero. */
    static void umArquivoQueFalhaNaoDerrubaOLote(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        GravadorDoPipeline gravador = new GravadorDoPipeline(sgdf);

        // O DO MEIO PRECISA FALHAR DENTRO DO gravar(), E NAO ANTES.
        //
        // A primeira versao usava um processado sem classificacao — que o
        // Pipeline trata como BARRADO e o gravador devolve cedo, sem erro
        // nenhum. Ele contava como processado e o teste nao media nada. Para
        // falhar de verdade, o documento precisa CHEGAR a gravacao: tipo
        // reconhecido, e hash que o Documento recusa.
        DocumentoProcessado bom = processar("quebrado.pdf", CND_RFB);
        DocumentoProcessado quebrado = new DocumentoProcessado("quebrado.pdf",
                "nao-e-um-hash", bom.tamanho(), bom.mime(), bom.texto(), bom.classificacao(),
                bom.vereditos());

        GravadorDoPipeline.Lote lote = gravador.gravarLote(f.ciclo, List.of(
                new GravadorDoPipeline.Item(processar("cnd.pdf", CND_RFB),
                        f.arquivo("a.pdf"), "e1"),
                new GravadorDoPipeline.Item(quebrado, f.arquivo("b.pdf"), "e2"),
                new GravadorDoPipeline.Item(processar("cndt.pdf", CNDT),
                        f.arquivo("c.pdf"), "e3")),
                MARCA);

        ok("RA-07 . os dois bons entram, apesar do quebrado no meio",
                lote.processados() == 2);
        ok("RA-07 . e a falha e nomeada, com o arquivo",
                lote.falhas().size() == 1 && lote.falhas().get(0).startsWith("quebrado.pdf"));
        ok("RA-07 . o lote se declara incompleto", !lote.completo());
        ok("RA-07 . e os dois estao no banco",
                2 == contar(sgdf, "documento WHERE caminho IN ('" + f.arquivo("a.pdf")
                        + "', '" + f.arquivo("c.pdf") + "')"));
    }

    /**
     * Somar as falhas ao total faria uma varredura que perdeu metade reportar o
     * mesmo numero de uma que nao perdeu nada.
     */
    static void aContagemNaoEscondeAsFalhas(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        GravadorDoPipeline.Lote lote = new GravadorDoPipeline(sgdf).gravarLote(f.ciclo,
                List.of(new GravadorDoPipeline.Item(processar("cnd.pdf", CND_RFB),
                        f.arquivo("ok.pdf"), "e1")), MARCA);

        ok("RA-07 . o lote sem falha se declara completo",
                lote.completo() && lote.falhas().isEmpty());
        ok("RA-07 . a contagem separa inedito, em triagem e sem exigencia",
                lote.ineditos() == 1 && lote.emTriagem() == 1 && lote.semExigencia() == 0);

        // Lote vazio: zero processados, e COMPLETO. Rodar e nao achar nada e um
        // desfecho legitimo — a mesma licao da V018.
        GravadorDoPipeline.Lote vazio = new GravadorDoPipeline(sgdf)
                .gravarLote(f.ciclo, List.of(), MARCA);
        ok("RA-07 . lote vazio e completo com zero processados — rodar e nao achar "
                        + "nada e desfecho, nao ausencia",
                vazio.completo() && vazio.processados() == 0);
    }

    // --- a costura completa: coleta -> pipeline -> banco ------------------------

    static void aVarreduraCosturaOrigemAteOBanco(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        // OS MESMOS BYTES NA SEGUNDA VARREDURA.
        //
        // O PDFBox carimba data de criacao: duas chamadas a pdf() com o mesmo
        // texto dao hashes diferentes, e a assercao de idempotencia mediria
        // dois arquivos distintos sendo distintos — que e sempre verdade.
        byte[] cnd = pdf(CND_RFB);
        br.com.engesoftware.sgdf.coleta.ResultadoVarredura r = varredura(
                coletado(f.arquivo("cnd.pdf"), cnd, "e1"),
                coletado(f.arquivo("cndt.pdf"), pdf(CNDT), "e2"));
        r.ignorados.add(new br.com.engesoftware.sgdf.coleta.ResultadoVarredura.Ignorado(
                f.arquivo("~$check.xlsx"),
                br.com.engesoftware.sgdf.coleta.PoliticaDeArquivos.Motivo.ARQUIVO_TEMPORARIO,
                "temporario do Office"));

        VarreduraDeCiclo.Ingerida i = new VarreduraDeCiclo(sgdf, pipeline())
                .ingerir(f.ciclo, contratoDo(sgdf, f.ciclo), "2026-06", r, MARCA);

        ok("RA-07 . os dois coletados entram no banco", i.processados() == 2);
        ok("RA-07 . um vira candidatura (a CND exigida) e o outro nao (a CNDT)",
                i.emTriagem() == 1 && i.semExigencia() == 1);
        ok("RA-07 . os dois sao ineditos", i.ineditos() == 2);
        ok("RA-07 . e a varredura completa nao e incompleta", !i.incompleta());
        ok("F1-10 . o que a varredura ignorou foi registrado antes de ingerir",
                i.organizacao() != null);

        // Idempotencia: a varredura roda de 30 em 30 minutos.
        VarreduraDeCiclo.Ingerida denovo = new VarreduraDeCiclo(sgdf, pipeline())
                .ingerir(f.ciclo, contratoDo(sgdf, f.ciclo), "2026-06",
                        varredura(coletado(f.arquivo("cnd.pdf"), cnd, "e1")), MARCA);
        ok("Cap. 8.1 . a segunda varredura nao duplica nada",
                denovo.processados() == 1 && denovo.ineditos() == 0);
    }

    /**
     * Reportar SUCESSO sobre uma varredura truncada faria o alerta de job
     * silencioso calar justamente quando metade da pasta nao foi lida.
     */
    static void aVarreduraTruncadaNaoDizSucesso(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        var r = varredura(coletado(f.arquivo("cnd.pdf"), pdf(CND_RFB), "e1"));
        r.truncada = true;
        r.motivoTruncamento = "limite de 2000 arquivos atingido";

        Agendador.FalhaParcial parcial = null;
        try {
            new VarreduraDeCiclo(sgdf, pipeline())
                    .ingerir(f.ciclo, contratoDo(sgdf, f.ciclo), "2026-06", r, MARCA);
        } catch (Agendador.FalhaParcial e) {
            parcial = e;
        }
        ok("Cap. 8.1 . a varredura truncada NAO reporta sucesso", parcial != null);
        ok("Cap. 14.1 . mas o que entrou FICA — nunca perda",
                parcial.itens() == 1
                        && 1 == contar(sgdf, "documento WHERE caminho = '"
                                + f.arquivo("cnd.pdf") + "'"));
        ok("Cap. 8.1 . e o motivo diz que a pasta nao foi lida inteira",
                parcial.getMessage().contains("truncada")
                        && parcial.getMessage().contains("2000"));
    }

    /**
     * A varredura que morre no fim e a que morre no comeco pedem investigacoes
     * diferentes — e a linha de FALHA precisa dizer qual foi.
     */
    static void aFalhaParcialRegistraOQueJaEntrou(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        executarSql(sgdf, "DELETE FROM execucao_de_job WHERE instancia = '" + MARCA + "'");
        var r = varredura(coletado(f.arquivo("cnd.pdf"), pdf(CND_RFB), "e1"));
        r.falhas.add(new br.com.engesoftware.sgdf.coleta.ResultadoVarredura.Falha(
                f.arquivo("outro.pdf"), "conexao encerrada pela origem"));

        Agendador agendador = new Agendador(sgdf, MARCA);
        UUID contrato = contratoDo(sgdf, f.ciclo);
        try {
            agendador.executar(br.com.engesoftware.sgdf.orquestracao.Job.VARREDURA_COMPLETA,
                    () -> new VarreduraDeCiclo(sgdf, pipeline())
                            .ingerir(f.ciclo, contrato, "2026-06", r, MARCA).processados());
        } catch (Agendador.FalhaParcial e) {
            // esperado
        }

        ok("RA-07 . o job fica registrado como FALHA",
                1 == contar(sgdf, "execucao_de_job WHERE instancia = '" + MARCA
                        + "' AND resultado = 'FALHA'"));
        ok("RA-07 . COM a contagem do que ja tinha entrado — 'morreu no fim' e "
                        + "'morreu no comeco' pedem investigacoes diferentes",
                "1".equals(escalar(sgdf, "SELECT itens::text FROM execucao_de_job"
                        + " WHERE instancia = '" + MARCA + "' AND resultado = 'FALHA'")));
        ok("Cap. 14.1 . e o documento ingerido antes da falha continua la",
                1 == contar(sgdf, "documento WHERE caminho = '" + f.arquivo("cnd.pdf") + "'"));
        executarSql(sgdf, "DELETE FROM execucao_de_job WHERE instancia = '" + MARCA + "'");
    }

    // -------------------------------------------------------------------------

    /**
     * RA-19: o alias aprendido na triagem chega ao arquivo que a varredura ingere.
     *
     * <p>Ate esta versao a varredura passava {@code null} no lugar do bonus. A
     * triagem gravava o alias, o RepositorioDeAlias sabia le-lo, e NENHUM
     * arquivo que a varredura ingeria passava por ele: a promessa da F1-06 — "o
     * mesmo padrao nao retorna" — valia no teste e nao valia em producao. O
     * sintoma era o arquivo do mes seguinte voltando a fila, que se parece com
     * trabalho normal.
     *
     * <p>O que se mede e a CONFIANCA gravada. Comparar o desfecho (foi ou nao
     * para a triagem) dependeria de o conteudo cair exatamente na faixa
     * 0,70–0,95, o que amarraria o teste a um score que as regras podem mudar.
     * A confianca e observavel sempre.
     */
    static void aVarreduraAplicaOAliasAprendidoNaTriagem(Sgdf sgdf) {
        Fixture semAlias = fixture(sgdf, "CER.CND_RFB");
        new VarreduraDeCiclo(sgdf, pipeline()).ingerir(semAlias.ciclo,
                contratoDo(sgdf, semAlias.ciclo), "2026-06",
                varredura(coletado(semAlias.arquivo("guia_padrao_alfa.pdf"),
                        pdf(CND_RFB_PARCIAL), "etag-a")),
                MARCA);
        String antes = escalar(sgdf, "SELECT confianca FROM documento WHERE caminho = '"
                + semAlias.arquivo("guia_padrao_alfa.pdf") + "'");
        ok("RA-19 . sem alias, a confianca gravada e a do conteudo (0,750)",
                antes != null && new java.math.BigDecimal(antes)
                        .compareTo(new java.math.BigDecimal("0.750")) == 0);

        // A triagem ensina: o padrao estavel do nome vira alias do tipo.
        executarSql(sgdf, "INSERT INTO tipo_alias (tipo_id, texto_original,"
                + " texto_normalizado, origem, criado_por)"
                + " SELECT t.id, 'GUIA_PADRAO_ALFA.pdf', 'guia_padrao_alfa', 'TRIAGEM', '"
                + MARCA + "' FROM tipo_documental t WHERE t.codigo = 'CER.CND_RFB'");

        // O MES SEGUINTE: mesmo conteudo, nome do mesmo padrao, ciclo novo.
        Fixture comAlias = fixture(sgdf, "CER.CND_RFB");
        new VarreduraDeCiclo(sgdf, pipeline()).ingerir(comAlias.ciclo,
                contratoDo(sgdf, comAlias.ciclo), "2026-06",
                varredura(coletado(comAlias.arquivo("guia_padrao_alfa_07.pdf"),
                        pdf(CND_RFB_PARCIAL), "etag-b")),
                MARCA);
        String depois = escalar(sgdf, "SELECT confianca FROM documento WHERE caminho = '"
                + comAlias.arquivo("guia_padrao_alfa_07.pdf") + "'");

        double diferenca = new java.math.BigDecimal(depois)
                .subtract(new java.math.BigDecimal(antes)).doubleValue();
        ok("RA-19 . com o alias, o arquivo do mes seguinte chega com o bonus do cadastro — "
                        + "a varredura passou a ler o que a triagem ensinou",
                Math.abs(diferenca - RepositorioDeAlias.doCadastro(sgdf).peso()) < 1e-6);

        // E A OUTRA METADE, QUE IMPORTA TANTO QUANTO: 0,75 + 0,10 = 0,85, ainda
        // dentro da faixa. O alias CORROBORA e nao ELEGE — renomear um arquivo
        // nao pode classifica-lo, e o sistema existe porque o nome nao e
        // confiavel.
        ok("Cap. 8.3 . e mesmo com o bonus o documento continua indo a triagem — "
                        + "0,85 nao fecha a faixa, e nao deve fechar",
                "PENDENTE".equals(escalar(sgdf, "SELECT status_triagem FROM documento"
                        + " WHERE caminho = '"
                        + comAlias.arquivo("guia_padrao_alfa_07.pdf") + "'")));
    }

    /**
     * Bonus desligado nao pode ser silencioso — a licao da RA-03, uma camada acima.
     *
     * <p>Sem o parametro, o alias deixa de agir e os arquivos que a triagem ja
     * ensinou voltam a fila. Quem olha a fila crescer nao tem como saber que a
     * causa e um parametro ausente e nao um mes movimentado. Por isso o motivo
     * viaja no resultado da varredura, em vez de ser deduzido.
     */
    static void oAliasDesligadoViaja(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        VarreduraDeCiclo.Ingerida comBonus = new VarreduraDeCiclo(sgdf, pipeline())
                .ingerir(f.ciclo, contratoDo(sgdf, f.ciclo), "2026-06",
                        varredura(coletado(f.arquivo("cnd.pdf"), pdf(CND_RFB), "etag-c")), MARCA);
        ok("RA-19 . com o parametro cadastrado, nada a anunciar",
                !comBonus.comAliasDesligado() && comBonus.aliasDesligado() == null);

        String anterior = escalar(sgdf, "SELECT valor #>> '{}' FROM parametro WHERE chave = '"
                + RepositorioDeAlias.CHAVE_PESO + "' AND escopo = 'GLOBAL'");
        try {
            executarSql(sgdf, "DELETE FROM parametro WHERE chave = '"
                    + RepositorioDeAlias.CHAVE_PESO + "' AND escopo = 'GLOBAL'");
            Fixture g = fixture(sgdf, "CER.CND_RFB");
            VarreduraDeCiclo.Ingerida sem = new VarreduraDeCiclo(sgdf, pipeline())
                    .ingerir(g.ciclo, contratoDo(sgdf, g.ciclo), "2026-06",
                            varredura(coletado(g.arquivo("cnd.pdf"), pdf(CND_RFB), "etag-d")), MARCA);

            ok("RA-19 . sem o parametro, a varredura ANUNCIA que o alias nao agiu",
                    sem.comAliasDesligado() && sem.aliasDesligado().contains("F1-06"));
            ok("RA-19 . e isso NAO e incompletude: a pasta foi lida inteira e o job nao "
                            + "deve falhar por causa de um bonus desligado",
                    !sem.incompleta() && sem.motivoDaIncompletude() == null);
            ok("RA-19 . os documentos entraram do mesmo jeito", sem.processados() == 1);
        } finally {
            // Restaura o que HAVIA — se nao havia, nao inventa. A primeira
            // versao deste padrao gravou o jsonb 'null' e envenenou a suite
            // inteira (achados 56.6).
            executarSql(sgdf, "DELETE FROM parametro WHERE chave = '"
                    + RepositorioDeAlias.CHAVE_PESO + "' AND escopo = 'GLOBAL'");
            if (anterior != null) {
                executarSql(sgdf, "INSERT INTO parametro (chave, escopo, valor, descricao,"
                        + " criado_por) VALUES ('" + RepositorioDeAlias.CHAVE_PESO
                        + "', 'GLOBAL', '" + anterior + "'::jsonb,"
                        + " 'restaurado pelo teste', 'carga-inicial')");
            }
        }
    }

    static br.com.engesoftware.sgdf.coleta.ResultadoVarredura varredura(
            br.com.engesoftware.sgdf.coleta.ArquivoColetado... arquivos) {
        var r = new br.com.engesoftware.sgdf.coleta.ResultadoVarredura();
        java.util.Collections.addAll(r.coletados, arquivos);
        return r;
    }

    /**
     * RA-07: o alvo da varredura sai do ciclo, e nao de quem chama.
     *
     * <p>E a mesma regra do painel (achados § 30.1) aplicada a coleta: se a
     * pasta viesse por parametro, quem chama escolheria ONDE O SISTEMA LE — e
     * um recorte que o cliente escolhe nao e restricao. Aqui ela vale duas
     * vezes, porque ler a pasta errada nao e so ver o que nao devia: e ingerir
     * documento de um contrato no ciclo de outro.
     */
    static void oAlvoDaVarreduraVemDoCiclo(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        RepositorioDeColeta coleta = new RepositorioDeColeta(sgdf);

        RepositorioDeColeta.Alvo alvo = coleta.alvoDe(f.ciclo);
        ok("RA-07 . o alvo existe para um ciclo que existe", alvo != null);
        // A RAIZ E A DO CONTRATO, NAO A DO ARQUIVO — e a primeira versao deste
        // teste comparou as duas coisas erradas e falhou com o codigo certo.
        // A fixture grava pasta_origem='/f' no contrato e guarda em f.pasta o
        // caminho ONDE OS ARQUIVOS MORAM ('/f/ing-N/2026/06'). Sao niveis
        // diferentes: a varredura recebe a raiz e desce ate AAAA/MM sozinha.
        ok("RA-07 . a raiz e a pasta_origem do CONTRATO, nao a do arquivo",
                alvo != null && "/f".equals(alvo.pastaOrigem()));
        ok("RA-07 . e os arquivos do ciclo ficam abaixo dela",
                alvo != null && f.pasta.startsWith(alvo.pastaOrigem() + "/"));
        ok("RA-07 . a competencia vem do ciclo",
                alvo != null && "2026-06".equals(alvo.competencia()));
        ok("RA-07 . e o contrato tambem",
                alvo != null && contratoDo(sgdf, f.ciclo).equals(alvo.contratoId()));

        ok("RA-07 . ciclo inexistente devolve null, e nao a pasta de outro",
                coleta.alvoDe(UUID.randomUUID()) == null);
    }

    /**
     * Cap. 8.1: o delta compara com a ULTIMA versao registrada.
     *
     * <p>O caminho e o mesmo; o que muda e a versao na origem. Depois de duas
     * ingestoes, perguntar "qual versao eu conheco?" tem que responder a
     * SEGUNDA. Responder a primeira faria a varredura tratar um documento novo
     * como inalterado — ele nunca mais seria baixado, e o ciclo ficaria com a
     * versao velha para sempre, sem erro nenhum em lugar nenhum.
     */
    static void oDeltaCompraAUltimaVersaoRegistrada(Sgdf sgdf) {
        Fixture f = fixture(sgdf, "CER.CND_RFB");
        RepositorioDeColeta coleta = new RepositorioDeColeta(sgdf);
        String caminho = f.arquivo("cnd.pdf");

        ok("Cap. 8.1 . caminho nunca visto nao tem versao conhecida",
                coleta.estadoDe("OWNCLOUD").versaoDe(caminho) == null);

        new VarreduraDeCiclo(sgdf, pipeline()).ingerir(f.ciclo, contratoDo(sgdf, f.ciclo),
                "2026-06", varredura(coletado(caminho, pdf(CND_RFB), "versao-1")), MARCA);
        ok("Cap. 8.1 . depois de ingerir, a versao da origem e conhecida",
                "versao-1".equals(coleta.estadoDe("OWNCLOUD").versaoDe(caminho)));

        // O MESMO caminho, conteudo diferente, versao nova na origem.
        new VarreduraDeCiclo(sgdf, pipeline()).ingerir(f.ciclo, contratoDo(sgdf, f.ciclo),
                "2026-06", varredura(coletado(caminho, pdf(CNDT), "versao-2")), MARCA);
        ok("Cap. 8.1 . com duas versoes do mesmo caminho, a conhecida e a ULTIMA",
                "versao-2".equals(coleta.estadoDe("OWNCLOUD").versaoDe(caminho)));

        ok("Cap. 8.1 . outra origem nao ve a versao desta",
                coleta.estadoDe("JIRA").versaoDe(caminho) == null);
    }

    /**
     * A troca de fonte nao pode custar um tipo reconhecido.
     *
     * <p>O classificador passou a carregar do banco. Este teste e a prova de que
     * isso NAO significou deixar de reconhecer nada: o conjunto que o
     * RepositorioDeRegras devolve — banco mais comprovantes do codigo — tem de
     * ser o mesmo que `CargaDeRegras.todas()` devolvia sozinho.
     *
     * <p>Sem ele, perder uma regra na migracao seria invisivel: documentos
     * daquele tipo passariam a cair em triagem manual, e a suspeita recairia
     * sobre os arquivos, nao sobre a carga.
     */
    static void oBancoCarregaAsMesmasRegrasQueOCodigoTinha(Sgdf sgdf) {
        java.util.Set<String> doBanco = new java.util.TreeSet<>();
        for (br.com.engesoftware.sgdf.classificacao.RegraDeReconhecimento r
                : new RepositorioDeRegras(sgdf).ativas()) {
            doBanco.add(r.tipo() + "|" + (r.emissor() == null ? "" : r.emissor()));
        }
        java.util.Set<String> doCodigo = new java.util.TreeSet<>();
        for (br.com.engesoftware.sgdf.classificacao.RegraDeReconhecimento r
                : br.com.engesoftware.sgdf.classificacao.CargaDeRegras.todas()) {
            doCodigo.add(r.tipo() + "|" + (r.emissor() == null ? "" : r.emissor()));
        }

        java.util.Set<String> faltando = new java.util.TreeSet<>(doCodigo);
        faltando.removeAll(doBanco);
        ok("F1-03 . o banco carrega TODAS as regras que o codigo tinha — faltam " + faltando,
                faltando.isEmpty());

        java.util.Set<String> sobrando = new java.util.TreeSet<>(doBanco);
        sobrando.removeAll(doCodigo);
        ok("F1-03 . e nenhuma a mais — sobram " + sobrando, sobrando.isEmpty());

        // A FRONTEIRA, VERIFICADA E NAO SO ESCRITA.
        ok("Cap. 8.5 . os comprovantes NAO vem do banco: nao sao tipo do checklist",
                doBanco.stream().anyMatch(x -> x.startsWith("CMP."))
                        && contarTipos(sgdf, "CMP.") == 0);
    }

    /** Quantos tipos com este prefixo existem no cadastro. */
    static int contarTipos(Sgdf sgdf, String prefixo) {
        return sgdf.emTransacao(conexao -> {
            try (java.sql.PreparedStatement ps = conexao.prepareStatement(
                    "SELECT count(*) FROM tipo_documental WHERE codigo LIKE ?")) {
                ps.setString(1, prefixo + "%");
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    static br.com.engesoftware.sgdf.coleta.ArquivoColetado coletado(String caminho,
                                                                    byte[] conteudo,
                                                                    String etag) {
        return new br.com.engesoftware.sgdf.coleta.ArquivoColetado(caminho, etag,
                Pipeline.sha256(conteudo), conteudo.length, conteudo);
    }

    static UUID contratoDo(Sgdf sgdf, UUID ciclo) {
        return UUID.fromString(escalar(sgdf,
                "SELECT contrato_servico_id::text FROM ciclo WHERE id = '" + ciclo + "'"));
    }

    /**
     * @param pasta caminho proprio deste fixture — ver a nota em {@link #fixture}
     */
    record Fixture(UUID ciclo, UUID exigencia, String pasta) {

        String arquivo(String nome) {
            return pasta + "/" + nome;
        }
    }

    static Fixture fixture(Sgdf sgdf, String tipoCodigo) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Ing " + n + "', '" + String.format("9%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-ing-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Ing " + n + "', '" + String.format("8%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-ING-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/f', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        UUID ciclo = uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '2026-06',"
                + " 'EM_COLETA', '" + versao + "', '" + MARCA + "') RETURNING id");
        // O tipo ja existe na carga (V102); a exigencia e que e do fixture.
        UUID exigencia = uuid(sgdf, "INSERT INTO exigencia (ciclo_id, tipo_id, evento, status,"
                + " prazo_calculado, criticidade, responsavel, origem, criado_por)"
                + " SELECT '" + ciclo + "', t.id, 'MENSAL', 'PENDENTE', DATE '2026-07-05',"
                + " 'BLOQUEANTE', 'FIN', 'MATRIZ', '" + MARCA + "'"
                + " FROM tipo_documental t WHERE t.codigo = '" + tipoCodigo + "' RETURNING id");
        // PASTA PROPRIA POR FIXTURE, E E A RAIZ DE UM DEFEITO QUE SE REPETIU.
        //
        // Com caminho fixo, dois testes com a mesma MARCA gravam a mesma linha
        // e as assercoes de contagem de um passam a depender de quem rodou
        // antes. Aconteceu tres vezes nesta sessao — no agendador, no
        // infectado, e aqui. Consertar a assercao trata o sintoma; a causa e o
        // caminho compartilhado.
        return new Fixture(ciclo, exigencia, "/f/ing-" + n + "/2026/06");
    }

    static Pipeline pipeline() {
        return new Pipeline(new ExtratorPdfBox(), new Classificador(CargaDeRegras.todas()),
                new ValidacaoDeSeguranca(50L * 1024 * 1024));
    }

    static DocumentoProcessado processar(String nome, String texto) {
        return processar(nome, pdf(texto));
    }

    static DocumentoProcessado processar(String nome, byte[] bytes) {
        return pipeline().processar(nome, bytes, VeredictoAntivirus.limpo(), Set.of(), null);
    }

    static byte[] pdf(String texto) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream fluxo = new PDPageContentStream(doc, pagina)) {
                fluxo.beginText();
                fluxo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                fluxo.newLineAtOffset(40, 780);
                fluxo.setLeading(11);
                StringBuilder atual = new StringBuilder();
                for (String palavra : texto.split(" ")) {
                    if (atual.length() + palavra.length() > 90) {
                        fluxo.showText(atual.toString());
                        fluxo.newLine();
                        atual = new StringBuilder();
                    }
                    atual.append(atual.isEmpty() ? "" : " ").append(palavra);
                }
                fluxo.showText(atual.toString());
                fluxo.endText();
            }
            doc.save(saida);
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("falha ao montar o PDF do fixture", e);
        }
    }

    static UUID uuid(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, UUID.class);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static void executarSql(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static String escalar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao ler " + sql, e);
        }
    }

    static long contar(Sgdf sgdf, String de) {
        return Long.parseLong(escalar(sgdf, "SELECT count(*) FROM " + de));
    }

    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM validacao_documento WHERE documento_id IN (SELECT id FROM documento"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM candidatura WHERE documento_id IN (SELECT id FROM documento"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM vinculo_exigencia_documento WHERE documento_id IN (SELECT id FROM"
                    + " documento WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM documento WHERE criado_por = '" + MARCA + "'",
            // A varredura registra achados de organização, e a FK para
            // contrato_servico barra a exclusão se eles ficarem.
            "DELETE FROM achado_de_organizacao WHERE contrato_servico_id IN"
                    + " (SELECT id FROM contrato_servico WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM execucao_de_job WHERE instancia = '" + MARCA + "'",
            // texto_normalizado é UNIQUE e a carga não o traz: sem esta linha o
            // teste do alias passa uma vez e falha em todas as execuções
            // seguintes com violação de unicidade. É a quarta vez nesta sessão
            // que um fixture não limpo produziria um teste que só funciona da
            // primeira vez — desta vez a limpeza entrou antes de a falha
            // aparecer.
            "DELETE FROM tipo_alias WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM exigencia WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM empresa WHERE criado_por = '" + MARCA + "'",
        };
        try (Statement st = conexao.createStatement()) {
            for (String c : comandos) {
                st.execute(c);
            }
        }
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

    private TestesDeIngestao() {}
}
