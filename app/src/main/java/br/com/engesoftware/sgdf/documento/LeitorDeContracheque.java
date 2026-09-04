package br.com.engesoftware.sgdf.documento;

import br.com.engesoftware.sgdf.extracao.Coluna;
import br.com.engesoftware.sgdf.extracao.Glifo;
import br.com.engesoftware.sgdf.extracao.LeitorDeTabela;
import br.com.engesoftware.sgdf.extracao.LinhaVisual;
import br.com.engesoftware.sgdf.extracao.PaginaExtraida;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monta a folha da competência a partir do arquivo de contracheques.
 *
 * <p><b>Por que não lê por coluna.</b> O contracheque tem seis colunas
 * (descrição, quantidade, valor, duas vezes), mas a coluna de quantidade dos
 * descontos fica vazia na maioria das folhas. A projeção acha cinco separações
 * numa competência e seis em outra, e uma lista fixa de nomes quebra na
 * segunda. Aqui a leitura é por FORMA DO CONTEÚDO dentro de cada metade: o
 * último número com centavos é o valor, um número com centavos antes dele é a
 * quantidade, e o que sobra é a descrição. É imune à contagem de colunas.
 *
 * <p>A divisão entre as metades vem do cabeçalho, que é confiável para isso: o
 * segundo rótulo "Descrição" marca onde começam os descontos.
 *
 * <p><b>Duas vias por página</b> (achado A17): o mesmo recibo é impresso duas
 * vezes. Somar sem deduplicar dobra o líquido de toda a folha.
 */
public final class LeitorDeContracheque {

    private static final Pattern MATRICULA_E_NOME = Pattern.compile("^(\\d{5,})\\s+(.+)$");
    private static final Pattern CPF = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    private static final Pattern DINHEIRO = Pattern.compile("\\d{1,3}(?:\\.\\d{3})*,\\d{2}");
    /**
     * "JUNHO/2026 MENSAL 1/2" — a última fração é a folha atual e o total de
     * folhas do MESMO recibo. Achado A21: o recibo de um colaborador com férias
     * não cabe numa página, e a continuação traz os totais.
     */
    private static final Pattern FLS = Pattern.compile(
            "(?:JANEIRO|FEVEREIRO|MARÇO|ABRIL|MAIO|JUNHO|JULHO|AGOSTO|SETEMBRO|OUTUBRO"
                    + "|NOVEMBRO|DEZEMBRO)/\\d{4}\\s+\\S+\\s+(\\d+)/(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REFERENCIA = Pattern.compile(
            "(JANEIRO|FEVEREIRO|MARÇO|ABRIL|MAIO|JUNHO|JULHO|AGOSTO|SETEMBRO|OUTUBRO"
                    + "|NOVEMBRO|DEZEMBRO)/(\\d{4})", Pattern.CASE_INSENSITIVE);

    private static final List<String> MESES = List.of("JANEIRO", "FEVEREIRO", "MARÇO",
            "ABRIL", "MAIO", "JUNHO", "JULHO", "AGOSTO", "SETEMBRO", "OUTUBRO",
            "NOVEMBRO", "DEZEMBRO");

    /**
     * Cada via começa no título do recibo, não no cabeçalho "Matrícula Nome".
     *
     * <p>A distinção importa: o número da folha ("JUNHO/2026 MENSAL 1/2") é
     * impresso ACIMA do cabeçalho de matrícula. Recortar a via a partir dele
     * deixava o número de fora, e a continuação da folha 2/2 era descartada
     * como se fosse a via repetida — perdendo metade dos proventos de quem
     * teve férias no mês (achado A21).
     */
    private static final String INICIO_DA_VIA = "Recibo de Pagamento";

    private static final String CABECALHO_DA_MATRICULA = "Matr";
    private static final String CABECALHO_DAS_RUBRICAS = "Descri";
    private static final String TOTAIS = "TOTAL DE PROVENTOS";
    private static final String LIQUIDO = "QUIDO A RECEBER";
    private static final String BASES = "Base C";

    /**
     * Rodapé de bases. Os rótulos são alinhados à esquerda e cada valor fica
     * sob o seu, então aqui o cabeçalho é a fonte certa das fronteiras.
     */
    private static final List<String> ROTULOS_DAS_BASES = List.of(
            "Sal", "Sal. Contrib. INSS", "Base C", "FGTS M", "Base C");
    private static final List<String> NOMES_DAS_BASES = List.of(
            "salario_contratual", "base_inss", "base_fgts", "fgts_mes", "base_irrf");

    public FolhaDeCompetencia ler(TextoExtraido texto) {
        // Chave: matrícula + número da folha. Distingue as duas situações que
        // parecem a mesma coisa e não são — a VIA repetida (achado A17), que é
        // descartada, e a CONTINUAÇÃO em outra folha (achado A21), que é unida.
        Map<String, ItemDaFolha> porFolha = new LinkedHashMap<>();
        String competencia = competenciaDe(texto.textoCompleto());

        for (PaginaExtraida pagina : texto.paginas()) {
            List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pagina);
            for (List<LinhaVisual> via : separarVias(linhas)) {
                ItemDaFolha item = montar(via, competencia);
                if (item != null) {
                    porFolha.putIfAbsent(item.matricula() + "#" + folhaDaVia(via), item);
                }
            }
        }

        Map<String, ItemDaFolha> porMatricula = new LinkedHashMap<>();
        porFolha.forEach((chave, item) -> porMatricula.merge(
                item.matricula(), item, LeitorDeContracheque::unir));
        return new FolhaDeCompetencia(competencia, new ArrayList<>(porMatricula.values()));
    }

    /** Número da folha dentro do recibo; 1 quando o documento não declara. */
    private static int folhaDaVia(List<LinhaVisual> via) {
        for (LinhaVisual l : via) {
            Matcher m = FLS.matcher(l.texto());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 1;
    }

    /**
     * Une a continuação à folha anterior do mesmo recibo.
     *
     * <p>As rubricas se somam na ordem; os totais, o líquido e as bases vêm da
     * folha que os imprime — a última. Tomar o valor não nulo, e não o último
     * incondicionalmente, é o que impede que uma folha sem totais apague os que
     * a anterior trazia.
     */
    private static ItemDaFolha unir(ItemDaFolha a, ItemDaFolha b) {
        List<Rubrica> proventos = new ArrayList<>(a.proventos());
        proventos.addAll(b.proventos());
        List<Rubrica> descontos = new ArrayList<>(a.descontos());
        descontos.addAll(b.descontos());
        return new ItemDaFolha(a.matricula(), a.nome(), primeiro(a.cpf(), b.cpf()),
                a.competencia(), proventos, descontos,
                primeiro(b.totalProventos(), a.totalProventos()),
                primeiro(b.totalDescontos(), a.totalDescontos()),
                primeiro(b.liquido(), a.liquido()),
                primeiro(b.baseFgts(), a.baseFgts()),
                primeiro(b.fgtsMes(), a.fgtsMes()),
                primeiro(b.baseInss(), a.baseInss()),
                primeiro(b.baseIrrf(), a.baseIrrf()),
                a.resultados().isEmpty() ? b.resultados() : a.resultados());
    }

    private static <T> T primeiro(T preferido, T alternativo) {
        return preferido != null ? preferido : alternativo;
    }

    /** Cada recibo da página, do título ao começo do próximo. */
    private static List<List<LinhaVisual>> separarVias(List<LinhaVisual> linhas) {
        List<Integer> inicios = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).texto().contains(INICIO_DA_VIA)) {
                inicios.add(i);
            }
        }
        List<List<LinhaVisual>> vias = new ArrayList<>();
        for (int v = 0; v < inicios.size(); v++) {
            int fim = v + 1 < inicios.size() ? inicios.get(v + 1) : linhas.size();
            vias.add(linhas.subList(inicios.get(v), fim));
        }
        return vias;
    }

    private ItemDaFolha montar(List<LinhaVisual> via, String competencia) {
        int iMatricula = indiceDoCabecalhoDaMatricula(via);
        if (iMatricula < 0) {
            return null;
        }
        Matcher m = MATRICULA_E_NOME.matcher(depoisDe(via, iMatricula));
        if (!m.find()) {
            return null;
        }
        String matricula = m.group(1);
        String nome = m.group(2).trim();

        int iCpf = LeitorDeTabela.indiceDaLinhaCom(via, "CPF");
        String cpf = null;
        if (iCpf >= 0 && iCpf + 1 < via.size()) {
            Matcher mc = CPF.matcher(via.get(iCpf + 1).texto());
            if (mc.find()) {
                cpf = mc.group();
            }
        }

        int iRubricas = LeitorDeTabela.indiceDaLinhaCom(via, CABECALHO_DAS_RUBRICAS);
        if (iRubricas < 0) {
            return null;
        }
        float divisor = divisorDasMetades(via.get(iRubricas));

        List<Rubrica> proventos = new ArrayList<>();
        List<Rubrica> descontos = new ArrayList<>();
        BigDecimal totalProventos = null;
        BigDecimal totalDescontos = null;
        BigDecimal liquido = null;

        for (int i = iRubricas + 1; i < via.size(); i++) {
            LinhaVisual linha = via.get(i);
            String texto = linha.texto();
            if (texto.contains(TOTAIS)) {
                totalProventos = ultimoValor(linha.textoEntre(Float.NEGATIVE_INFINITY, divisor));
                totalDescontos = ultimoValor(linha.textoEntre(divisor, Float.POSITIVE_INFINITY));
                continue;
            }
            if (texto.contains(LIQUIDO)) {
                liquido = ultimoValor(texto);
                continue;
            }
            if (texto.contains(BASES)) {
                Map<String, BigDecimal> bases = lerBases(via, i);
                return new ItemDaFolha(matricula, nome, cpf, competencia,
                        proventos, descontos, totalProventos, totalDescontos, liquido,
                        bases.get("base_fgts"), bases.get("fgts_mes"),
                        bases.get("base_inss"), bases.get("base_irrf"));
            }
            acrescentar(proventos, linha.textoEntre(Float.NEGATIVE_INFINITY, divisor));
            acrescentar(descontos, linha.textoEntre(divisor, Float.POSITIVE_INFINITY));
        }
        return new ItemDaFolha(matricula, nome, cpf, competencia, proventos, descontos,
                totalProventos, totalDescontos, liquido, null, null, null, null);
    }

    /**
     * Onde começam os descontos: o segundo rótulo "Descrição" do cabeçalho.
     *
     * <p>O rótulo é confiável para ISTO — marcar o início de uma coluna alinhada
     * à esquerda — ainda que não sirva para deduzir a fronteira das colunas de
     * valor, que são alinhadas à direita.
     */
    private static float divisorDasMetades(LinhaVisual cabecalho) {
        List<Glifo> glifos = cabecalho.glifos();
        boolean primeiroJaVisto = false;
        Glifo anterior = null;
        for (Glifo g : glifos) {
            boolean inicioDePalavra = anterior == null
                    || g.x() - (anterior.x() + anterior.largura()) > anterior.largura() * 0.5f;
            if (inicioDePalavra && (g.caractere() == 'D' || g.caractere() == 'd')) {
                if (primeiroJaVisto) {
                    return g.x() - 1f;
                }
                primeiroJaVisto = true;
            }
            anterior = g;
        }
        throw new IllegalArgumentException(
                "cabeçalho de rubricas sem a segunda coluna de descrição: " + cabecalho.texto());
    }

    /**
     * Recorta uma célula de rubrica pela FORMA do conteúdo.
     *
     * <p>O último número com centavos é o valor; um número com centavos antes
     * dele é a quantidade; o resto é a descrição. Sem número, não há rubrica —
     * é linha de moldura ou de continuação.
     */
    private static void acrescentar(List<Rubrica> destino, String celula) {
        if (celula.isBlank()) {
            return;
        }
        List<String> numeros = new ArrayList<>();
        Matcher m = DINHEIRO.matcher(celula);
        int inicioDoPrimeiro = -1;
        while (m.find()) {
            if (inicioDoPrimeiro < 0) {
                inicioDoPrimeiro = m.start();
            }
            numeros.add(m.group());
        }
        if (numeros.isEmpty()) {
            return;
        }
        String descricao = celula.substring(0, inicioDoPrimeiro).trim();
        if (descricao.isEmpty()) {
            return;   // número solto sem rubrica: não é uma linha de rubrica
        }
        BigDecimal valor = aDecimal(numeros.get(numeros.size() - 1));
        BigDecimal quantidade = numeros.size() > 1 ? aDecimal(numeros.get(0)) : null;
        destino.add(Rubrica.semCodigo(descricao, quantidade, valor));
    }

    /** O rodapé de bases: rótulos alinhados à esquerda, um valor sob cada um. */
    private static Map<String, BigDecimal> lerBases(List<LinhaVisual> via, int iRotulos) {
        Map<String, BigDecimal> bases = new LinkedHashMap<>();
        if (iRotulos + 1 >= via.size()) {
            return bases;
        }
        List<Coluna> colunas = LeitorDeTabela.colunasPorCabecalho(via.get(iRotulos),
                ROTULOS_DAS_BASES, NOMES_DAS_BASES,
                java.util.Collections.nCopies(NOMES_DAS_BASES.size(),
                        LeitorDeTabela.Alinhamento.ESQUERDA));
        Map<String, String> celulas =
                LeitorDeTabela.lerLinha(via.get(iRotulos + 1), colunas);
        celulas.forEach((nome, valor) -> {
            BigDecimal v = ultimoValor(valor);
            if (v != null) {
                bases.put(nome, v);
            }
        });
        return bases;
    }

    /**
     * Onde está o cabeçalho "Matrícula Nome" dentro da via.
     *
     * <p>Exige as duas palavras: a linha do estabelecimento traz "Matriz", que
     * um {@code contains("Matr")} sozinho casaria.
     */
    private static int indiceDoCabecalhoDaMatricula(List<LinhaVisual> via) {
        for (int i = 0; i < via.size(); i++) {
            String t = via.get(i).texto();
            if (t.startsWith(CABECALHO_DA_MATRICULA) && t.contains("Nome")) {
                return i;
            }
        }
        return -1;
    }

    private static String depoisDe(List<LinhaVisual> via, int indice) {
        return indice + 1 < via.size() ? via.get(indice + 1).texto() : "";
    }

    private static BigDecimal ultimoValor(String texto) {
        Matcher m = DINHEIRO.matcher(texto == null ? "" : texto);
        String ultimo = null;
        while (m.find()) {
            ultimo = m.group();
        }
        return ultimo == null ? null : aDecimal(ultimo);
    }

    private static BigDecimal aDecimal(String valor) {
        return new BigDecimal(valor.replace(".", "").replace(",", "."));
    }

    /** "JUNHO/2026" vira "06/2026". */
    static String competenciaDe(String texto) {
        Matcher m = REFERENCIA.matcher(texto);
        if (!m.find()) {
            return null;
        }
        int mes = MESES.indexOf(m.group(1).toUpperCase(Locale.ROOT)) + 1;
        return String.format("%02d/%s", mes, m.group(2));
    }
}
