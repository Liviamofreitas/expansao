package br.com.engesoftware.sgdf.coleta;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decide o que a varredura ingere e o que ignora — cap. 8.1.
 *
 * <p>Nenhum arquivo é descartado em silêncio: todo descarte devolve um
 * {@link Motivo}, que o chamador registra. Arquivo que some sem rastro é a
 * falha que destrói a confiança do operador na ferramenta, porque o sintoma
 * ("coloquei na pasta e o sistema não viu") é indistinguível de um defeito.
 */
public final class PoliticaDeArquivos {

    public enum Motivo {
        ACEITO,
        EXTENSAO_NAO_ACEITA,
        TAMANHO_EXCEDIDO,
        ARQUIVO_TEMPORARIO,
        COPIA_DE_CONFLITO,
        CHECKLIST_EM_PLANILHA,
        ARQUIVO_OCULTO
    }

    public record Decisao(Motivo motivo, String detalhe) {
        public boolean aceito() {
            return motivo == Motivo.ACEITO;
        }
    }

    /** Cap. 8.1: "tipos aceitos pdf, xlsx, xls, csv". */
    private static final Set<String> EXTENSOES_PADRAO = Set.of("pdf", "xlsx", "xls", "csv");

    /**
     * Cópias de conflito de sincronização. O OwnCloud e o Nextcloud gravam
     * "(conflicted copy …)" ou "_conflict-…"; o Windows/OneDrive usa
     * "- Copia" / "- Copy". São sinalizadas como alerta de organização e nunca
     * publicadas, porque publicar uma cópia divergente como evidência é pior do
     * que não achar o arquivo.
     */
    private static final List<Pattern> CONFLITO = List.of(
            Pattern.compile("\\(conflicted copy[^)]*\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("_conflict-\\d", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\(copia em conflito[^)]*\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile(" - c[oó]pia(\\s*\\(\\d+\\))?\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile(" - copy(\\s*\\(\\d+\\))?\\.", Pattern.CASE_INSENSITIVE));

    private static final Pattern CHECKLIST = Pattern.compile(
            "check\\s*list|checklist", Pattern.CASE_INSENSITIVE);

    private final Set<String> extensoesAceitas;
    private final long tamanhoMaximo;

    public PoliticaDeArquivos(Set<String> extensoesAceitas, long tamanhoMaximo) {
        this.extensoesAceitas = extensoesAceitas;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    /** Padrões do cap. 8.1: pdf/xlsx/xls/csv e 50 MB. Ambos são parâmetro. */
    public static PoliticaDeArquivos padrao() {
        return new PoliticaDeArquivos(EXTENSOES_PADRAO, 50L * 1024 * 1024);
    }

    public Decisao avaliar(EntradaRemota entrada) {
        String nome = entrada.nome();
        String minusculo = nome.toLowerCase(Locale.ROOT);

        if (nome.startsWith("~$") || minusculo.endsWith(".tmp") || minusculo.endsWith(".part")) {
            return new Decisao(Motivo.ARQUIVO_TEMPORARIO, nome);
        }
        if (nome.startsWith(".")) {
            return new Decisao(Motivo.ARQUIVO_OCULTO, nome);
        }
        for (Pattern p : CONFLITO) {
            if (p.matcher(nome).find()) {
                return new Decisao(Motivo.COPIA_DE_CONFLITO, nome);
            }
        }
        // Os checklists em planilha são o processo antigo (D-10) e não são
        // evidência. Restrito a planilhas: um PDF com "checklist" no nome pode
        // ser um documento legítimo do cliente.
        String extensao = extensao(minusculo);
        if (CHECKLIST.matcher(nome).find() && (extensao.equals("xlsx") || extensao.equals("xls"))) {
            return new Decisao(Motivo.CHECKLIST_EM_PLANILHA, nome);
        }
        if (!extensoesAceitas.contains(extensao)) {
            return new Decisao(Motivo.EXTENSAO_NAO_ACEITA, extensao.isEmpty() ? "(sem extensão)" : extensao);
        }
        if (entrada.tamanho() > tamanhoMaximo) {
            return new Decisao(Motivo.TAMANHO_EXCEDIDO,
                    entrada.tamanho() + " > " + tamanhoMaximo + " bytes");
        }
        return new Decisao(Motivo.ACEITO, null);
    }

    public long tamanhoMaximo() {
        return tamanhoMaximo;
    }

    private static String extensao(String nomeMinusculo) {
        int ponto = nomeMinusculo.lastIndexOf('.');
        return ponto < 0 ? "" : nomeMinusculo.substring(ponto + 1);
    }
}
