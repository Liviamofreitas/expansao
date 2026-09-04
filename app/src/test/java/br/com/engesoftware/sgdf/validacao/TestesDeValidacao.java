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
        formatoRecusaCasamentoParcial();
        documentoCompletoAprova();
        campoAusenteReprova();
        campoMalFormadoReprova();
        semCadastroNaoReprova();
        motivoEObrigatorio();
        comprovanteVazioReal();
        comprovantePreenchidoReal();

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
