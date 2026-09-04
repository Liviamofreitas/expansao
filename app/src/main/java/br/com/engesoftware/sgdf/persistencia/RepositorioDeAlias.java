package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.classificacao.Bonus;
import br.com.engesoftware.sgdf.triagem.PadraoDeNome;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O bônus de alias do cap. 8.3 — e a metade da F1-06 que faz o critério de
 * aceite ser verdade.
 *
 * <p><b>Por que esta classe precisa existir.</b> A F1-06 promete que <i>"o mesmo
 * padrão não retorna"</i> à fila. Gravar o alias na confirmação não cumpre isso
 * sozinho: sem alguém que LEIA a tabela e transforme o alias em bônus, o mês
 * seguinte reclassifica o mesmo arquivo com a mesma pontuação e ele volta para a
 * fila — a confirmação teria virado um registro que ninguém consulta, e o
 * usuário aprenderia que triar não adianta.
 *
 * <p><b>Por que o casamento é por substring.</b> O alias é a parte estável
 * ({@code aso_demissional}); o nome do arquivo carrega o resto, que varia
 * ({@code aso_demissional_joao_silva}). Exigir igualdade faria o alias casar
 * apenas com o arquivo exato de onde foi aprendido — inútil por construção.
 */
public final class RepositorioDeAlias {

    /**
     * Peso do alias no score.
     *
     * <p>Provisório e deliberadamente pequeno. O {@link Bonus} recusa qualquer
     * valor acima de 0,20 porque bônus grande vira classificação pelo nome do
     * arquivo, e o sistema existe porque o nome não é confiável. 0,10 fecha a
     * faixa de triagem (0,70–0,95) pela metade: um documento que o conteúdo já
     * colocou em 0,86 passa a decidir sozinho; um em 0,72 continua indo para a
     * fila, que é o comportamento certo — o alias corrobora, não elege.
     *
     * <p>Deveria vir do cadastro, como os limiares. Registrado em PENDENCIAS.
     */
    public static final double PESO_DO_ALIAS = 0.10;

    private final Sgdf sgdf;

    public RepositorioDeAlias(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * O bônus que o nome do arquivo concede a cada tipo.
     *
     * <p>Um nome pode casar com mais de um alias — de tipos diferentes. Não é
     * ambiguidade a resolver aqui: os dois recebem o bônus e o
     * {@code Classificador} decide pelo conteúdo, que é onde a decisão pertence.
     * Empatar dois tipos altos manda para triagem, que é o desfecho correto.
     */
    public Bonus doNome(String nomeArquivo) {
        PadraoDeNome padrao = PadraoDeNome.de(nomeArquivo);
        if (!padrao.aprendivel()) {
            return Bonus.nenhum();
        }
        String sql = """
                SELECT t.codigo
                FROM   tipo_alias a
                JOIN   tipo_documental t ON t.id = a.tipo_id
                WHERE  position(a.texto_normalizado IN ?) > 0
                  AND  t.ativo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, padrao.normalizado());
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Double> porTipo = new LinkedHashMap<>();
                while (rs.next()) {
                    porTipo.put(rs.getString(1), PESO_DO_ALIAS);
                }
                return porTipo.isEmpty() ? Bonus.nenhum() : new Bonus(porTipo);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os aliases", e);
        }
    }
}
