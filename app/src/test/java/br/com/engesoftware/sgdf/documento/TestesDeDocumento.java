package br.com.engesoftware.sgdf.documento;

import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Testes dos leitores de documento.
 *
 * <p>As coordenadas dos fixtures sao as medidas nos documentos reais da massa,
 * porque o que quebra estes leitores e geometria, nao logica.
 */
public final class TestesDeDocumento {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        executar("loteCompleto", TestesDeDocumento::loteCompleto);
        executar("loteComNomeEmTresLinhas", TestesDeDocumento::loteComNomeEmTresLinhas);
        executar("loteSemRodapeNaoConfere", TestesDeDocumento::loteSemRodapeNaoConfere);
        executar("loteComRodapeQueNaoBate", TestesDeDocumento::loteComRodapeQueNaoBate);
        executar("matriculaSaiDoNumeroDoCliente", TestesDeDocumento::matriculaSaiDoNumeroDoCliente);
        executar("folhaSaiDosContracheques", TestesDeDocumento::folhaSaiDosContracheques);
        executar("duasViasNaoDobramAFolha", TestesDeDocumento::duasViasNaoDobramAFolha);
        executar("contrachequeQueNaoFechaEAcusado", TestesDeDocumento::contrachequeQueNaoFechaEAcusado);
        executar("insumosDasConciliacoes", TestesDeDocumento::insumosDasConciliacoes);
        executar("reciboQueContinuaEmOutraFolha", TestesDeDocumento::reciboQueContinuaEmOutraFolha);
        executar("identificadorTemCodigoDeEmpresa", TestesDeDocumento::identificadorTemCodigoDeEmpresa);
        executar("fopagLeitaPorCodigo", TestesDeDocumento::fopagLeitaPorCodigo);
        executar("relacaoDeBeneficioPorMatricula", TestesDeDocumento::relacaoDeBeneficioPorMatricula);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    static void loteCompleto() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(3, "15.319,82"));

        ok("Lote . os tres pagamentos sao lidos", c.pagamentos().size() == 3);
        ok("Lote . a soma reproduz o valor declarado no rodape",
                c.somaLida().compareTo(new BigDecimal("15319.82")) == 0);
        ok("Lote . e a leitura confere com o rodape", c.confere());

        PagamentoDoLote p = c.pagamentos().get(1);
        ok("Lote . o numero do pagamento sai da coluna certa",
                p.numeroPagamento().equals("900059649"));
        ok("Lote . o nome de uma linha so fica inteiro",
                p.favorecido().equals("RONI DA SILVA ROSA"));
        ok("Lote . o valor nao leva o tipo de pagamento junto",
                p.valor().compareTo(new BigDecimal("5935.22")) == 0);
    }

    /**
     * O caso que motivou o achado A18: o nome ocupa tres linhas visuais, uma
     * acima e uma abaixo da linha dos numeros.
     */
    static void loteComNomeEmTresLinhas() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(3, "15.319,82"));

        ok("Lote . nome acima e abaixo da linha dos numeros e reunido",
                c.pagamentos().get(0).favorecido().equals("DOUGLAS VINISIOS NUNES SOUZA"));
        ok("Lote . e o registro do vizinho nao entra no nome",
                c.pagamentos().get(2).favorecido().equals("ADILON SANTIAGO DA SILVA"));
        ok("Lote . o numero do cliente do registro de tres linhas esta certo",
                c.pagamentos().get(0).numeroCliente().equals("00000010225306072026"));
    }

    /**
     * Achado A12: o comprovante do Itau traz so os rotulos, sem dado nenhum.
     * Ler zero pagamentos e dizer que confere seria o pior resultado possivel —
     * uma falha silenciosa num documento que ja passou por antivirus, MIME e
     * legibilidade.
     */
    static void loteSemRodapeNaoConfere() throws Exception {
        ComprovanteEmLote c = lerLote(null);

        // A prova de que a delimitacao por ancora funciona: sem o literal do
        // rodape, o texto de atendimento nao entra nos dados, e os tres
        // registros continuam sendo lidos.
        ok("Lote . sem rodape o texto de atendimento nao entra nos dados",
                c.pagamentos().size() == 3);
        ok("Lote . e a projecao das colunas nao e destruida por ele",
                c.problemasDeLeitura().isEmpty());
        ok("Lote . documento sem rodape nao e dado por conferido", !c.confere());
        ok("Lote . e a recusa diz que a leitura nao pode ser verificada",
                c.divergenciasComORodape().get(0).contains("não pôde ser verificada"));
    }

    static void loteComRodapeQueNaoBate() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(4, "15.319,82"));

        ok("Lote . rodape com contagem diferente da lida acusa divergencia",
                !c.confere() && c.divergenciasComORodape().size() == 1);
        ok("Lote . e a divergencia nomeia os dois numeros",
                c.divergenciasComORodape().get(0).contains("4")
                        && c.divergenciasComORodape().get(0).contains("3"));
    }

    /** Achado A14: a matricula esta embutida no numero do cliente. */
    static void matriculaSaiDoNumeroDoCliente() throws Exception {
        ComprovanteEmLote c = lerLote(rodape(3, "15.319,82"));
        ok("Lote . a matricula sai do numero do cliente sem a data e sem zeros",
                c.pagamentos().get(0).matricula().equals("102253"));
        ok("Lote . numero do cliente curto demais nao inventa matricula",
                new PagamentoDoLote("9", "06072026", "X", "06/07/2026", "CC",
                        BigDecimal.ONE).matricula() == null);
    }

    /**
     * A05: o lado esperado das conciliacoes sai do contracheque, que ja esta no
     * repositorio, enquanto o sistema de folha nao e nomeado.
     */
    static void folhaSaiDosContracheques() throws Exception {
        FolhaDeCompetencia f = lerFolha(true);

        ok("Folha . a competencia sai da referencia por extenso",
                f.competencia().equals("06/2026"));
        ok("Folha . um item por colaborador", f.itens().size() == 2);

        ItemDaFolha i = f.porMatricula("000101862").orElseThrow();
        ok("Folha . matricula, nome e CPF saem do cabecalho do recibo",
                i.nome().equals("ADRIANO LIN SOARES PERRUOLO")
                        && i.cpf().equals("052.190.471-40"));
        ok("Folha . rubrica com quantidade traz a quantidade",
                i.proventos().get(0).quantidade().compareTo(new BigDecimal("30.00")) == 0);
        ok("Folha . rubrica sem quantidade nao inventa uma",
                i.proventos().get(1).quantidade() == null);

        // O caso do leitor de tabela, agora no dominio: a rubrica que aparece
        // sozinha na metade da direita nao pode virar provento.
        ok("Folha . desconto sozinho na linha nao vira provento",
                i.proventos().size() == 2 && i.descontos().size() == 4);
        ok("Folha . e o vale alimentacao esta entre os descontos",
                i.desconto("VALE ALIMENTACAO").orElseThrow()
                        .valor().compareTo(new BigDecimal("64.89")) == 0);
        ok("Folha . o item fecha com os totais impressos", i.fecha());
    }

    /**
     * Achado A17: cada pagina traz o recibo duas vezes. Somar sem deduplicar
     * dobra o liquido de toda a folha.
     */
    static void duasViasNaoDobramAFolha() throws Exception {
        FolhaDeCompetencia f = lerFolha(true);
        ok("A17 . as duas vias da pagina viram um item so", f.itens().size() == 2);
        ok("A17 . e o liquido nao dobra",
                f.somaDosLiquidos().compareTo(new BigDecimal("9619.67")) == 0);
    }

    /**
     * O contracheque carrega a propria conferencia. Uma extracao que perde uma
     * rubrica precisa dizer isso, nao entregar uma folha plausivel.
     */
    static void contrachequeQueNaoFechaEAcusado() throws Exception {
        FolhaDeCompetencia f = lerFolha(false);
        ItemDaFolha i = f.porMatricula("000101862").orElseThrow();

        ok("Folha . item com total impresso divergente nao fecha", !i.fecha());
        ok("Folha . e a inconsistencia mostra os dois numeros",
                i.inconsistencias().get(0).contains("7300.79"));
        ok("Folha . a folha nomeia o colaborador da inconsistencia",
                f.inconsistencias().get(0).startsWith("000101862"));
    }

    /** O que as regras de conciliacao consomem. */
    static void insumosDasConciliacoes() throws Exception {
        FolhaDeCompetencia f = lerFolha(true);

        ok("R05 . as matriculas da competencia saem da folha",
                f.matriculas().equals(List.of("000101862", "000101864")));
        ok("R08 . quem tem desconto de vale alimentacao sai por CPF",
                f.comDescontoDe("VALE ALIMENTACAO").keySet()
                        .equals(java.util.Set.of("052.190.471-40", "054.721.901-69")));
        ok("R09 . a soma das bases de FGTS e o insumo da conciliacao com a guia",
                f.somaDasBasesFgts().compareTo(new BigDecimal("11876.28")) == 0);
        ok("R10 . a soma das bases de INSS tambem",
                f.somaDasBasesInss().compareTo(new BigDecimal("11876.28")) == 0);
    }

    /**
     * Achado A21: o recibo de um colaborador com ferias nao cabe numa pagina.
     * A folha 1/2 traz "Continua..." e nenhum total; a 2/2 traz o resto das
     * rubricas e os totais. Descartar a continuacao como se fosse a via
     * repetida perde metade dos proventos.
     */
    static void reciboQueContinuaEmOutraFolha() throws Exception {
        FolhaDeCompetencia f = new LeitorDeContracheque()
                .ler(new ExtratorPdfBox().extrair(contrachequeComContinuacao()));

        ok("A21 . as duas folhas do mesmo recibo viram um item so", f.itens().size() == 1);
        ItemDaFolha i = f.itens().get(0);
        ok("A21 . as rubricas das duas folhas se somam", i.proventos().size() == 2);
        ok("A21 . os totais vem da folha que os imprime",
                i.totalProventos().compareTo(new BigDecimal("9000.00")) == 0);
        ok("A21 . e o item fecha com eles", i.fecha());
    }

    /**
     * Achado A14, corrigido pela massa do segundo contrato. O identificador tem
     * codigo de empresa, matricula de 9 digitos e data. Tirar so a data e
     * remover zeros funcionava por acaso quando a empresa era "000".
     */
    static void identificadorTemCodigoDeEmpresa() {
        PagamentoDoLote itau = new PagamentoDoLote("-", "00100010196303072026", "-",
                "03/07/2026", "-", BigDecimal.ONE);
        ok("A14 . a matricula sao os 9 digitos antes da data",
                "101963".equals(itau.matricula()));
        ok("A14 . o que sobra na frente e o codigo da empresa",
                "001".equals(itau.codigoDaEmpresa()));
        ok("A14 . e a data sai formatada do proprio identificador",
                "03/07/2026".equals(itau.dataDoIdentificador()));

        PagamentoDoLote santander = new PagamentoDoLote("-", "00000010225306072026", "-",
                "06/07/2026", "-", BigDecimal.ONE);
        ok("A14 . o comprovante de empresa 000 continua dando a mesma matricula",
                "102253".equals(santander.matricula()));
    }

    /** A FOPAG e a folha estruturada: rubrica com codigo e coluna RESULTADOS. */
    static void fopagLeitaPorCodigo() throws Exception {
        TextoExtraido t = new ExtratorPdfBox().extrair(fopag());
        LeitorDeFopag leitor = new LeitorDeFopag();
        FolhaDeCompetencia f = leitor.ler(t);

        ok("FOPAG . a competencia sai do cabecalho", "06/2026".equals(f.competencia()));
        ok("FOPAG . um item por funcionario", f.itens().size() == 1);

        ItemDaFolha i = f.itens().get(0);
        ok("FOPAG . a rubrica traz o codigo, nao so a descricao",
                i.rubrica("00005").orElseThrow().descricao().equals("SALARIO"));
        ok("FOPAG . o desconto tambem", i.rubrica("08305").orElseThrow()
                .valor().compareTo(new BigDecimal("6.05")) == 0);
        ok("FOPAG . os totais vem da coluna RESULTADOS por codigo",
                i.totalProventos().compareTo(new BigDecimal("12000.00")) == 0
                        && i.liquido().compareTo(new BigDecimal("10000.00")) == 0);
        ok("FOPAG . a base do FGTS e o codigo 14000, nao um rotulo posicional",
                i.baseFgts().compareTo(new BigDecimal("12000.00")) == 0);
        ok("FOPAG . o custo total do vale alimentacao e o codigo 17300",
                i.resultado(LeitorDeFopag.CUSTO_TOTAL_VA)
                        .compareTo(new BigDecimal("604.80")) == 0);
        ok("FOPAG . custo total = custo da empresa mais a coparticipacao",
                i.resultado("17305").add(i.rubrica("08305").orElseThrow().valor())
                        .compareTo(i.resultado("17300")) == 0);

        Map<String, BigDecimal> resumo = leitor.resumoGeral(t);
        ok("FOPAG . o resumo geral e lido por codigo",
                resumo.get("10000").compareTo(new BigDecimal("12000.00")) == 0);
        ok("FOPAG . e o resumo nao vira um colaborador", f.itens().size() == 1);
    }

    /**
     * A mesma leitura serve a fornecedores diferentes porque nao depende do
     * layout: matricula no comeco da linha, valor no fim.
     */
    static void relacaoDeBeneficioPorMatricula() throws Exception {
        RelacaoDeBeneficio r = new LeitorDeRelacaoDeBeneficio()
                .ler(new ExtratorPdfBox().extrair(relacaoDeBeneficio(true)));

        ok("Relacao . os dois beneficiarios sao lidos", r.beneficiarios().size() == 2);
        ok("Relacao . a matricula sai sem os zeros a esquerda da folha",
                r.beneficiarios().get(0).matricula().equals("101963"));
        ok("Relacao . o CPF e lido quando a relacao o traz",
                "618.557.313-04".equals(r.beneficiarios().get(0).cpf()));
        ok("Relacao . a soma reproduz o total declarado", r.confere());

        RelacaoDeBeneficio semTotal = new LeitorDeRelacaoDeBeneficio()
                .ler(new ExtratorPdfBox().extrair(relacaoDeBeneficio(false)));
        ok("Relacao . relacao sem total declarado nao e dada por conferida",
                !semTotal.confere());
        ok("Relacao . e a recusa diz que a leitura nao pode ser verificada",
                semTotal.divergenciasComOTotal().get(0).contains("não pôde ser verificada"));
        ok("Relacao . a matricula com zeros da folha e a mesma da relacao",
                LeitorDeRelacaoDeBeneficio.semZeros("000101963").equals("101963"));
    }

    // -------------------------------------------------------------------------

    static ComprovanteEmLote lerLote(String rodape) throws Exception {
        TextoExtraido t = new ExtratorPdfBox().extrair(comprovanteEmLote(rodape));
        return new LeitorDeComprovanteEmLote().ler(t);
    }

    static String rodape(int compromissos, String valor) {
        return "Total Compromissos: " + compromissos + " Valor Total: R$ " + valor;
    }

    /**
     * Geometria medida em COMPROVANTE_PG_FOLHA_2.pdf: numeros na linha
     * principal em x 59..552, nome em x 245, 31 pontos entre registros e 6,5
     * dentro do registro.
     */
    static byte[] comprovanteEmLote(String rodape) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);

                // Texto corrido acima do cabecalho: e o que apaga as lacunas
                // quando a projecao e calculada sobre a pagina inteira.
                escrever(f, 37.2f, 600.3f,
                        "Clique sobre o titulo da respectiva coluna que deseja ordenar");
                escrever(f, 38.3f, 567.7f, "Número do Pagamento Número do Cliente Funcionário "
                        + "Data de Pagamento Tipo de Pagamento Valor R$");

                // Registro 1: nome em tres linhas.
                escrever(f, 245.5f, 541.6f, "DOUGLAS VINISIOS");
                linhaPrincipal(f, 535.1f, "900059648", "00000010225306072026", "2.232,21");
                escrever(f, 245.5f, 528.6f, "NUNES SOUZA");

                // Registro 2: tudo numa linha.
                escrever(f, 245.5f, 497.6f, "RONI DA SILVA ROSA");
                linhaPrincipal(f, 497.6f, "900059649", "00000010196806072026", "5.935,22");

                // Registro 3: nome em tres linhas outra vez.
                escrever(f, 245.5f, 466.6f, "ADILON SANTIAGO DA");
                linhaPrincipal(f, 460.1f, "900059650", "00000010206206072026", "7.152,39");
                escrever(f, 245.5f, 453.6f, "SILVA");

                if (rodape != null) {
                    escrever(f, 37.2f, 87.7f, rodape);
                }
                escrever(f, 37.2f, 60.0f, "Central de Atendimento Empresarial SAC");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    static void linhaPrincipal(PDPageContentStream f, float y, String pagamento,
                               String cliente, String valor) throws IOException {
        escrever(f, 59.4f, y, pagamento);
        escrever(f, 133.0f, y, cliente);
        escrever(f, 369.0f, y, "06/07/2026");
        escrever(f, 462.0f, y, "CC");
        escrever(f, 518.0f, y, valor);
    }

    static FolhaDeCompetencia lerFolha(boolean totaisCoerentes) throws Exception {
        TextoExtraido t = new ExtratorPdfBox().extrair(contracheques(totaisCoerentes));
        return new LeitorDeContracheque().ler(t);
    }

    /**
     * Geometria medida em CONTRACHEQUE.pdf: proventos a esquerda de x=272,
     * descontos a direita, valores alinhados a direita, e duas vias por pagina.
     */
    static byte[] contracheques(boolean totaisCoerentes) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String[] pessoa : new String[][] {
                    {"000101862", "ADRIANO LIN SOARES PERRUOLO", "052.190.471-40"},
                    {"000101864", "JAQUELINE DI CARLO ARAUJO DUARTE", "054.721.901-69"}}) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                doc.addPage(pagina);
                try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                    f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7);
                    // Achado A17: o mesmo recibo, duas vezes na pagina.
                    via(f, 740f, pessoa, totaisCoerentes);
                    via(f, 366f, pessoa, totaisCoerentes);
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    static void via(PDPageContentStream f, float topo, String[] pessoa, boolean coerente)
            throws IOException {
        boolean primeiro = pessoa[0].equals("000101862");
        escrever(f, 270.8f, topo - 12f, "Recibo de Pagamento");
        escrever(f, 272.0f, topo - 23f, "JUNHO/2026 MENSAL 1/1");
        escrever(f, 38.0f, topo - 50f, "Matrícula Nome");
        escrever(f, 38.0f, topo - 59f, pessoa[0] + " " + pessoa[1]);
        escrever(f, 38.0f, topo - 75f, "CPF Cargo/Nível");
        escrever(f, 38.0f, topo - 84f, pessoa[2] + " TESTADOR - PLENO /");
        // Os seis rotulos nas posicoes medidas no documento real: e a distancia
        // entre o primeiro e o segundo "Descrição" que divide as metades.
        escrever(f, 38.0f, topo - 132f, "Descrição");
        escrever(f, 179.0f, topo - 132f, "Qtde");
        escrever(f, 242.0f, topo - 132f, "Valor");
        escrever(f, 272.0f, topo - 132f, "Descrição");
        escrever(f, 413.0f, topo - 132f, "Qtde");
        escrever(f, 473.0f, topo - 132f, "Valor");

        // Linha 1: provento com quantidade a esquerda, desconto a direita.
        escrever(f, 38.0f, topo - 143f, "SALARIO");
        escrever(f, 169.0f, topo - 143f, "30,00");
        escrever(f, 218.0f, topo - 143f, primeiro ? "6.452,92" : "4.575,49");
        escrever(f, 272.0f, topo - 143f, "INSS MES");
        escrever(f, 458.0f, topo - 143f, primeiro ? "823,61" : "442,07");

        if (primeiro) {
            // Provento sem quantidade.
            escrever(f, 38.0f, topo - 154f, "DIF SALARIO MENSAL");
            escrever(f, 227.0f, topo - 154f, "847,87");
            escrever(f, 272.0f, topo - 154f, "IRRF MES");
            escrever(f, 458.0f, topo - 154f, "865,93");
            // Descontos sozinhos na metade da direita.
            escrever(f, 272.0f, topo - 165f, "TIT ASS ODT BRADESCO");
            escrever(f, 463.0f, topo - 165f, "11,49");
        }
        escrever(f, 272.0f, topo - 176f, "VALE ALIMENTACAO");
        escrever(f, 463.0f, topo - 176f, primeiro ? "64,89" : "48,62");

        escrever(f, 38.0f, topo - 272f, "TOTAL DE PROVENTOS");
        escrever(f, 218.0f, topo - 272f,
                primeiro ? (coerente ? "7.300,79" : "7.999,99") : "4.575,49");
        escrever(f, 272.0f, topo - 272f, "TOTAL DE DESCONTOS");
        escrever(f, 448.0f, topo - 272f, primeiro ? "1.765,92" : "490,69");

        escrever(f, 272.0f, topo - 298f, "LÍQUIDO A RECEBER");
        escrever(f, 450.0f, topo - 298f, primeiro ? "5.534,87" : "4.084,80");

        escrever(f, 38.0f, topo - 313f, "Salário Contratual");
        escrever(f, 132.0f, topo - 313f, "Sal. Contrib. INSS");
        escrever(f, 225.0f, topo - 313f, "Base Cálc. FGTS");
        escrever(f, 319.0f, topo - 313f, "FGTS Mês");
        escrever(f, 412.0f, topo - 313f, "Base Cálc. IRRF");
        escrever(f, 57.2f, topo - 322f, primeiro ? "6.452,92" : "4.575,49");
        escrever(f, 151.0f, topo - 322f, primeiro ? "7.300,79" : "4.575,49");
        escrever(f, 244.0f, topo - 322f, primeiro ? "7.300,79" : "4.575,49");
        escrever(f, 348.0f, topo - 322f, primeiro ? "584,06" : "366,04");
        escrever(f, 432.0f, topo - 322f, primeiro ? "6.477,18" : "3.968,29");
    }

    /** Recibo em duas folhas: "Continua..." na 1/2 e os totais na 2/2. */
    static byte[] contrachequeComContinuacao() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int folha = 1; folha <= 2; folha++) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                doc.addPage(pagina);
                try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                    f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7);
                    escrever(f, 270.8f, 736.0f, "Recibo de Pagamento");
                    escrever(f, 272.0f, 717.0f, "JUNHO/2026 MENSAL " + folha + "/2");
                    escrever(f, 38.0f, 690.0f, "Matrícula Nome");
                    escrever(f, 38.0f, 681.0f, "000100884 OZIAS ALVES DE LIMA JUNIOR");
                    escrever(f, 38.0f, 665.0f, "CPF Cargo/Nível");
                    escrever(f, 38.0f, 656.0f, "058.507.653-79 DESENVOLVEDOR /");
                    escrever(f, 38.0f, 608.0f, "Descrição");
                    escrever(f, 179.0f, 608.0f, "Qtde");
                    escrever(f, 242.0f, 608.0f, "Valor");
                    escrever(f, 272.0f, 608.0f, "Descrição");
                    escrever(f, 413.0f, 608.0f, "Qtde");
                    escrever(f, 473.0f, 608.0f, "Valor");
                    if (folha == 1) {
                        escrever(f, 38.0f, 597.0f, "SALARIO");
                        escrever(f, 218.0f, 597.0f, "4.000,00");
                        escrever(f, 272.0f, 597.0f, "INSS MES");
                        escrever(f, 458.0f, 597.0f, "1.000,00");
                        escrever(f, 38.0f, 586.0f, "Continua...");
                        escrever(f, 272.0f, 586.0f, "Continua...");
                        // A folha 1/2 imprime o rótulo do líquido, sem valor.
                        escrever(f, 272.0f, 442.0f, "LÍQUIDO A RECEBER");
                    } else {
                        escrever(f, 38.0f, 597.0f, "FERIAS 1 OCORRENCIA");
                        escrever(f, 218.0f, 597.0f, "5.000,00");
                        escrever(f, 38.0f, 469.0f, "TOTAL DE PROVENTOS");
                        escrever(f, 218.0f, 469.0f, "9.000,00");
                        escrever(f, 272.0f, 469.0f, "TOTAL DE DESCONTOS");
                        escrever(f, 448.0f, 469.0f, "1.000,00");
                        escrever(f, 272.0f, 442.0f, "LÍQUIDO A RECEBER");
                        escrever(f, 450.0f, 442.0f, "8.000,00");
                    }
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    /** Geometria medida na FOPAG real: proventos | descontos | resultados. */
    static byte[] fopag() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);
                escrever(f, 23.6f, 552.0f, "RELAÇÃO DA FOLHA DE PAGAMENTO Pág: 0001");
                escrever(f, 372.8f, 544.0f, "Mês: JUNHO/2026");
                escrever(f, 23.6f, 536.0f, "Folha: MENSAL");
                escrever(f, 23.6f, 512.8f, "FUNCIONÁRIO ADMISSÃO SITUAÇÃO DATA INÍCIO FOLHA");
                escrever(f, 23.6f, 505.1f,
                        "000101963 - HERMES LIMA DE OLIVEIRA 13/01/2026 ATIVIDADE NORMAL MENSAL");
                linhaDaFopag(f, 460.7f, "00005 SALARIO 30,00 9.793,14",
                        "07200 INSS MES 988,07", "10000 TOTAL PROVENTOS 12.000,00");
                linhaDaFopag(f, 453.5f, "02420 CRED CESTA BASICA 110,51",
                        "08305 VALE ALIMENTACAO 6,05", "10100 TOTAL DESCONTOS 2.000,00");
                linhaDaFopag(f, 446.3f, null, null, "10200 LIQUIDO A RECEBER 10.000,00");
                linhaDaFopag(f, 439.1f, null, null, "14000 BASE FGTS MES 12.000,00");
                linhaDaFopag(f, 431.9f, null, null, "17300 CUST TT VL ALIME 604,80");
                linhaDaFopag(f, 424.7f, null, null, "17305 CUST EMP VL ALIME 598,75");
            }
            PDPage resumo = new PDPage(PDRectangle.A4);
            doc.addPage(resumo);
            try (PDPageContentStream f = new PDPageContentStream(doc, resumo)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);
                escrever(f, 23.6f, 552.0f, "RELAÇÃO DA FOLHA DE PAGAMENTO Pág: 0002");
                escrever(f, 23.6f, 525.2f, "RESUMO GERAL");
                escrever(f, 23.6f, 480.2f, "10000 TOTAL PROVENTOS 0001 12.000,00 12.000,00");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    static void linhaDaFopag(PDPageContentStream f, float y, String provento,
                             String desconto, String resultado) throws IOException {
        if (provento != null) {
            escrever(f, 22.9f, y, provento);
        }
        if (desconto != null) {
            escrever(f, 268.0f, y, desconto);
        }
        if (resultado != null) {
            escrever(f, 512.0f, y, resultado);
        }
    }

    /** Relação de benefício: matrícula no começo da linha, valor no fim. */
    static byte[] relacaoDeBeneficio(boolean comTotal) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7);
                escrever(f, 44.1f, 590.0f, "MATRÍCULA COLABORADOR CPF ALIMENTAÇÃO");
                escrever(f, 44.1f, 576.0f,
                        "101963 HERMES LIMA DE OLIVEIRA 618.557.313-04 R$ 604,80");
                escrever(f, 44.1f, 565.0f,
                        "100787 SIDHARTHA BEZERRA DE SOUZA 003.684.063-77 R$ 460,80");
                if (comTotal) {
                    escrever(f, 465.3f, 518.0f, "SUBTOTAL R$ 1.065,60");
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    static void escrever(PDPageContentStream f, float x, float y, String texto)
            throws IOException {
        f.beginText();
        f.newLineAtOffset(x, y);
        f.showText(texto);
        f.endText();
    }

    /**
     * Roda um teste isolando a sua falha.
     *
     * <p>Sem isto, uma excecao dentro de um teste derruba a suite inteira e
     * esconde o resultado de todos os outros — que foi exatamente o que
     * aconteceu ao verificar a trava de divergencia entre a carga e o seed.
     */
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

    private TestesDeDocumento() {}
}
