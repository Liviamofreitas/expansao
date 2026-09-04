package br.com.engesoftware.sgdf.extracao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * Testes do leitor de tabela por coordenadas.
 *
 * <p>O layout reproduzido e o do contracheque real: proventos a esquerda,
 * descontos a direita, e linhas em que so ha desconto. Lido linearmente, um
 * desconto de beneficio vira provento — e o liquido deixa de fechar.
 */
public final class TestesDeTabela {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        agrupamentoEmLinhas();
        colunasPorProjecao();
        linhaSoComDesconto();
        descricaoMaisLargaQueORotulo();
        reconstrucaoDeEspacos();
        blocosPorLacunaVertical();
        leituraPorBloco();
        campoAbaixoDoRotulo();
        campoNumericoAlinhadoADireita();
        rotuloSozinhoNaLinha();

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    static void agrupamentoEmLinhas() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);

        ok("Tabela . as linhas visuais sao agrupadas por coordenada Y",
                linhas.size() == 5);
        ok("Tabela . a primeira linha e o cabecalho",
                linhas.get(0).texto().startsWith("Descricao"));
        ok("Tabela . dentro da linha os glifos vem da esquerda para a direita",
                linhas.get(1).texto().startsWith("SALARIO"));
    }

    static void colunasPorProjecao() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        List<Coluna> colunas = LeitorDeTabela.colunasPorProjecao(
                linhas.subList(1, linhas.size()), 6,
                List.of("prov", "prov_valor", "desc", "desc_valor"));

        ok("Tabela . a projecao encontra as quatro colunas", colunas.size() == 4);

        List<Map<String, String>> dados =
                LeitorDeTabela.lerAbaixoDe(linhas, 0, colunas, null);
        ok("Tabela . todas as linhas de dados sao lidas", dados.size() == 4);
        ok("Tabela . a primeira linha tem provento e desconto",
                dados.get(0).get("prov").equals("SALARIO")
                        && dados.get(0).get("prov_valor").equals("6.452,92")
                        && dados.get(0).get("desc").equals("INSS MES")
                        && dados.get(0).get("desc_valor").equals("823,61"));
    }

    /**
     * O caso que motivou tudo. No contracheque real, "TIT ASS ODT BRADESCO" e
     * "VALE ALIMENTACAO" aparecem sozinhos na linha, na coluna da direita. Lidos
     * linearmente pareceriam comecar na coluna de proventos.
     */
    static void linhaSoComDesconto() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        List<Coluna> colunas = LeitorDeTabela.colunasPorProjecao(
                linhas.subList(1, linhas.size()), 6,
                List.of("prov", "prov_valor", "desc", "desc_valor"));
        List<Map<String, String>> dados =
                LeitorDeTabela.lerAbaixoDe(linhas, 0, colunas, null);

        Map<String, String> terceira = dados.get(2);
        ok("Tabela . linha so com desconto nao inventa provento",
                terceira.get("prov").isBlank() && terceira.get("prov_valor").isBlank());
        ok("Tabela . e o desconto vai para a coluna certa",
                terceira.get("desc").equals("TIT ASS ODT BRADESCO")
                        && terceira.get("desc_valor").equals("11,49"));

        Map<String, String> quarta = dados.get(3);
        ok("Tabela . o vale alimentacao tambem e desconto, nao provento",
                quarta.get("prov").isBlank()
                        && quarta.get("desc").equals("VALE ALIMENTACAO"));
    }

    /**
     * A descricao e mais larga que o rotulo "Descricao" que a encabeca. Derivar
     * a fronteira do cabecalho truncaria — foi o que aconteceu na primeira
     * versao, que devolvia "TIT ASS ODT BRADES".
     */
    static void descricaoMaisLargaQueORotulo() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        List<Coluna> colunas = LeitorDeTabela.colunasPorProjecao(
                linhas.subList(1, linhas.size()), 6,
                List.of("prov", "prov_valor", "desc", "desc_valor"));
        List<Map<String, String>> dados =
                LeitorDeTabela.lerAbaixoDe(linhas, 0, colunas, null);

        ok("Tabela . descricao mais larga que o rotulo nao e truncada",
                dados.get(2).get("desc").length() == "TIT ASS ODT BRADESCO".length());
    }

    /**
     * O PDF nao desenha espacos: a separacao entre palavras e a lacuna entre
     * glifos. Sem reconstrui-la, "VALE ALIMENTACAO" viraria "VALEALIMENTACAO".
     */
    static void reconstrucaoDeEspacos() throws Exception {
        byte[] pdf = tabela();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pg);
        ok("Tabela . o espaco entre palavras e reconstruido pela lacuna",
                linhas.get(4).texto().contains("VALE ALIMENTACAO"));
    }

    /**
     * A relacao de beneficios da Flash nao tem uma linha por registro: cada
     * beneficiario ocupa seis linhas visuais, e o que separa um do proximo e a
     * lacuna vertical — 39 pontos entre blocos, 3 a 7 dentro do bloco.
     */
    static void blocosPorLacunaVertical() throws Exception {
        List<LinhaVisual> linhas = linhasDaRelacao();
        List<List<LinhaVisual>> blocos = LeitorDeTabela.agruparEmBlocos(linhas, 20f);

        ok("Bloco . a lacuna vertical separa os dois beneficiarios",
                blocos.size() == 2);
        ok("Bloco . e cada beneficiario mantem suas seis linhas",
                blocos.get(0).size() == 6 && blocos.get(1).size() == 6);
        ok("Bloco . as linhas do primeiro bloco vem de cima para baixo",
                blocos.get(0).get(0).y() > blocos.get(0).get(5).y());

        // Se a lacuna exigida passar da distancia entre blocos, os dois
        // registros se fundem — e um CPF acabaria colado no nome do outro.
        List<List<LinhaVisual>> fundidos = LeitorDeTabela.agruparEmBlocos(linhas, 45f);
        ok("Bloco . lacuna maior que a distancia real funde os registros",
                fundidos.size() == 1);
    }

    /**
     * O caso que motivou a leitura por bloco. Na relacao real o CPF
     * "052.190.471-40" esta partido em duas linhas visuais, com o valor do
     * beneficio no meio; e o nome "JAQUELINE DI CARLO ARAUJO DUARTE" tambem
     * quebra. Lido linha a linha, nenhum dos dois se forma — e a chave de
     * juncao com a folha nunca aparece.
     */
    static void leituraPorBloco() throws Exception {
        List<LinhaVisual> linhas = linhasDaRelacao();
        List<Coluna> colunas = LeitorDeTabela.colunasPorProjecao(
                linhas, 10, List.of("nome", "cpf", "grupo", "valor", "total"));
        ok("Bloco . a projecao acha as cinco colunas da relacao",
                colunas.size() == 5);

        List<List<LinhaVisual>> blocos = LeitorDeTabela.agruparEmBlocos(linhas, 20f);
        Map<String, String> primeiro = LeitorDeTabela.lerBloco(blocos.get(0), colunas);
        Map<String, String> segundo = LeitorDeTabela.lerBloco(blocos.get(1), colunas);

        ok("Bloco . o CPF partido em duas linhas e reconstituido",
                primeiro.get("cpf").replace(" ", "").equals("052.190.471-40"));
        ok("Bloco . o nome de uma linha so nao ganha pedaco do vizinho",
                primeiro.get("nome").equals("ADRIANO LIN SOARES PERRUOLO"));
        ok("Bloco . o nome quebrado em duas linhas e reunido com espaco",
                segundo.get("nome").equals("JAQUELINE DI CARLO ARAUJO DUARTE"));
        ok("Bloco . e o CPF do segundo beneficiario e o dele",
                segundo.get("cpf").replace(" ", "").equals("054.721.901-69"));
        ok("Bloco . o valor total fica na coluna da direita",
                primeiro.get("total").equals("R$ 865,19")
                        && segundo.get("total").equals("R$ 865,19"));
    }

    /**
     * Achado A20: em layout tabular os rotulos vem todos primeiro e os valores
     * todos depois. Nenhuma janela de vizinhanca alcanca o valor sem atravessar
     * os outros rotulos.
     */
    static void campoAbaixoDoRotulo() throws Exception {
        PaginaExtraida pg = new ExtratorPdfBox().extrair(guiaComRotulos()).paginas().get(0);
        List<String> rotulos = List.of("CPF/CNPJ do Empregador", "Nome/Razao Social");

        CampoExtraido cnpj = ExtratorPorRotulo.valorAbaixoDe(pg, rotulos,
                "CPF/CNPJ do Empregador", "cnpj");
        CampoExtraido nome = ExtratorPorRotulo.valorAbaixoDe(pg, rotulos,
                "Nome/Razao Social", "nome");

        ok("A20 . o valor sai da coluna do seu rotulo",
                cnpj != null && cnpj.valor().equals("00.681.946"));
        ok("A20 . e o da coluna vizinha nao se mistura",
                nome != null && nome.valor().equals("ENGESOFTWARE TECNOLOGIA S/A"));
        ok("A20 . o campo extraido guarda de onde veio",
                cnpj.posicao() != null && !cnpj.posicao().retangulos().isEmpty());

        ok("A20 . rotulo que nao esta entre os declarados e erro de cadastro",
                recusa(() -> ExtratorPorRotulo.valorAbaixoDe(pg, rotulos, "Inexistente", "x")));
    }

    /**
     * Coluna numerica cresce para a ESQUERDA a partir da borda direita. Declarar
     * o alinhamento errado devolve o valor da coluna vizinha — na guia real,
     * 0,00 em vez de 119.301,51.
     */
    static void campoNumericoAlinhadoADireita() throws Exception {
        PaginaExtraida pg = new ExtratorPdfBox().extrair(guiaComRotulos()).paginas().get(0);
        List<String> rotulos = List.of("Competencia", "Encargos", "FGTS Total");

        CampoExtraido esquerda = ExtratorPorRotulo.valorAbaixoDe(pg, rotulos, "FGTS Total",
                "valor", List.of(LeitorDeTabela.Alinhamento.ESQUERDA,
                        LeitorDeTabela.Alinhamento.ESQUERDA,
                        LeitorDeTabela.Alinhamento.ESQUERDA));
        CampoExtraido direita = ExtratorPorRotulo.valorAbaixoDe(pg, rotulos, "FGTS Total",
                "valor", List.of(LeitorDeTabela.Alinhamento.ESQUERDA,
                        LeitorDeTabela.Alinhamento.DIREITA,
                        LeitorDeTabela.Alinhamento.DIREITA));

        ok("A20 . alinhamento a direita traz o valor da coluna certa",
                direita != null && direita.valor().endsWith("119.301,51"));
        ok("A20 . e a esquerda traria o da vizinha — por isso o cadastro declara",
                esquerda != null && !esquerda.valor().equals(direita.valor()));
    }

    /**
     * Rotulo sozinho na linha: a faixa e a extensao dele mesmo. Usar a linha
     * inteira faria o cabecalho do bloco seguinte, impresso entre o rotulo e o
     * seu valor, ser tomado como valor.
     */
    static void rotuloSozinhoNaLinha() throws Exception {
        PaginaExtraida pg = new ExtratorPdfBox().extrair(guiaComRotulos()).paginas().get(0);

        CampoExtraido venc = ExtratorPorRotulo.valorAbaixoDe(pg,
                List.of("Pagar este documento ate"), "Pagar este documento ate", "vencimento");

        ok("A20 . rotulo sozinho encontra o proprio valor",
                venc != null && venc.valor().equals("20/07/2026"));
        ok("A20 . e nao o cabecalho que fica no meio do caminho",
                venc != null && !venc.valor().contains("CPF"));
    }

    // -------------------------------------------------------------------------

    /** Layout de duas colunas, como o contracheque: proventos | descontos. */
    static byte[] tabela() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                escrever(f, 40, 700, "Descricao");
                escrever(f, 160, 700, "Valor");
                escrever(f, 300, 700, "Descricao");
                escrever(f, 430, 700, "Valor");

                escrever(f, 40, 685, "SALARIO");
                escrever(f, 160, 685, "6.452,92");
                escrever(f, 300, 685, "INSS MES");
                escrever(f, 430, 685, "823,61");

                escrever(f, 40, 670, "DIF SALARIO MENSAL");
                escrever(f, 160, 670, "847,87");
                escrever(f, 300, 670, "IRRF MES");
                escrever(f, 430, 670, "865,93");

                // Linha so com desconto — o caso que motivou o leitor.
                escrever(f, 300, 655, "TIT ASS ODT BRADESCO");
                escrever(f, 430, 655, "11,49");

                escrever(f, 300, 640, "VALE ALIMENTACAO");
                escrever(f, 430, 640, "64,89");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    /**
     * Reproduz a geometria da relacao de beneficios da Flash, com as
     * coordenadas medidas no documento real de 06/2026: o CPF na faixa
     * x 219..275 partido entre duas linhas, o nome a esquerda de 219, e 39
     * pontos de lacuna entre um beneficiario e o proximo.
     */
    static List<LinhaVisual> linhasDaRelacao() throws Exception {
        byte[] pdf = relacaoDeBeneficios();
        PaginaExtraida pg = new ExtratorPdfBox().extrair(pdf).paginas().get(0);
        return LeitorDeTabela.agruparEmLinhas(pg);
    }

    static byte[] relacaoDeBeneficios() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);

                // Beneficiario 1: nome inteiro numa linha, CPF partido em duas.
                escrever(f, 457.1f, 617.4f, "Beneficio");
                escrever(f, 219.6f, 610.7f, "052.190.471-");
                escrever(f, 357.6f, 610.7f, "Refeicao e");
                escrever(f, 454.2f, 610.7f, "de R$ 865,19)");
                escrever(f, 47.7f, 604.7f, "ADRIANO LIN SOARES PERRUOLO");
                escrever(f, 515.0f, 604.7f, "R$ 865,19");
                escrever(f, 448.1f, 601.7f, "Custo de conta");
                escrever(f, 242.7f, 598.7f, "40");
                escrever(f, 352.7f, 598.7f, "Alimentacao");
                escrever(f, 459.3f, 592.7f, "R$ 0,00");

                // 39 pontos de lacuna, e o beneficiario 2 — com o nome tambem
                // quebrado em duas linhas.
                escrever(f, 457.1f, 553.7f, "Beneficio");
                escrever(f, 56.6f, 546.9f, "JAQUELINE DI CARLO ARAUJO");
                escrever(f, 219.6f, 546.9f, "054.721.901-");
                escrever(f, 357.6f, 546.9f, "Refeicao e");
                escrever(f, 454.2f, 546.9f, "de R$ 865,19)");
                escrever(f, 515.0f, 540.9f, "R$ 865,19");
                escrever(f, 448.1f, 537.9f, "Custo de conta");
                escrever(f, 104.1f, 534.9f, "DUARTE");
                escrever(f, 242.7f, 534.9f, "69");
                escrever(f, 352.7f, 534.9f, "Alimentacao");
                escrever(f, 459.3f, 528.9f, "R$ 0,00");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    /**
     * Geometria medida em GFDGUIA_DO_FGTS.pdf: o rotulo "Pagar este documento
     * ate" sozinho a direita, com o cabecalho de outro bloco entre ele e o seu
     * valor, e a tabela de competencia com a coluna numerica a direita.
     */
    static byte[] guiaComRotulos() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream f = new PDPageContentStream(doc, pagina)) {
                f.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7);
                escrever(f, 465.9f, 738.0f, "Pagar este documento ate");
                escrever(f, 25.0f, 725.0f, "CPF/CNPJ do Empregador");
                escrever(f, 150.0f, 725.0f, "Nome/Razao Social");
                escrever(f, 468.1f, 720.3f, "20/07/2026");
                escrever(f, 88.8f, 711.5f, "00.681.946");
                escrever(f, 150.0f, 711.5f, "ENGESOFTWARE TECNOLOGIA S/A");

                escrever(f, 25.0f, 598.7f, "Competencia");
                escrever(f, 300.0f, 598.7f, "Encargos");
                escrever(f, 400.0f, 598.7f, "FGTS Total");
                escrever(f, 25.0f, 583.9f, "06/2026");
                escrever(f, 330.0f, 583.9f, "0,00");
                escrever(f, 440.0f, 583.9f, "119.301,51");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    static boolean recusa(Runnable acao) {
        try {
            acao.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    static void escrever(PDPageContentStream f, float x, float y, String texto)
            throws IOException {
        f.beginText();
        f.newLineAtOffset(x, y);
        f.showText(texto);
        f.endText();
    }

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    private TestesDeTabela() {}
}
