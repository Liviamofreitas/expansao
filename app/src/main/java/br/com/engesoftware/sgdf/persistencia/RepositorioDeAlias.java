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

    /** A chave do cadastro. O valor não vive mais aqui — RA-03. */
    public static final String CHAVE_PESO = "classificacao.peso_do_alias";

    private final Sgdf sgdf;
    private final double peso;
    private final String desligadoPorque;

    /**
     * @param peso quanto um alias soma ao score; zero desliga o bônus
     * @param desligadoPorque a razão, quando o peso é zero por falta de cadastro
     */
    RepositorioDeAlias(Sgdf sgdf, double peso, String desligadoPorque) {
        this.sgdf = sgdf;
        this.peso = peso;
        this.desligadoPorque = desligadoPorque;
    }

    /**
     * Lê o peso do cadastro — o que a RA-03 pedia.
     *
     * <p><b>Sem cadastro, bônus nenhum — e nunca um padrão em código.</b> Cair
     * num 0,10 escrito aqui recriaria exatamente a duplicação que a RA-10 custou
     * caro: duas fontes que concordam hoje, e a que ninguém edita é a que o
     * código lê. Zero é a direção segura: o alias deixa de corroborar, o
     * documento vai para a fila de triagem, e uma pessoa decide — que é o
     * comportamento do sistema antes de qualquer alias existir.
     *
     * <p><b>Mas zero em silêncio seria o defeito de sempre.</b> Sem cadastro, a
     * F1-06 para de cumprir o que promete ("o mesmo padrão não retorna"): quem
     * triou no mês passado vê o arquivo voltar, e aprende que triar não adianta.
     * A fila crescendo é o único sintoma, e ele parece trabalho normal. Por isso
     * o motivo fica legível em {@link #desligadoPorque()} em vez de ser
     * deduzido.
     *
     * <p><b>Valor inválido levanta, não degrada.</b> Um peso de 0,5 no cadastro
     * faria o nome do arquivo classificar sozinho — a única coisa que este
     * sistema existe para não fazer. Recusar é preferível a classificar errado.
     */
    public static RepositorioDeAlias doCadastro(Sgdf sgdf) {
        java.math.BigDecimal cadastrado = new RepositorioDeParametro(sgdf)
                .decimalGlobal(CHAVE_PESO, 0.0, Bonus.MAXIMO);
        if (cadastrado == null) {
            return new RepositorioDeAlias(sgdf, 0.0,
                    "o parâmetro " + CHAVE_PESO + " não está cadastrado: o alias não soma "
                    + "nada ao score, e o mesmo padrão volta à fila de triagem todo mês "
                    + "(a F1-06 promete o contrário). Cadastrar é um INSERT em parametro, "
                    + "não um release");
        }
        return new RepositorioDeAlias(sgdf, cadastrado.doubleValue(),
                cadastrado.signum() == 0
                        ? "o parâmetro " + CHAVE_PESO + " está cadastrado como zero: alguém "
                          + "desligou o bônus de alias deliberadamente"
                        : null);
    }

    /** Quanto um alias soma hoje, como o cadastro manda. */
    public double peso() {
        return peso;
    }

    /** Nulo quando o bônus está ativo; a razão, quando não está. */
    public String desligadoPorque() {
        return desligadoPorque;
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
        if (peso <= 0) {
            // Nem consulta o banco: sem peso não há bônus a conceder, e ler a
            // tabela para multiplicar tudo por zero só gastaria uma query por
            // arquivo. O porquê está em desligadoPorque(), não neste return.
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
                    porTipo.put(rs.getString(1), peso);
                }
                return porTipo.isEmpty() ? Bonus.nenhum() : new Bonus(porTipo);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os aliases", e);
        }
    }
}
