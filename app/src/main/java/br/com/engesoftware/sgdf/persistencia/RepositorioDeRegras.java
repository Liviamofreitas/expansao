package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.classificacao.Ancora;
import br.com.engesoftware.sgdf.classificacao.CargaDeRegras;
import br.com.engesoftware.sgdf.classificacao.RegraDeReconhecimento;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * As regras de reconhecimento, lidas do banco — F1-03 com cadastro.
 *
 * <p><b>Por que isto existe.</b> O classificador era construído com
 * {@code CargaDeRegras.todas()}: regras compiladas em Java. A tabela
 * {@code regra_reconhecimento} existia, era semeada pela V103 e ninguém a lia.
 * Enquanto as regras eram só do time de desenvolvimento isso funcionava; a
 * partir do momento em que a área cadastra tipo novo (ADR-004), uma tela que
 * grava numa tabela não lida seria uma tela que não faz nada.
 *
 * <p><b>Carregado a cada varredura, e é de propósito.</b> "Entra valendo na
 * hora" foi requisito explícito. Um cache exigiria invalidação, e invalidação
 * errada produz o pior sintoma possível: a regra nova funciona na máquina de
 * quem testou e não funciona na réplica que já estava no ar. Ler ~20 regras do
 * banco custa milissegundos ao lado de baixar e escanear arquivos.
 */
public class RepositorioDeRegras {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Sgdf sgdf;

    public RepositorioDeRegras(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Todas as regras ativas, prontas para o {@code Classificador}.
     *
     * <p><b>Duas fontes, e não duas verdades.</b> A fronteira é: <i>a regra mora
     * junto do que ela identifica</i>.
     *
     * <ul>
     *   <li><b>Banco</b> — os tipos do checklist. São cadastro: o admin
     *       acrescenta e exclui (ADR-004), e a regra entra valendo na hora.
     *   <li><b>Código</b> — a família de comprovante bancário. Eles NÃO são
     *       tipo documental: `tipo_documental` é o checklist, e comprovante não
     *       é item de checklist, é a prova de que um item foi pago. O cap. 8.5
     *       diz que não têm âncora fixa e são "identificados pelo pareamento",
     *       e `documento.tipo_id` é anulável por causa disso.
     * </ul>
     *
     * <p>Empurrar os comprovantes para dentro de {@code tipo_documental} só
     * para unificar a origem os faria aparecer no checklist — o oposto do que o
     * capítulo manda. O conjunto das duas fontes é, por construção, igual a
     * {@code CargaDeRegras.todas()} no dia em que esta classe nasceu; um teste
     * compara os dois e falha na primeira divergência.
     */
    public List<RegraDeReconhecimento> ativas() {
        return sgdf.emTransacao(conexao -> {
            String sql = """
                    SELECT t.codigo, r.emissor, r.ancoras, r.campos, r.campos_essenciais,
                           r.peso_por_campo, r.limiar_auto, r.limiar_triagem, r.versao
                      FROM regra_reconhecimento r
                      JOIN tipo_documental t ON t.id = r.tipo_id
                     WHERE r.ativo AND t.ativo
                     ORDER BY t.codigo, r.versao
                    """;
            List<RegraDeReconhecimento> regras = new ArrayList<>();
            try (PreparedStatement ps = conexao.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    regras.add(uma(rs));
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("falha ao carregar as regras de reconhecimento", e);
            }
            if (regras.isEmpty()) {
                // NENHUMA REGRA NÃO É "NADA A RECONHECER" — É O CLASSIFICADOR
                // CEGO.
                //
                // Com a lista vazia o pipeline roda inteiro, não reprova nada e
                // manda todo documento para triagem manual com "nenhum tipo
                // candidato". Parece um repositório de arquivos estranhos;
                // é a carga que não foi aplicada. Já aconteceu neste projeto
                // com a base da NVD, e o sintoma enganou por horas.
                throw new IllegalStateException(
                        "nenhuma regra de reconhecimento ativa no banco: o classificador não "
                        + "reconheceria NENHUM tipo e todo documento iria para triagem. "
                        + "Verifique se as cargas V103 e V108 foram aplicadas.");
            }
            // Os comprovantes entram aqui, do código, pelo motivo no javadoc.
            regras.addAll(CargaDeRegras.comprovantesBancarios());
            return List.copyOf(regras);
        });
    }

    // -------------------------------------------------------------------------

    private static RegraDeReconhecimento uma(ResultSet rs) throws java.sql.SQLException {
        String tipo = rs.getString("codigo");
        try {
            return new RegraDeReconhecimento(
                    tipo,
                    rs.getString("emissor"),
                    ancoras(rs.getString("ancoras")),
                    // CAMPOS E ESSENCIAIS FICAM VAZIOS, E ISSO É FIDELIDADE, NÃO
                    // DESCUIDO.
                    //
                    // Medido antes de decidir: TODAS as 14 regras corporativas
                    // do código têm 0 campos e 0 essenciais, enquanto o banco
                    // traz de 1 a 4 de cada. As colunas `campos` e
                    // `campos_essenciais` guardam a forma do CADASTRO — inclusive
                    // campos posicionais (`modo: ABAIXO_DO_ROTULO`), que o
                    // record PadraoDeCampo nem representa — e hoje NINGUÉM as lê
                    // em Java.
                    //
                    // Carregá-las aqui ativaria a V8 (completude de campos
                    // essenciais) para tipos onde ela está inerte desde sempre.
                    // Pode até ser desejável; não é o que esta mudança se propõe
                    // a fazer. Trocar a fonte das regras E ligar uma validação no
                    // mesmo passo tornaria impossível saber qual das duas causou
                    // o que aparecesse depois.
                    //
                    // Fica registrado como achado: há um terceiro espelho não
                    // lido no banco, e ligá-lo é uma decisão própria.
                    List.of(),
                    List.of(),
                    rs.getDouble("peso_por_campo"),
                    rs.getDouble("limiar_auto"),
                    rs.getDouble("limiar_triagem"),
                    rs.getInt("versao"));
        } catch (RuntimeException e) {
            // UMA REGRA QUEBRADA DERRUBA A CARGA INTEIRA, E NÃO É EXAGERO.
            //
            // A alternativa seria pular a regra e seguir — e aí o sistema
            // deixaria de reconhecer um tipo inteiro sem que nada ficasse
            // vermelho. Documentos daquele tipo passariam a cair em triagem
            // manual, e alguém levaria dias para desconfiar da regra em vez de
            // desconfiar dos arquivos.
            throw new IllegalStateException(
                    "regra de reconhecimento inválida no banco, tipo '" + tipo + "': "
                    + e.getMessage(), e);
        }
    }

    private static List<Ancora> ancoras(String json) {
        List<Ancora> lista = new ArrayList<>();
        for (JsonNode a : ler(json)) {
            String expressao = texto(a, "expressao");
            double peso = a.get("peso").asDouble();
            boolean discriminante = a.has("discriminante") && a.get("discriminante").asBoolean();
            boolean semEspacos = a.has("ignorando_espacos")
                    && a.get("ignorando_espacos").asBoolean();
            lista.add(new Ancora(java.util.regex.Pattern.compile(expressao),
                    peso, discriminante, semEspacos));
        }
        return lista;
    }



    private static JsonNode ler(String json) {
        if (json == null || json.isBlank()) {
            return JSON.createArrayNode();
        }
        return JSON.readTree(json);
    }

    /** Campo obrigatório: ausente é erro de cadastro, não valor vazio. */
    private static String texto(JsonNode no, String campo) {
        JsonNode v = no.get(campo);
        if (v == null || v.isNull() || v.asString().isBlank()) {
            throw new IllegalArgumentException("campo '" + campo + "' ausente ou vazio em "
                    + no);
        }
        return v.asString();
    }
}
