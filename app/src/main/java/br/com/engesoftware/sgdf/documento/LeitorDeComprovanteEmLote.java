package br.com.engesoftware.sgdf.documento;

import br.com.engesoftware.sgdf.extracao.Coluna;
import br.com.engesoftware.sgdf.extracao.LeitorDeTabela;
import br.com.engesoftware.sgdf.extracao.LinhaVisual;
import br.com.engesoftware.sgdf.extracao.PaginaExtraida;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import br.com.engesoftware.sgdf.validacao.FormatoDeCampo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o comprovante de pagamento em lote — a lista de créditos de folha, um por
 * funcionário, num único arquivo.
 *
 * <p><b>Correção do achado A18.</b> O achado registrou que este layout "não cede
 * a nenhum dos dois métodos de detecção de coluna". Estava errado, e o erro foi
 * de método: a projeção era calculada sobre a PÁGINA INTEIRA, onde o rodapé de
 * atendimento e as linhas de texto corrido atravessam todas as faixas e apagam
 * as lacunas. Restrita às linhas de dados, a projeção acha as seis colunas de
 * forma estável para qualquer largura mínima entre 4 e 10 pontos.
 *
 * <p>O que de fato exigia trabalho novo era outra coisa: o nome do funcionário
 * ocupa até três linhas visuais, e o registro precisa ser agrupado por lacuna
 * vertical antes de ser recortado por coluna.
 */
public final class LeitorDeComprovanteEmLote {

    /** Marca do cabeçalho da lista. */
    private static final String CABECALHO = "Número do Pagamento";

    /** Marca do rodapé com os totais — encerra a tabela. */
    private static final String RODAPE = "Total Compromissos";

    /**
     * Marca o início de um registro. Delimitar a região de dados por ela, e não
     * por um literal de rodapé, é o que torna a leitura robusta: o comprovante
     * sem rodapé deixaria o texto de atendimento entrar nos dados, e uma única
     * linha de texto corrido atravessa todas as faixas e apaga as lacunas que
     * separam as colunas. Foi assim que o achado A18 se formou.
     */
    private static final Pattern ANCORA_DE_REGISTRO = Pattern.compile("^\\d{9}\\b");

    private static final Pattern TOTAL =
            Pattern.compile("Total Compromissos:\\s*(\\d+)");
    private static final Pattern VALOR_TOTAL =
            Pattern.compile("Valor Total:\\s*R\\$\\s*([\\d.]+,\\d{2})");

    private static final List<String> COLUNAS = List.of(
            "pagamento", "cliente", "funcionario", "data", "tipo", "valor");

    /**
     * Lacuna vertical que separa um pagamento do próximo. No documento real são
     * 31 pontos entre registros e 6,5 a 13 dentro do registro.
     */
    private static final float LACUNA_ENTRE_REGISTROS = 20f;

    /** Largura mínima de uma lacuna horizontal para contar como separação de coluna. */
    private static final float LACUNA_ENTRE_COLUNAS = 8f;

    public ComprovanteEmLote ler(TextoExtraido texto) {
        List<PagamentoDoLote> pagamentos = new ArrayList<>();
        List<String> problemas = new ArrayList<>();
        for (PaginaExtraida pagina : texto.paginas()) {
            List<LinhaVisual> linhas = LeitorDeTabela.agruparEmLinhas(pagina);
            int cabecalho = LeitorDeTabela.indiceDaLinhaCom(linhas, CABECALHO);
            if (cabecalho < 0) {
                continue;
            }
            List<LinhaVisual> candidatas =
                    LeitorDeTabela.dadosAbaixoDe(linhas, cabecalho, RODAPE);
            List<List<LinhaVisual>> registros =
                    apenasRegistros(LeitorDeTabela.agruparEmBlocos(candidatas, LACUNA_ENTRE_REGISTROS));
            if (registros.isEmpty()) {
                continue;
            }
            List<LinhaVisual> dados = new ArrayList<>();
            registros.forEach(dados::addAll);

            List<Coluna> colunas;
            try {
                colunas = LeitorDeTabela.colunasPorProjecao(dados, LACUNA_ENTRE_COLUNAS, COLUNAS);
            } catch (IllegalArgumentException e) {
                problemas.add("página " + pagina.numero() + ": " + e.getMessage());
                continue;
            }
            for (List<LinhaVisual> registro : registros) {
                PagamentoDoLote p = montar(LeitorDeTabela.lerBloco(registro, colunas));
                if (p != null) {
                    pagamentos.add(p);
                }
            }
        }
        String completo = texto.textoCompleto();
        return new ComprovanteEmLote(pagamentos, inteiro(TOTAL, completo),
                dinheiro(VALOR_TOTAL, completo), problemas);
    }

    /** Só os blocos que contêm a linha de âncora — os demais são rodapé ou nota. */
    private static List<List<LinhaVisual>> apenasRegistros(List<List<LinhaVisual>> blocos) {
        List<List<LinhaVisual>> registros = new ArrayList<>();
        for (List<LinhaVisual> bloco : blocos) {
            boolean temAncora = bloco.stream()
                    .anyMatch(l -> ANCORA_DE_REGISTRO.matcher(l.texto()).find());
            if (temAncora) {
                registros.add(bloco);
            }
        }
        return registros;
    }

    /**
     * Monta o pagamento se as células tiverem o formato esperado.
     *
     * <p>O recorte por coluna sempre devolve alguma coisa; só o formato revela
     * que a faixa pegou o campo errado. Uma célula fora de formato descarta o
     * registro em vez de gravá-lo torto — e a divergência com o rodapé, que o
     * documento declara, torna o descarte visível em vez de silencioso.
     */
    private PagamentoDoLote montar(Map<String, String> c) {
        String pagamento = c.getOrDefault("pagamento", "").trim();
        String cliente = c.getOrDefault("cliente", "").trim();
        String data = c.getOrDefault("data", "").trim();
        String valor = c.getOrDefault("valor", "").trim();

        if (!pagamento.matches("\\d{6,}") || !cliente.matches("\\d{12,}")
                || !FormatoDeCampo.DATA.aceita(data) || !FormatoDeCampo.VALOR.aceita(valor)) {
            return null;
        }
        return new PagamentoDoLote(pagamento, cliente,
                c.getOrDefault("funcionario", "").trim(), data,
                c.getOrDefault("tipo", "").trim(), aDecimal(valor));
    }

    private static int inteiro(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static BigDecimal dinheiro(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        return m.find() ? aDecimal(m.group(1)) : null;
    }

    static BigDecimal aDecimal(String valor) {
        return new BigDecimal(valor.replace(".", "").replace(",", "."));
    }
}
