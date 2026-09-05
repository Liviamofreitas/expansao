package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.matriz.MudancaDeCriticidade;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rascunho → publicação da matriz — história F0-05.
 *
 * <p>Critério de aceite: <i>"alterar regra não afeta ciclo aberto; histórico
 * lista versões com autor e motivo"</i>.
 *
 * <p><b>Publicar não promove o rascunho: publicar cria uma versão nova.</b> As
 * regras da versão base são COPIADAS para a versão nova, exceto as que o
 * rascunho remove ou altera; depois entram as incluídas e as alteradas. A base
 * fica intacta — e é o que faz o ciclo que a congelou continuar respondendo o
 * mesmo, que é a garantia da F0-05 provada em {@code TestesDeMatriz}.
 */
public final class RepositorioDeRascunho {

    private final Sgdf sgdf;

    public RepositorioDeRascunho(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Analisa o rascunho antes de publicar.
     *
     * <p>Devolve o que a tela precisa mostrar ANTES do clique: o que exige DAF e
     * quais ciclos abertos seguem na versão antiga. O cap. 12 pede um "aviso
     * fixo: alterações não travam o faturamento" — aqui ele deixa de ser texto
     * fixo e vira evidência: os ciclos são nomeados, com a versão em que ficam.
     */
    public Analise analisar(UUID rascunhoId) {
        Rascunho r = carregar(rascunhoId);
        List<MudancaDeCriticidade.Item> itens = itensComCriticidade(rascunhoId);
        return new Analise(rascunhoId, r.numeroProposto(), r.baseVersaoId(),
                MudancaDeCriticidade.exigemAprovacaoDaf(itens), itens.size(),
                ciclosAbertosNaBase(r.baseVersaoId()));
    }

    /**
     * Publica: cria a versão e aplica os itens.
     *
     * @param aprovadorDaf o ator tem {@code ALTERAR_CRITICIDADE} (cap. 15.1)
     * @throws ExigeAprovacaoDaf quando o rascunho toca criticidade bloqueante e
     *                           quem publica não é APROVADOR_DAF
     */
    public Publicada publicar(UUID rascunhoId, String ator, String papel,
                              boolean aprovadorDaf) {
        Analise analise = analisar(rascunhoId);
        if (!analise.exigemDaf().isEmpty() && !aprovadorDaf) {
            // Barrado ANTES da transação: nada é criado, e a trilha registra a
            // tentativa negada — que é o que permite ver alguém insistindo.
            registrarNegado(ator, papel, rascunhoId, analise.exigemDaf());
            throw new ExigeAprovacaoDaf("a publicação altera criticidade bloqueante e exige "
                    + "APROVADOR_DAF (cap. 12): " + String.join("; ", analise.exigemDaf()));
        }
        Rascunho r = carregar(rascunhoId);
        if (!"ABERTO".equals(r.situacao())) {
            throw new RascunhoJaFechado("o rascunho " + rascunhoId + " está " + r.situacao()
                    + ". Publicar de novo criaria uma segunda versão a partir da mesma "
                    + "proposta, e o histórico deixaria de dizer o que gerou o quê");
        }

        return sgdf.emTransacao(conexao -> {
            UUID versao = criarVersao(conexao, r, ator);
            int copiadas = copiarDaBase(conexao, r, versao);
            int aplicadas = aplicarItens(conexao, rascunhoId, versao, ator);
            fechar(conexao, rascunhoId, versao, ator);

            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, papel, "MATRIZ_PUBLICAR", "versao_matriz", versao.toString(),
                    Map.of("rascunho", List.of(rascunhoId.toString()),
                            "numero", List.of(r.numeroProposto()),
                            "motivo", List.of(r.motivo()),
                            "copiadas", List.of(String.valueOf(copiadas)),
                            "aplicadas", List.of(String.valueOf(aplicadas)),
                            "criticidade_bloqueante", analise.exigemDaf())));

            return new Publicada(versao, r.numeroProposto(), copiadas + aplicadas,
                    analise.ciclosAbertosNaBase());
        });
    }

    /** O histórico do critério de aceite: versões com autor e motivo. */
    public List<VersaoNoHistorico> historico(int limite) {
        String sql = """
                SELECT v.id, v.numero, v.publicada_em, v.publicada_por, v.motivo,
                       (SELECT count(*) FROM regra_exigibilidade r
                         WHERE r.versao_matriz_id = v.id),
                       (SELECT count(*) FROM ciclo c WHERE c.versao_matriz_id = v.id),
                       rm.criado_por
                FROM   versao_matriz v
                LEFT   JOIN rascunho_matriz rm ON rm.versao_publicada_id = v.id
                ORDER  BY v.publicada_em DESC
                LIMIT  ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<VersaoNoHistorico> historico = new ArrayList<>();
                while (rs.next()) {
                    historico.add(new VersaoNoHistorico(rs.getObject(1, UUID.class),
                            rs.getString(2), rs.getObject(3, java.time.OffsetDateTime.class),
                            rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7),
                            rs.getString(8)));
                }
                return historico;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o histórico da matriz", e);
        }
    }

    // --- a publicação, passo a passo -----------------------------------------

    private UUID criarVersao(Connection conexao, Rascunho r, String ator) {
        String sql = """
                INSERT INTO versao_matriz (numero, publicada_por, motivo)
                VALUES (?, ?, ?) RETURNING id
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, r.numeroProposto());
            ps.setString(2, ator);
            ps.setString(3, r.motivo());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        } catch (SQLException e) {
            throw new NumeroJaUsado("já existe versão da matriz com o número \""
                    + r.numeroProposto() + "\"", e);
        }
    }

    /**
     * Copia da base tudo o que o rascunho não toca.
     *
     * <p>A versão nova é COMPLETA, não um delta: {@code regra_exigibilidade} é
     * lida por {@code versao_matriz_id = ?}, e uma versão que só contivesse as
     * mudanças faria o ciclo aberto nela enxergar três regras onde a matriz tem
     * 176.
     */
    private int copiarDaBase(Connection conexao, Rascunho r, UUID versao) {
        if (r.baseVersaoId() == null) {
            return 0;
        }
        String sql = """
                INSERT INTO regra_exigibilidade
                       (tipo_id, alvo, alvo_modalidade_id, alvo_contrato_id, obrigatoriedade,
                        criticidade, prazo, responsavel_titular, responsavel_substituto,
                        fundamento, vigencia_ini, vigencia_fim, versao_matriz_id, criado_por)
                SELECT b.tipo_id, b.alvo, b.alvo_modalidade_id, b.alvo_contrato_id,
                       b.obrigatoriedade, b.criticidade, b.prazo, b.responsavel_titular,
                       b.responsavel_substituto, b.fundamento, b.vigencia_ini, b.vigencia_fim,
                       ?, b.criado_por
                FROM   regra_exigibilidade b
                WHERE  b.versao_matriz_id = ?
                  AND  NOT EXISTS (SELECT 1 FROM rascunho_regra i
                                    WHERE i.rascunho_id = ? AND i.regra_origem_id = b.id)
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, versao);
            ps.setObject(2, r.baseVersaoId());
            ps.setObject(3, r.id());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao copiar as regras da versão base", e);
        }
    }

    /** INCLUIR e ALTERAR viram regra na versão nova; REMOVER apenas não é copiada. */
    private int aplicarItens(Connection conexao, UUID rascunhoId, UUID versao, String ator) {
        String sql = """
                INSERT INTO regra_exigibilidade
                       (tipo_id, alvo, alvo_modalidade_id, alvo_contrato_id, obrigatoriedade,
                        criticidade, prazo, responsavel_titular, responsavel_substituto,
                        fundamento, vigencia_ini, vigencia_fim, versao_matriz_id, criado_por)
                SELECT i.tipo_id, i.alvo, i.alvo_modalidade_id, i.alvo_contrato_id,
                       i.obrigatoriedade, i.criticidade, i.prazo, i.responsavel_titular,
                       i.responsavel_substituto, i.fundamento, i.vigencia_ini, i.vigencia_fim,
                       ?, ?
                FROM   rascunho_regra i
                WHERE  i.rascunho_id = ? AND i.acao <> 'REMOVER'
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, versao);
            ps.setString(2, ator);
            ps.setObject(3, rascunhoId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao aplicar os itens do rascunho", e);
        }
    }

    private void fechar(Connection conexao, UUID rascunhoId, UUID versao, String ator) {
        String sql = """
                UPDATE rascunho_matriz
                SET    situacao = 'PUBLICADO', publicado_em = now(), publicado_por = ?,
                       versao_publicada_id = ?
                WHERE  id = ? AND situacao = 'ABERTO'
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, ator);
            ps.setObject(2, versao);
            ps.setObject(3, rascunhoId);
            if (ps.executeUpdate() != 1) {
                // Outra publicação venceu a corrida. Desfaz tudo: a versão que
                // esta transação criou não pode ficar sem o rascunho que a
                // explica, ou o histórico passa a ter uma versão órfã.
                throw new RascunhoJaFechado("o rascunho " + rascunhoId
                        + " foi fechado por outra publicação");
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao fechar o rascunho", e);
        }
    }

    // --- leituras ------------------------------------------------------------

    Rascunho carregar(UUID id) {
        String sql = """
                SELECT id, numero_proposto, motivo, base_versao_id, situacao
                FROM   rascunho_matriz WHERE id = ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new Sgdf.FalhaDePersistencia("rascunho " + id + " não existe", null);
                }
                return new Rascunho(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o rascunho", e);
        }
    }

    /**
     * Os itens com a criticidade EFETIVA dos dois lados.
     *
     * <p>{@code coalesce(criticidade, tipo.criticidade)} nos dois lados porque
     * nula na regra significa "herda do tipo" (V001). Comparar as nulas
     * diretamente diria que nada mudou quando o tipo por trás é bloqueante.
     */
    List<MudancaDeCriticidade.Item> itensComCriticidade(UUID rascunhoId) {
        String sql = """
                SELECT i.acao, t.codigo,
                       CASE WHEN i.acao = 'INCLUIR' THEN NULL
                            ELSE coalesce(b.criticidade, tb.criticidade) END AS atual,
                       CASE WHEN i.acao = 'REMOVER' THEN NULL
                            ELSE coalesce(i.criticidade, t.criticidade) END AS proposta
                FROM   rascunho_regra i
                LEFT   JOIN tipo_documental t ON t.id = i.tipo_id
                LEFT   JOIN regra_exigibilidade b ON b.id = i.regra_origem_id
                LEFT   JOIN tipo_documental tb ON tb.id = b.tipo_id
                WHERE  i.rascunho_id = ?
                ORDER  BY t.codigo
                """;
        List<MudancaDeCriticidade.Item> itens = new ArrayList<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, rascunhoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    itens.add(new MudancaDeCriticidade.Item(rs.getString(1),
                            rs.getString(2) == null ? "(regra removida)" : rs.getString(2),
                            rs.getString(3), rs.getString(4)));
                }
            }
            return itens;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os itens do rascunho", e);
        }
    }

    /** Os ciclos que continuam na versão base — a evidência do aviso do cap. 12. */
    List<String> ciclosAbertosNaBase(UUID baseVersaoId) {
        if (baseVersaoId == null) {
            return List.of();
        }
        String sql = """
                SELECT cs.numero || ' ' || c.competencia
                FROM   ciclo c JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
                WHERE  c.versao_matriz_id = ?
                  AND  c.status NOT IN ('FECHADO', 'FATURADO')
                ORDER  BY 1
                """;
        List<String> ciclos = new ArrayList<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, baseVersaoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ciclos.add(rs.getString(1));
                }
            }
            return ciclos;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os ciclos da versão base", e);
        }
    }

    private void registrarNegado(String ator, String papel, UUID rascunhoId,
                                 List<String> motivos) {
        sgdf.emTransacao(conexao -> {
            TrilhaDeAuditoria.registrar(conexao, new TrilhaDeAuditoria.Registro(
                    ator, papel, "MATRIZ_PUBLICAR", "rascunho_matriz", rascunhoId.toString(),
                    "NEGADO", Map.of("criticidade_bloqueante", motivos)));
            return null;
        });
    }

    record Rascunho(UUID id, String numeroProposto, String motivo, UUID baseVersaoId,
                    String situacao) {
    }

    /**
     * O que a tela mostra antes do clique.
     *
     * @param exigemDaf          vazio quando o CURADOR_MATRIZ publica sozinho
     * @param ciclosAbertosNaBase quem continua na versão antiga — cap. 12
     */
    public record Analise(UUID rascunhoId, String numeroProposto, UUID baseVersaoId,
                          List<String> exigemDaf, int itens, List<String> ciclosAbertosNaBase) {

        public Analise {
            exigemDaf = List.copyOf(exigemDaf);
            ciclosAbertosNaBase = List.copyOf(ciclosAbertosNaBase);
        }
    }

    /**
     * @param naoAfetados ciclos que continuam na versão anterior — o "alterações
     *                    não travam o faturamento" do cap. 12, nomeado
     */
    public record Publicada(UUID versaoId, String numero, int regras, List<String> naoAfetados) {

        public Publicada {
            naoAfetados = List.copyOf(naoAfetados);
        }
    }

    /**
     * Uma linha do histórico da F0-05.
     *
     * @param regras   quantas regras a versão tem — o número que denuncia uma
     *                 publicação que copiou errado
     * @param ciclos   quantos ciclos congelaram esta versão; versão com ciclos
     *                 é versão que ainda responde perguntas
     * @param propostaPor quem abriu o rascunho; nulo nas versões anteriores à F0-05
     */
    public record VersaoNoHistorico(UUID id, String numero,
                                    java.time.OffsetDateTime publicadaEm,
                                    String publicadaPor, String motivo, int regras, int ciclos,
                                    String propostaPor) {
    }

    /** Cap. 12: bloqueante pede aprovação DAF. */
    public static final class ExigeAprovacaoDaf extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ExigeAprovacaoDaf(String motivo) {
            super(motivo);
        }
    }

    /** Publicar duas vezes o mesmo rascunho. */
    public static final class RascunhoJaFechado extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RascunhoJaFechado(String motivo) {
            super(motivo);
        }
    }

    /** O número proposto já pertence a outra versão. */
    public static final class NumeroJaUsado extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NumeroJaUsado(String motivo, Throwable causa) {
            super(motivo, causa);
        }
    }
}
