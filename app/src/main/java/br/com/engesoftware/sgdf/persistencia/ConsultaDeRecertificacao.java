package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.indicadores.Csv;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Relatório de recertificação trimestral de acessos — F3-06 e SEC-10.
 *
 * <p>Critério de aceite: <i>"relatório gerado e enviado à DAF"</i>. SEC-10:
 * <i>"recertificação trimestral de acessos por contrato, com relatório para a
 * DAF"</i>.
 *
 * <p><b>O relatório diz o que o sistema sabe e diz o que ele não sabe, e a
 * segunda metade é a que impede uma aprovação em falso.</b> Um documento que
 * lista acessos e não declara a sua própria cobertura convida quem aprova a
 * ler a lista como completa — e assinar uma revisão de acessos parcial
 * acreditando ter revisto tudo é pior que não ter feito revisão nenhuma, porque
 * produz a evidência de conformidade sem o controle.
 *
 * <p>Três colunas, e a diferença entre elas é o relatório:
 *
 * <ul>
 *   <li><b>Concedido</b> — de {@code acesso_observado}: papéis e contratos que o
 *       provedor de identidade entregou, como o sistema os viu.</li>
 *   <li><b>Exercido</b> — de {@code log_auditoria}: o que a pessoa efetivamente
 *       fez no período.</li>
 *   <li><b>A diferença</b> — concedido e nunca exercido. É a coluna acionável:
 *       o acesso que ninguém usa é o que sobrevive a um desligamento, e é o que
 *       um atacante quer.</li>
 * </ul>
 *
 * <p><b>O que fica fora, declarado.</b> Uma conta que existe no diretório e
 * <b>nunca entrou</b> não aparece aqui — o sistema não tem leitura do diretório
 * (RA-16). A recertificação do SGDF cobre o acesso <i>observado</i>; a lista de
 * contas concedidas é da DAF e do provedor, e a revisão completa é a comparação
 * das duas. O cabeçalho do relatório diz isso, para que ninguém aprove metade
 * achando que aprovou o todo.
 */
public final class ConsultaDeRecertificacao {

    /** O que o relatório NÃO cobre. Vai no topo do CSV e do JSON. */
    public static final String RESSALVA =
            "COBERTURA: este relatório lista o acesso OBSERVADO — quem entrou no SGDF no "
            + "período. Uma conta que existe no diretório e nunca entrou NÃO aparece aqui, "
            + "porque o sistema não lê o diretório (RA-16). A recertificação do SEC-10 se "
            + "completa comparando esta lista com a de contas concedidas, que é da DAF e do "
            + "provedor de identidade. Aprovar só esta metade não cumpre o controle.";

    /** Papéis cuja concessão não exercida merece pergunta imediata (cap. 15.1). */
    static final List<String> PRIVILEGIADOS =
            List.of("APROVADOR_DAF", "ADMIN_SISTEMA", "AUDITORIA");

    private final Sgdf sgdf;

    public ConsultaDeRecertificacao(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * O relatório do trimestre.
     *
     * @param desde início do período, inclusive
     * @param ate   fim, inclusive
     */
    public List<Acesso> relatorio(LocalDate desde, LocalDate ate) {
        if (desde == null || ate == null || ate.isBefore(desde)) {
            throw new PeriodoInvalido("a recertificação exige um período com início e fim, "
                    + "e o fim não vem antes do início");
        }
        String sql = """
                WITH concedido AS (
                    SELECT ator,
                           array_agg(DISTINCT p) AS papeis,
                           count(DISTINCT c) FILTER (WHERE c IS NOT NULL) AS contratos,
                           min(dia) AS primeiro_dia,
                           max(dia) AS ultimo_dia,
                           count(DISTINCT (papeis, contratos)) AS concessoes_distintas
                    FROM   acesso_observado a
                    LEFT   JOIN LATERAL unnest(a.papeis) AS p ON true
                    LEFT   JOIN LATERAL unnest(a.contratos) AS c ON true
                    WHERE  a.dia BETWEEN ? AND ?
                    GROUP  BY ator
                ), exercido AS (
                    SELECT ator, count(*) AS acoes,
                           array_agg(DISTINCT acao) AS acoes_distintas,
                           max(ocorrido_em)::date AS ultima_acao
                    FROM   log_auditoria
                    WHERE  ocorrido_em::date BETWEEN ? AND ?
                    GROUP  BY ator
                )
                SELECT c.ator, c.papeis, c.contratos, c.primeiro_dia, c.ultimo_dia,
                       c.concessoes_distintas,
                       coalesce(e.acoes, 0), e.acoes_distintas, e.ultima_acao
                FROM   concedido c
                LEFT   JOIN exercido e ON e.ator = c.ator
                ORDER  BY coalesce(e.acoes, 0), c.ator
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, desde);
            ps.setObject(2, ate);
            ps.setObject(3, desde);
            ps.setObject(4, ate);
            try (ResultSet rs = ps.executeQuery()) {
                List<Acesso> acessos = new ArrayList<>();
                while (rs.next()) {
                    List<String> papeis = lista(rs, 2);
                    acessos.add(new Acesso(rs.getString(1), papeis, rs.getInt(3),
                            rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class),
                            rs.getInt(6), rs.getInt(7), lista(rs, 8),
                            rs.getObject(9, LocalDate.class)));
                }
                return acessos;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao montar a recertificação", e);
        }
    }

    /**
     * O CSV que vai à DAF.
     *
     * <p>A ressalva é a <b>primeira linha do arquivo</b>, antes do cabeçalho.
     * Pô-la no fim, ou num campo por linha, faria a planilha ordenada esconder
     * exatamente o aviso que muda o significado da tabela inteira.
     */
    public String csv(LocalDate desde, LocalDate ate) {
        List<Acesso> acessos = relatorio(desde, ate);
        Csv csv = new Csv(List.of(RESSALVA, "período: " + desde + " a " + ate + "; "
                        + acessos.size() + " ator(es) observado(s)"),
                "ator", "papeis", "contratos_no_escopo", "primeiro_acesso",
                "ultimo_acesso", "concessoes_distintas", "acoes_no_periodo", "ultima_acao",
                "situacao", "atencao");
        for (Acesso a : acessos) {
            csv.linha(a.ator(), String.join("+", a.papeis()), a.contratosNoEscopo(),
                    a.primeiroAcesso(), a.ultimoAcesso(), a.concessoesDistintas(),
                    a.acoesNoPeriodo(), a.ultimaAcao(), a.situacao(),
                    String.join("; ", a.atencao()));
        }
        return csv.texto();
    }

    private static List<String> lista(ResultSet rs, int coluna) throws SQLException {
        java.sql.Array array = rs.getArray(coluna);
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).sorted().toList();
    }

    /** O período tem de existir, e o fim não vem antes do início. */
    public static final class PeriodoInvalido extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PeriodoInvalido(String motivo) {
            super(motivo);
        }
    }

    /**
     * Uma linha do relatório.
     *
     * @param contratosNoEscopo quantos contratos o token concedeu; 0 = visão global
     * @param concessoesDistintas quantas combinações de papéis/contratos foram vistas
     *                            no período — mais de uma é deriva de concessão
     */
    public record Acesso(String ator, List<String> papeis, int contratosNoEscopo,
                         LocalDate primeiroAcesso, LocalDate ultimoAcesso,
                         int concessoesDistintas, int acoesNoPeriodo,
                         List<String> acoesDistintas, LocalDate ultimaAcao) {

        public Acesso {
            papeis = List.copyOf(papeis);
            acoesDistintas = List.copyOf(acoesDistintas);
        }

        /** Entrou e não fez nada que a trilha registre. */
        public boolean somenteLeitura() {
            return acoesNoPeriodo == 0;
        }

        public boolean privilegiado() {
            return papeis.stream().anyMatch(PRIVILEGIADOS::contains);
        }

        /**
         * O que a DAF precisa olhar nesta linha.
         *
         * <p><b>"Somente leitura" não é apontamento sozinho</b>, e é a distinção
         * que evita transformar o relatório em ruído: o papel AUDITORIA lê por
         * definição, e marcá-lo faria a lista de atenção repetir todo trimestre
         * a única linha que está certa. O apontamento é o <b>privilégio de
         * escrita nunca exercido</b>.
         */
        public List<String> atencao() {
            List<String> pontos = new ArrayList<>();
            if (somenteLeitura() && privilegiado() && !papeis.equals(List.of("AUDITORIA"))) {
                pontos.add("papel privilegiado de escrita concedido e não exercido no "
                        + "período: " + papeis);
            }
            if (concessoesDistintas > 1) {
                pontos.add("a concessão mudou " + (concessoesDistintas - 1)
                        + " vez(es) no período — confira se foi autorizado");
            }
            // SoD do cap. 15.1: quem cadastra a regra não aprova a exceção sobre
            // ela. Ter os dois papéis não é ilegal e é concentração — o
            // relatório aponta, não bloqueia.
            if (papeis.contains("APROVADOR_DAF") && papeis.contains("CURADOR_MATRIZ")) {
                pontos.add("acumula CURADOR_MATRIZ e APROVADOR_DAF: cadastra a regra e "
                        + "aprova a exceção sobre ela (concentração, cap. 15.1)");
            }
            if (papeis.stream().anyMatch(p -> p.startsWith("SVC_"))) {
                pontos.add("conta de serviço: login interativo é negado pelo cap. 15.1 e "
                        + "ela aparece aqui como tendo entrado");
            }
            return List.copyOf(pontos);
        }

        public String situacao() {
            if (!atencao().isEmpty()) {
                return "REVISAR";
            }
            return somenteLeitura() ? "SOMENTE_LEITURA" : "ATIVO";
        }
    }
}
