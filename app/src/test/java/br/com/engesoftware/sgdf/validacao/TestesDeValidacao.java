package br.com.engesoftware.sgdf.validacao;

import br.com.engesoftware.sgdf.extracao.CampoExtraido;
import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.extracao.LocalizadorDeCampos;
import br.com.engesoftware.sgdf.extracao.PadraoDeCampo;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Testes da validacao V8 — completude de campos essenciais.
 *
 * <p>O caso real que a motivou (achado A12) esta reproduzido no fixture: o
 * comprovante do Itau que traz so os rotulos, sem valor nenhum.
 */
public final class TestesDeValidacao {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    static final List<CampoEssencial> DO_COMPROVANTE = List.of(
            new CampoEssencial("valor", FormatoDeCampo.VALOR,
                    "sem valor o comprovante nao pode ser pareado com a guia (regra R01)"),
            new CampoEssencial("data_pagamento", FormatoDeCampo.DATA,
                    "sem data nao se verifica se o pagamento ocorreu ate o vencimento"));

    public static void main(String[] args) throws Exception {
        executar("formatoRecusaCasamentoParcial", TestesDeValidacao::formatoRecusaCasamentoParcial);
        executar("documentoCompletoAprova", TestesDeValidacao::documentoCompletoAprova);
        executar("campoAusenteReprova", TestesDeValidacao::campoAusenteReprova);
        executar("campoMalFormadoReprova", TestesDeValidacao::campoMalFormadoReprova);
        executar("semCadastroNaoReprova", TestesDeValidacao::semCadastroNaoReprova);
        executar("motivoEObrigatorio", TestesDeValidacao::motivoEObrigatorio);
        executar("comprovanteVazioReal", TestesDeValidacao::comprovanteVazioReal);
        executar("comprovantePreenchidoReal", TestesDeValidacao::comprovantePreenchidoReal);
        executar("cnpjNasFormasReais", TestesDeValidacao::cnpjNasFormasReais);
        executar("titularidadeAceitaRaizEMascara", TestesDeValidacao::titularidadeAceitaRaizEMascara);
        executar("titularidadeReprovaOutraEmpresa", TestesDeValidacao::titularidadeReprovaOutraEmpresa);
        executar("titularidadeRegistraEvidenciaParcial",
                TestesDeValidacao::titularidadeRegistraEvidenciaParcial);
        executar("dataPorExtenso", TestesDeValidacao::dataPorExtenso);

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    /**
     * O formato casa o valor INTEIRO. Aceitar "casa em algum lugar" faria
     * "06/07/2026 CC" passar por data — que e exatamente o erro que um recorte
     * de coluna mal calibrado produz.
     */
    static void formatoRecusaCasamentoParcial() {
        ok("V8 . o formato aceita o valor bem formado",
                FormatoDeCampo.DATA.aceita("06/07/2026"));
        ok("V8 . e recusa o mesmo valor com sujeira da coluna vizinha",
                !FormatoDeCampo.DATA.aceita("06/07/2026 CC"));
        ok("V8 . valor com separador de milhar e aceito",
                FormatoDeCampo.VALOR.aceita("60.363,05"));
        ok("V8 . valor sem centavos nao e valor",
                !FormatoDeCampo.VALOR.aceita("60.363"));
        ok("V8 . nulo nao passa por nenhum formato",
                !FormatoDeCampo.CPF.aceita(null));
    }

    static void documentoCompletoAprova() {
        ResultadoDeValidacao r = new ValidacaoDeCompletude().validar(DO_COMPROVANTE,
                Map.of("valor", "2.232,21", "data_pagamento", "06/07/2026"));

        ok("V8 . documento com todos os campos essenciais e aprovado", r.aprovado());
        ok("V8 . aprovacao nao carrega detalhe de falha", r.detalhe().isEmpty());
    }

    static void campoAusenteReprova() {
        ResultadoDeValidacao r = new ValidacaoDeCompletude().validar(DO_COMPROVANTE,
                Map.of("data_pagamento", "06/07/2026"));

        ok("V8 . campo essencial ausente reprova o documento", r.reprovado());
        ok("V8 . o motivo nomeia o campo que faltou", r.motivo().contains("valor"));
        ok("V8 . e diz por que ele era indispensavel",
                r.motivo().contains("pareado com a guia"));
        ok("V8 . o detalhe registra o campo ausente para auditoria",
                r.detalhe().get("ausentes").equals(List.of("valor")));
        ok("V8 . a mensagem diz quantos de quantos faltaram",
                r.motivo().contains("1 de 2"));
    }

    static void campoMalFormadoReprova() {
        ResultadoDeValidacao r = new ValidacaoDeCompletude().validar(DO_COMPROVANTE,
                Map.of("valor", "R$", "data_pagamento", "06/07/2026"));

        ok("V8 . campo presente mas fora de formato tambem reprova", r.reprovado());
        ok("V8 . o motivo mostra o valor recusado e o esperado",
                r.motivo().contains("\"R$\"") && r.motivo().contains("valor decimal"));
        ok("V8 . campo mal formado nao e contado como ausente",
                !r.detalhe().containsKey("ausentes"));
    }

    /**
     * Cap. 1, principio 1: o sistema nao trava faturamento por falta de
     * configuracao propria. Mas o resultado precisa ficar registrado, ou
     * ninguem descobre que o tipo esta passando sem ser conferido.
     */
    static void semCadastroNaoReprova() {
        ResultadoDeValidacao r = new ValidacaoDeCompletude().validar(List.of(), Map.of());

        ok("V8 . tipo sem campos essenciais declarados nao e reprovado", !r.reprovado());
        ok("V8 . mas o resultado e NAO_APLICAVEL, nao aprovado",
                r.veredito() == Veredito.NAO_APLICAVEL);
        ok("V8 . e o motivo avisa que V8 nao protege esse tipo",
                r.motivo().contains("não confere completude"));
    }

    static void motivoEObrigatorio() {
        ok("V8 . campo essencial sem motivo e recusado no cadastro",
                recusa(() -> new CampoEssencial("valor", FormatoDeCampo.VALOR, "  ")));
        ok("V8 . reprovacao sem motivo nao e auditavel",
                recusa(() -> new ResultadoDeValidacao("V8", Veredito.REPROVADO, null, Map.of())));
    }

    /**
     * Achado A12 reproduzido: SISPAG SALARIOS com os rotulos e nenhum valor.
     * Passa em V1 (antivirus e MIME), passa em V2 (tem texto nativo) e seria
     * anexado a uma exigencia como prova de pagamento.
     */
    static void comprovanteVazioReal() throws Exception {
        ResultadoDeValidacao r = validarComprovante(comprovanteItau(false));

        ok("A12 . o comprovante so com rotulos tem texto legivel",
                new ExtratorPdfBox().extrair(comprovanteItau(false))
                        .textoCompleto().contains("Valor:"));
        ok("A12 . mas V8 o reprova por falta de conteudo", r.reprovado());
        ok("A12 . e a reprovacao aponta os dois campos que faltaram",
                r.detalhe().get("ausentes").size() == 2);
    }

    /** O mesmo layout, agora preenchido: V8 nao pode reprovar documento bom. */
    static void comprovantePreenchidoReal() throws Exception {
        ResultadoDeValidacao r = validarComprovante(comprovanteItau(true));
        ok("A12 . o mesmo layout preenchido e aprovado", r.aprovado());
    }

    static final String EMPRESA = "00.681.946/0001-60";

    /** As tres formas que a massa real trouxe, e que a igualdade de string recusa. */
    static void cnpjNasFormasReais() {
        ok("A6 . o CNPJ truncado na raiz e reconhecido",
                Cnpj.todosNo("cpf/cnpj do empregador 00.681.946 engesoftware")
                        .equals(List.of("00681946")));
        ok("A7 . o CNPJ mascarado e reconhecido",
                Cnpj.todosNo("cpf/cnpj: **.681.946/0001-**").size() == 1);
        ok("A16 . o CNPJ com separador quebrado e reconhecido",
                Cnpj.todosNo("flash tecnologia ? cnpj 32.223.020. 0001-18")
                        .equals(List.of("32223020000118")));
        ok("Cnpj . a raiz sao os oito primeiros digitos",
                Cnpj.raiz("00.681.946/0001-60").equals("00681946"));
        ok("Cnpj . o mascarado e marcado como mascarado",
                Cnpj.mascarado("**.681.946/0001-**") && !Cnpj.mascarado(EMPRESA));
        ok("Cnpj . a raiz sozinha nao e CNPJ completo",
                !Cnpj.completo("00.681.946") && Cnpj.completo(EMPRESA));
    }

    static void titularidadeAceitaRaizEMascara() {
        ValidacaoDeTitularidade v = new ValidacaoDeTitularidade();

        ok("V3 . guia com o CNPJ truncado na raiz e aprovada",
                v.validar("gfd - guia do fgts digital 00.681.946 engesoftware", EMPRESA)
                        .aprovado());
        ok("V3 . comprovante com o CNPJ mascarado e aprovado",
                v.validar("pagador: cpf/cnpj: **.681.946/0001-**", EMPRESA).aprovado());
        ok("V3 . e o CNPJ completo tambem, claro",
                v.validar("nome: engesoftware cnpj: 00.681.946/0001-60", EMPRESA).aprovado());

        // O CNPJ de terceiro no documento nao reprova: a relacao da Flash traz o
        // da propria Flash no rodape, e o comprovante do SICOOB o do destinatario.
        ok("V3 . CNPJ de terceiro no documento nao reprova",
                v.validar("engesoftware 00.681.946/0001-60 ... flash 32.223.020/0001-18",
                        EMPRESA).aprovado());
    }

    static void titularidadeReprovaOutraEmpresa() {
        ValidacaoDeTitularidade v = new ValidacaoDeTitularidade();

        ResultadoDeValidacao outra = v.validar("cnpj: 32.223.020/0001-18", EMPRESA);
        ok("V3 . documento de outra empresa e reprovado", outra.reprovado());
        ok("V3 . e o motivo mostra o esperado e o encontrado",
                outra.motivo().contains("00.681.946/0001-60")
                        && outra.motivo().contains("32.223.020/0001-18"));

        ok("V3 . documento sem nenhum CNPJ e reprovado",
                v.validar("comprovante sem identificacao nenhuma", EMPRESA).reprovado());
        ok("V3 . mascara nao deixa passar raiz diferente",
                v.validar("cnpj: **.360.305/0001-**", EMPRESA).reprovado());
        ok("V3 . sem CNPJ cadastrado na exigencia, V3 nao se aplica",
                v.validar("cnpj: 00.681.946/0001-60", null).veredito()
                        == Veredito.NAO_APLICAVEL);

        // Exigencia por estabelecimento: a raiz deixa de bastar, e a decisao e
        // do cadastro, nao da validacao.
        ValidacaoDeTitularidade porEstabelecimento = new ValidacaoDeTitularidade(true);
        ok("V3 . por estabelecimento, so a raiz nao basta",
                porEstabelecimento.validar("00.681.946 engesoftware", EMPRESA).reprovado());
        ok("V3 . mas o CNPJ completo continua bastando",
                porEstabelecimento.validar("00.681.946/0001-60", EMPRESA).aprovado());
    }

    /** Aprovar sobre dígito escondido é certo, mas tem de ficar no registro. */
    static void titularidadeRegistraEvidenciaParcial() {
        ValidacaoDeTitularidade v = new ValidacaoDeTitularidade();

        ResultadoDeValidacao mascarado = v.validar("cpf/cnpj: **.681.946/0001-**", EMPRESA);
        ok("V3 . aprovacao sobre CNPJ mascarado registra evidencia parcial",
                mascarado.detalhe().containsKey("evidencia_parcial"));
        ok("V3 . e diz que a titularidade veio de digitos escondidos",
                mascarado.detalhe().get("evidencia_parcial").get(0).contains("escondidos"));

        ResultadoDeValidacao raiz = v.validar("00.681.946 engesoftware", EMPRESA);
        ok("V3 . aprovacao sobre a raiz registra que nao prova o estabelecimento",
                raiz.detalhe().get("evidencia_parcial").get(0).contains("estabelecimento"));

        ResultadoDeValidacao completo = v.validar("cnpj: 00.681.946/0001-60", EMPRESA);
        ok("V3 . CNPJ completo nao carrega ressalva",
                !completo.detalhe().containsKey("evidencia_parcial"));
    }

    /**
     * Achado A25: a certidao negativa do GDF escreve "Valida ate 27 de agosto
     * de 2026" e traz, no mesmo documento, a data numerica 04/07/2003 — do
     * decreto que a ampara. Pegar "a primeira data numerica do documento" daria
     * uma certidao vencida ha vinte anos.
     */
    static void dataPorExtenso() {
        String certidao = "Certidão expedida conforme Decreto Distrital nº 23.873 de "
                + "04/07/2003, gratuitamente. Válida até 27 de agosto de 2026. *";

        ok("A25 . a data por extenso e reconhecida",
                java.time.LocalDate.of(2026, 8, 27)
                        .equals(Datas.depoisDe(certidao, "Válida até")));
        ok("A25 . e a primeira data NUMERICA do documento e a do decreto",
                Datas.NUMERICA.matcher(certidao).results().findFirst()
                        .orElseThrow().group().equals("04/07/2003"));
        ok("A25 . ancorar no rotulo e o que separa uma da outra",
                !java.time.LocalDate.of(2003, 7, 4)
                        .equals(Datas.depoisDe(certidao, "Válida até")));

        ok("A25 . a forma numerica continua sendo lida",
                java.time.LocalDate.of(2026, 11, 25)
                        .equals(Datas.depoisDe("Validade: 25/11/2026", "Validade:")));
        ok("A25 . rotulo ausente devolve nulo, nao a primeira data que aparecer",
                Datas.depoisDe(certidao, "Vigência até") == null);
        ok("A25 . 31 de fevereiro nao e data",
                Datas.primeira("31 de fevereiro de 2026") == null);
        ok("A25 . o formato de cadastro aceita a forma por extenso",
                FormatoDeCampo.DATA_POR_EXTENSO.aceita("27 de agosto de 2026")
                        && !FormatoDeCampo.DATA_POR_EXTENSO.aceita("27/08/2026"));
    }

    // -------------------------------------------------------------------------

    static ResultadoDeValidacao validarComprovante(byte[] pdf) throws IOException {
        TextoExtraido texto = new ExtratorPdfBox().extrair(pdf);
        Map<String, String> extraidos = new LinkedHashMap<>();
        // Os padroes casam o valor DEPOIS do rotulo: e o que distingue
        // "Valor: 2.232,21" de "Valor:" sozinho.
        colher(extraidos, texto, PadraoDeCampo.de("valor",
                "valor:\\s*r?\\$?\\s*(\\d{1,3}(?:\\.\\d{3})*,\\d{2})", 1));
        colher(extraidos, texto, PadraoDeCampo.de("data_pagamento",
                "data do pagamento:\\s*(\\d{2}/\\d{2}/\\d{4})", 1));
        return new ValidacaoDeCompletude().validar(DO_COMPROVANTE, extraidos);
    }

    static void colher(Map<String, String> destino, TextoExtraido texto, PadraoDeCampo padrao) {
        CampoExtraido c = LocalizadorDeCampos.primeiro(texto, padrao, 1.0);
        if (c != null) {
            destino.put(padrao.nome(), c.valor());
        }
    }

    /** Layout medido em COMPROVANTE_PG_FOLHA_6.pdf. */
    static byte[] comprovanteItau(boolean comValores) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                escrever(f, 151.6f, 697.8f, "SISPAG SALARIOS");
                escrever(f, 67.2f, 667.3f, "Nome da empresa:"
                        + (comValores ? " ENGESOFTWARE TECNOLOGIA S/A" : ""));
                escrever(f, 111.8f, 651.5f, "Agencia:" + (comValores ? " 4515" : "")
                        + "  Conta corrente:" + (comValores ? " 130048445" : ""));
                escrever(f, 121.1f, 618.8f, "Nome:" + (comValores ? " RONI DA SILVA ROSA" : ""));
                escrever(f, 124.3f, 587.2f, "Valor:" + (comValores ? " 5.935,22" : ""));
                escrever(f, 124.3f, 573.4f, "Data do pagamento:"
                        + (comValores ? " 06/07/2026" : ""));
                escrever(f, 18.6f, 515.4f, "0BDD36626EF6D436B3E13B41525B3F7A7E9C01AE");
                escrever(f, 18.4f, 61.4f, "Em caso de duvidas, de posse do comprovante, "
                        + "contate seu gerente ou a Central no 40901685.");
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

    static boolean recusa(Runnable acao) {
        try {
            acao.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
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

    private TestesDeValidacao() {}
}
