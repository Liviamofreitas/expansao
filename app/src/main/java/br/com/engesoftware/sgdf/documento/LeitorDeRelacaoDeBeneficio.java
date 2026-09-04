package br.com.engesoftware.sgdf.documento;

import br.com.engesoftware.sgdf.extracao.LeitorDeTabela;
import br.com.engesoftware.sgdf.extracao.LinhaVisual;
import br.com.engesoftware.sgdf.extracao.PaginaExtraida;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê uma relação de beneficiários — VA/VR, plano de saúde, vale-transporte.
 *
 * <p><b>Por que um leitor só para vários emissores.</b> A massa trouxe duas
 * relações de VA/VR de fornecedores diferentes (Flash e Pluxee) e uma de plano
 * de saúde, com layouts que não se parecem. O que elas têm em comum não é o
 * layout: é a FORMA DO REGISTRO — uma matrícula no começo da linha e um valor
 * no fim. Ler por essa forma atravessa os três documentos; ler por coordenada
 * exigiria um leitor por fornecedor.
 *
 * <p>A relação da Flash é a exceção que confirma: lá o registro ocupa seis
 * linhas visuais e a chave é o CPF, não a matrícula. Aquela precisa do
 * agrupamento por bloco (achado A19) e tem o seu próprio caminho.
 */
public final class LeitorDeRelacaoDeBeneficio {

    /** Matrícula no começo da linha: 5 a 12 dígitos seguidos de separador. */
    private static final Pattern REGISTRO =
            Pattern.compile("^(\\d{5,12})\\s+(?!\\d)(.+)$");

    private static final Pattern CPF =
            Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");

    private static final Pattern DINHEIRO =
            Pattern.compile("\\d{1,3}(?:\\.\\d{3})*,\\d{2}");

    /**
     * Onde o emissor imprime o total. Cada fornecedor usa uma palavra diferente
     * e todas foram lidas no documento real — nenhuma foi suposta.
     */
    private static final List<Pattern> TOTAIS = List.of(
            Pattern.compile("SUBTOTAL\\s+R\\$\\s*([\\d.]+,\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("RATEIO\\s+\\d{2}/\\d{4}\\s+R\\$\\s*([\\d.]+,\\d{2})",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("TOTAL DOS PRODUTOS.*?R\\$\\s*([\\d.]+,\\d{2})",
                    Pattern.CASE_INSENSITIVE));

    public RelacaoDeBeneficio ler(TextoExtraido texto) {
        List<BeneficiarioDaRelacao> beneficiarios = new ArrayList<>();
        for (PaginaExtraida pagina : texto.paginas()) {
            for (LinhaVisual linha : LeitorDeTabela.agruparEmLinhas(pagina)) {
                BeneficiarioDaRelacao b = montar(linha.texto().trim());
                if (b != null) {
                    beneficiarios.add(b);
                }
            }
        }
        return new RelacaoDeBeneficio(beneficiarios, totalDeclarado(texto.textoCompleto()));
    }

    private static BeneficiarioDaRelacao montar(String linha) {
        Matcher m = REGISTRO.matcher(linha);
        if (!m.find()) {
            return null;
        }
        String resto = m.group(2);
        BigDecimal valor = ultimoValor(resto);
        if (valor == null) {
            return null;   // linha que começa com número mas não é registro
        }
        Matcher c = CPF.matcher(resto);
        String cpf = c.find() ? c.group() : null;

        // O nome vai do início do resto até o CPF, o primeiro número, ou o fim —
        // o que vier antes. Depois dele só há códigos, centros de custo e valores.
        int fim = cpf != null ? resto.indexOf(cpf) : resto.length();
        String nome = resto.substring(0, fim).replaceAll("\\s+\\d.*$", "").trim();

        return new BeneficiarioDaRelacao(semZeros(m.group(1)), cpf, nome, valor);
    }

    private static BigDecimal totalDeclarado(String texto) {
        for (Pattern p : TOTAIS) {
            Matcher m = p.matcher(texto.replaceAll("\\s+", " "));
            if (m.find()) {
                return aDecimal(m.group(1));
            }
        }
        return null;
    }

    private static BigDecimal ultimoValor(String texto) {
        Matcher m = DINHEIRO.matcher(texto);
        String ultimo = null;
        while (m.find()) {
            ultimo = m.group();
        }
        return ultimo == null ? null : aDecimal(ultimo);
    }

    private static BigDecimal aDecimal(String valor) {
        return new BigDecimal(valor.replace(".", "").replace(",", "."));
    }

    /**
     * Matrícula sem os zeros à esquerda.
     *
     * <p>A folha imprime "000101963" e a relação do fornecedor imprime "101963".
     * Comparar as duas formas como texto não casa nunca — e o efeito seria uma
     * conciliação que acusa 100% de divergência com os dois lados corretos.
     */
    public static String semZeros(String matricula) {
        if (matricula == null) {
            return null;
        }
        String sem = matricula.replaceFirst("^0+", "");
        return sem.isEmpty() ? "0" : sem;
    }
}
