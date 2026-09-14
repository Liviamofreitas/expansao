package br.com.engesoftware.sgdf.documento;

import br.com.engesoftware.sgdf.extracao.LeitorDeTabela;
import br.com.engesoftware.sgdf.folha.DeParaDeRubricas;
import br.com.engesoftware.sgdf.folha.PapelDaRubrica;
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
 * Lê a FOPAG — a relação da folha de pagamento.
 *
 * <p><b>É a fonte que a pendência A05 pedia.</b> O contracheque servia e serve,
 * mas é fonte de segunda escolha por dois motivos que este documento resolve:
 *
 * <ol>
 *   <li><b>Código de rubrica.</b> A FOPAG imprime "08305 VALE ALIMENTACAO"; o
 *       contracheque imprime só a descrição. A descrição varia de grafia entre
 *       competências e entre sistemas; o código não. Onde há código, a junção
 *       das conciliações é por ele.</li>
 *   <li><b>Coluna RESULTADOS.</b> Traz as bases e os totais já codificados —
 *       {@code 14000 BASE FGTS MES}, {@code 12200 BASE INSS LIM TETO},
 *       {@code 13300 BASE LIQ IRRF MES} — que no contracheque saem de um rodapé
 *       de rótulos posicionais.</li>
 * </ol>
 *
 * <p>Três colunas por colaborador, separadas por faixa horizontal: proventos à
 * esquerda de x=262, descontos entre 262 e 506, resultados à direita. Dentro de
 * cada célula a leitura é por FORMA DO CONTEÚDO — código de 5 dígitos no
 * início, último número com centavos é o valor, um número antes dele é a
 * referência — pelo mesmo motivo do contracheque: a coluna de referência fica
 * vazia na maioria das linhas e a projeção não é estável.
 *
 * <p><b>Os códigos vêm do cadastro, e não daqui</b> — pendência RA-10, cap. 7.2
 * última linha: <i>"os códigos de rubrica são CADASTRO, não código-fonte"</i>.
 * Até aqui esta classe carregava nove códigos em constante Java <i>embora os
 * mesmos nove já vivessem em</i> {@code rubrica_de_para}. Eram duas verdades
 * para a mesma regra, e a segunda ganhava em silêncio: mudar o plano de contas
 * no cadastro deixava o leitor procurando o código antigo, não achando o total,
 * e devolvendo <b>nulo</b> — a conciliação do cap. 9 então compararia a guia
 * contra nada e passaria.
 *
 * <p><b>Sem de-para não há leitura.</b> O construtor exige os sete papéis
 * estruturais e recusa na hora quando falta um, em vez de descobrir na 400ª
 * página que a base do FGTS é nula em todo mundo.
 */
public final class LeitorDeFopag {

    /**
     * O que a coluna RESULTADOS tem de entregar para a folha servir à
     * conciliação. Sete, e nenhum é opcional: cada um alimenta uma regra do
     * cap. 9.
     */
    private static final PapelDaRubrica[] ESTRUTURAIS = {
        PapelDaRubrica.TOTAL_PROVENTOS, PapelDaRubrica.TOTAL_DESCONTOS,
        PapelDaRubrica.LIQUIDO, PapelDaRubrica.BASE_FGTS, PapelDaRubrica.FGTS_MES,
        PapelDaRubrica.BASE_INSS, PapelDaRubrica.BASE_IRRF,
    };

    private final String totalProventos;
    private final String totalDescontos;
    private final String liquido;
    private final String baseFgts;
    private final String fgtsMes;
    private final String baseInss;
    private final String baseIrrf;

    /**
     * @param dePara o de-para vigente do sistema de folha, do cadastro
     * @throws DeParaDeRubricas.DeParaIncompleto se faltar papel estrutural
     * @throws DeParaDeRubricas.RubricaAmbigua se dois códigos declararem o mesmo
     */
    public LeitorDeFopag(DeParaDeRubricas dePara) {
        if (dePara == null) {
            throw new IllegalArgumentException(
                    "ler a FOPAG exige o de-para de rubricas do cadastro (cap. 7.2)");
        }
        Map<PapelDaRubrica, String> codigos = dePara.exigir(ESTRUTURAIS);
        this.totalProventos = codigos.get(PapelDaRubrica.TOTAL_PROVENTOS);
        this.totalDescontos = codigos.get(PapelDaRubrica.TOTAL_DESCONTOS);
        this.liquido = codigos.get(PapelDaRubrica.LIQUIDO);
        this.baseFgts = codigos.get(PapelDaRubrica.BASE_FGTS);
        this.fgtsMes = codigos.get(PapelDaRubrica.FGTS_MES);
        this.baseInss = codigos.get(PapelDaRubrica.BASE_INSS);
        this.baseIrrf = codigos.get(PapelDaRubrica.BASE_IRRF);
    }

    private static final float FIM_DOS_PROVENTOS = 262f;
    private static final float FIM_DOS_DESCONTOS = 506f;

    private static final Pattern FUNCIONARIO =
            Pattern.compile("^(\\d{6,})\\s*-\\s*(.+?)\\s+(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern CELULA =
            Pattern.compile("^(\\d{5})\\s+(.+)$");
    private static final Pattern DINHEIRO =
            Pattern.compile("\\d{1,3}(?:\\.\\d{3})*,\\d{2}");
    private static final Pattern MES =
            Pattern.compile("M[eê]s:\\s*(\\w+)/(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CENTRO_DE_CUSTO =
            Pattern.compile("CENTRO DE CUSTO:\\s*(\\S+)\\s*-\\s*(.+)$");

    private static final List<String> MESES = List.of("JANEIRO", "FEVEREIRO", "MARÇO",
            "ABRIL", "MAIO", "JUNHO", "JULHO", "AGOSTO", "SETEMBRO", "OUTUBRO",
            "NOVEMBRO", "DEZEMBRO");

    private static final String RESUMO = "RESUMO GERAL";

    public FolhaDeCompetencia ler(TextoExtraido texto) {
        String competencia = competenciaDe(texto.textoCompleto());
        Map<String, ItemDaFolha> porMatricula = new LinkedHashMap<>();

        for (PaginaExtraida pagina : texto.paginas()) {
            List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pagina);
            // O resumo geral consolida por rubrica e não tem colaborador: é o
            // gabarito da leitura, não parte dela.
            if (LeitorDeTabela.indiceDaLinhaCom(linhas, RESUMO) >= 0) {
                continue;
            }
            for (ItemDaFolha item : itensDaPagina(linhas, competencia)) {
                // Um colaborador pode continuar na página seguinte, como no
                // contracheque (achado A21): as rubricas se somam.
                porMatricula.merge(item.matricula(), item, this::unir);
            }
        }
        return new FolhaDeCompetencia(competencia, new ArrayList<>(porMatricula.values()));
    }

    /**
     * O RESUMO GERAL: total de cada rubrica somado sobre todos os colaboradores.
     *
     * <p>É a conferência que o próprio documento carrega, e a mais forte que
     * existe aqui: a soma do que foi lido colaborador a colaborador tem de
     * reproduzir o total que a folha declara por rubrica.
     */
    public Map<String, BigDecimal> resumoGeral(TextoExtraido texto) {
        Map<String, BigDecimal> totais = new LinkedHashMap<>();
        for (PaginaExtraida pagina : texto.paginas()) {
            List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pagina);
            if (LeitorDeTabela.indiceDaLinhaCom(linhas, RESUMO) < 0) {
                continue;
            }
            for (LinhaVisual linha : linhas) {
                Matcher m = CELULA.matcher(linha.texto().trim());
                if (!m.find()) {
                    continue;
                }
                // No resumo a última coluna é a TOTAL — a soma das três acima.
                BigDecimal total = ultimoValor(m.group(2));
                if (total != null) {
                    totais.put(m.group(1), total);
                }
            }
        }
        return totais;
    }

    private List<ItemDaFolha> itensDaPagina(List<LinhaVisual> linhas, String competencia) {
        List<ItemDaFolha> itens = new ArrayList<>();
        // A FOPAG é emitida por EMPRESA, não por contrato: as páginas se
        // agrupam por centro de custo, e o recorte de um ciclo de faturamento é
        // o centro de custo. Sem carregá-lo, a conciliação de um contrato
        // somaria colaboradores de outro.
        String centro = centroDeCusto(linhas);
        String matricula = null;
        String nome = null;
        List<Rubrica> proventos = new ArrayList<>();
        List<Rubrica> descontos = new ArrayList<>();
        Map<String, BigDecimal> resultados = new LinkedHashMap<>();

        for (LinhaVisual linha : linhas) {
            Matcher f = FUNCIONARIO.matcher(linha.texto().trim());
            if (f.find()) {
                if (matricula != null) {
                    itens.add(montar(matricula, nome, competencia,
                            proventos, descontos, resultados, centro));
                }
                matricula = f.group(1);
                nome = f.group(2).trim();
                proventos = new ArrayList<>();
                descontos = new ArrayList<>();
                resultados = new LinkedHashMap<>();
                continue;
            }
            if (matricula == null) {
                continue;
            }
            acrescentar(proventos, linha.textoEntre(Float.NEGATIVE_INFINITY, FIM_DOS_PROVENTOS));
            acrescentar(descontos, linha.textoEntre(FIM_DOS_PROVENTOS, FIM_DOS_DESCONTOS));
            resultado(resultados, linha.textoEntre(FIM_DOS_DESCONTOS, Float.POSITIVE_INFINITY));
        }
        if (matricula != null) {
            itens.add(montar(matricula, nome, competencia, proventos, descontos,
                    resultados, centro));
        }
        return itens;
    }

    private ItemDaFolha montar(String matricula, String nome, String competencia,
                               List<Rubrica> proventos, List<Rubrica> descontos,
                               Map<String, BigDecimal> resultados, String centro) {
        // A FOPAG não imprime CPF: a chave dela é a matrícula, e é por isso que
        // a junção com os documentos de benefício tem de ser por matrícula
        // sempre que o outro lado a traga (achado A14).
        return new ItemDaFolha(matricula, nome, null, competencia, proventos, descontos,
                resultados.get(totalProventos), resultados.get(totalDescontos),
                resultados.get(liquido), resultados.get(baseFgts), resultados.get(fgtsMes),
                resultados.get(baseInss), resultados.get(baseIrrf), resultados, centro);
    }

    private static void acrescentar(List<Rubrica> destino, String celula) {
        Matcher m = CELULA.matcher(celula.trim());
        if (!m.find()) {
            return;
        }
        String resto = m.group(2);
        List<String> numeros = numerosDe(resto);
        if (numeros.isEmpty()) {
            return;
        }
        int inicioDoPrimeiro = resto.indexOf(numeros.get(0));
        String descricao = resto.substring(0, inicioDoPrimeiro).trim();
        if (descricao.isEmpty()) {
            return;
        }
        BigDecimal valor = aDecimal(numeros.get(numeros.size() - 1));
        BigDecimal referencia = numeros.size() > 1 ? aDecimal(numeros.get(0)) : null;
        destino.add(new Rubrica(m.group(1), descricao, referencia, valor));
    }

    private static void resultado(Map<String, BigDecimal> destino, String celula) {
        Matcher m = CELULA.matcher(celula.trim());
        if (!m.find()) {
            return;
        }
        BigDecimal valor = ultimoValor(m.group(2));
        if (valor != null) {
            destino.put(m.group(1), valor);
        }
    }

    private ItemDaFolha unir(ItemDaFolha a, ItemDaFolha b) {
        List<Rubrica> proventos = new ArrayList<>(a.proventos());
        proventos.addAll(b.proventos());
        List<Rubrica> descontos = new ArrayList<>(a.descontos());
        descontos.addAll(b.descontos());
        Map<String, BigDecimal> resultados = new LinkedHashMap<>(a.resultados());
        resultados.putAll(b.resultados());
        return montar(a.matricula(), a.nome(), a.competencia(), proventos, descontos,
                resultados, a.centroDeCusto() != null ? a.centroDeCusto() : b.centroDeCusto());
    }

    private static List<String> numerosDe(String texto) {
        List<String> numeros = new ArrayList<>();
        Matcher m = DINHEIRO.matcher(texto);
        while (m.find()) {
            numeros.add(m.group());
        }
        return numeros;
    }

    private static BigDecimal ultimoValor(String texto) {
        List<String> numeros = numerosDe(texto == null ? "" : texto);
        return numeros.isEmpty() ? null : aDecimal(numeros.get(numeros.size() - 1));
    }

    private static BigDecimal aDecimal(String valor) {
        return new BigDecimal(valor.replace(".", "").replace(",", "."));
    }

    /** O centro de custo da página, quando declarado — o recorte por contrato. */
    public static String centroDeCusto(List<LinhaVisual> linhas) {
        for (LinhaVisual l : linhas) {
            Matcher m = CENTRO_DE_CUSTO.matcher(l.texto().trim());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    static String competenciaDe(String texto) {
        Matcher m = MES.matcher(texto);
        if (!m.find()) {
            return null;
        }
        int mes = MESES.indexOf(m.group(1).toUpperCase(Locale.ROOT)) + 1;
        return mes == 0 ? null : String.format("%02d/%s", mes, m.group(2));
    }
}
