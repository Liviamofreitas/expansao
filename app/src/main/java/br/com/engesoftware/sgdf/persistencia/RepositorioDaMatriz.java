package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.matriz.Alocacao;
import br.com.engesoftware.sgdf.matriz.Calendario;
import br.com.engesoftware.sgdf.matriz.ExigenciaResolvida;
import br.com.engesoftware.sgdf.matriz.Prazo;
import br.com.engesoftware.sgdf.matriz.Regra;
import br.com.engesoftware.sgdf.matriz.Resolvedor;
import br.com.engesoftware.sgdf.matriz.TipoDoCadastro;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Materializa as exigências de um ciclo — cap. 7.1, história F0-07.
 *
 * <p>Só a metade mecânica: o QUE criar é decidido pelo {@link Resolvedor}, que é
 * função pura e responde à suíte normativa. Aqui se lê o cadastro, se grava o
 * resultado e se abrem as pendências.
 *
 * <p><b>A garantia da F0-05 mora nesta classe, numa linha.</b> As regras são
 * lidas por {@code versao_matriz_id = <a versão que o ciclo congelou>}, nunca
 * "a versão vigente". É isso que faz <i>"alterar regra não afeta ciclo aberto"</i>
 * ser verdade: publicar a versão 2.0 enquanto abril está aberto não muda uma
 * exigência de abril, porque abril continua perguntando pela 1.0. Ler pela versão
 * corrente pareceria mais simples e faria o reprocessamento de um ciclo antigo
 * produzir uma lista de exigências que ninguém jamais cobrou.
 */
public final class RepositorioDaMatriz {

    private final Sgdf sgdf;

    public RepositorioDaMatriz(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Resolve e grava as exigências do ciclo.
     *
     * <p>Idempotente: reexecutar não duplica. Os dois índices únicos PARCIAIS do
     * esquema — {@code ux_exigencia_de_ciclo} e {@code ux_exigencia_corporativa}
     * — fazem o trabalho, e é deliberado que façam: a abertura automática do
     * cap. 7.6 roda por agendador, e agendador repete.
     *
     * <p>Os dois {@code ON CONFLICT} repetem o {@code WHERE} do índice porque é
     * assim que se infere um índice parcial. Sem ele o PostgreSQL não encontra
     * o índice e a gravação falha — e falharia só quando houvesse conflito, ou
     * seja, na segunda execução, que é a que ninguém testa à mão.
     */
    public Materializacao materializar(UUID cicloId, String ator) {
        Ciclo ciclo = ciclo(cicloId);
        Map<String, TipoDoCadastro> tipos = tipos();
        List<Regra> regras = regrasDaVersao(ciclo.versaoMatrizId());
        List<Alocacao> alocacoes = alocacoes(ciclo.contratoId());
        Calendario calendario = calendario(ciclo.calendarioUf());

        Resolvedor.Abertura abertura = Resolvedor.abrirCiclo(
                new Resolvedor.Contrato(ciclo.numero(), ciclo.modalidade()),
                ciclo.competencia(), tipos, regras, alocacoes, contexto(ciclo), calendario);

        Map<String, UUID> profissionais = profissionaisDoContrato(ciclo.contratoId());

        return sgdf.emTransacao(conexao -> {
            int criadas = 0;
            int pendencias = 0;
            for (ExigenciaResolvida e : abertura.exigencias()) {
                UUID id = gravar(conexao, ciclo, e, tipos, profissionais, ator);
                if (id != null) {
                    criadas++;
                    if (e.prazo() != null && abrirPendencia(conexao, id, e.prazo())) {
                        pendencias++;
                    }
                }
            }
            TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                    ator, "SVC_ABERTURA", "CICLO_MATERIALIZAR", "ciclo", cicloId.toString(),
                    Map.of("versao_matriz", List.of(ciclo.versaoMatrizId().toString()),
                            "resolvidas", List.of(String.valueOf(abertura.exigencias().size())),
                            "criadas", List.of(String.valueOf(criadas)),
                            "alertas", abertura.alertas())));
            return new Materializacao(cicloId, ciclo.versaoMatrizId(),
                    abertura.exigencias().size(), criadas, pendencias, abertura.alertas());
        });
    }

    /**
     * Grava uma exigência, no endereçamento que o escopo pede (V004).
     *
     * @return o id quando criou; nulo quando já existia
     */
    private UUID gravar(Connection conexao, Ciclo ciclo, ExigenciaResolvida e,
                        Map<String, TipoDoCadastro> tipos, Map<String, UUID> profissionais,
                        String ator) {
        UUID tipoId = tipoId(conexao, e.tipo());
        if ("CORPORATIVO".equals(e.escopo())) {
            if (ciclo.empresaId() == null) {
                throw new Sgdf.FalhaDePersistencia(
                        "exigência corporativa exige empresa emitente no contrato "
                        + ciclo.numero() + " (V004)", null);
            }
            return gravarCorporativa(conexao, ciclo, e, tipoId, ator);
        }
        UUID profissionalId = null;
        if (e.profissional() != null) {
            profissionalId = profissionais.get(e.profissional());
            if (profissionalId == null) {
                throw new Sgdf.FalhaDePersistencia("matrícula " + e.profissional()
                        + " alocada no contrato mas ausente do cadastro de profissional", null);
            }
        }
        String sql = """
                INSERT INTO exigencia (ciclo_id, tipo_id, evento, profissional_id, status,
                                       prazo_calculado, criticidade, condicional_grupo,
                                       responsavel, origem, criado_por)
                VALUES (?, ?, ?, ?, 'PENDENTE', ?, ?, ?, ?, 'MATRIZ', ?)
                ON CONFLICT (ciclo_id, tipo_id, evento, profissional_id)
                    WHERE ciclo_id IS NOT NULL
                DO NOTHING
                RETURNING id
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, ciclo.id());
            ps.setObject(2, tipoId);
            ps.setString(3, e.evento());
            ps.setObject(4, profissionalId);
            // O prazo pode ser nulo (âncora aguardando evento), mas a coluna é
            // NOT NULL: a exigência sem prazo entra com o fim da competência e o
            // aviso na trilha, porque suprimi-la esconderia o que falta (cap. 7.3).
            ps.setObject(5, e.prazo() != null ? e.prazo() : fimDaCompetencia(ciclo));
            ps.setString(6, e.criticidade());
            ps.setString(7, e.condicionalGrupo());
            ps.setString(8, e.responsavel());
            ps.setString(9, ator);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException ex) {
            throw new Sgdf.FalhaDePersistencia("falha ao gravar a exigência " + e.tipo(), ex);
        }
    }

    /**
     * A exigência corporativa é uma só por (empresa, competência) — cap. 7.1.
     *
     * <p>Quando dois contratos da mesma empresa a exigem com prazos diferentes,
     * fica o MENOR: se um cliente quer a CND no 5º dia útil e outro no 10º, a
     * certidão compartilhada precisa estar lá no 5º. O maior faria o cliente mais
     * exigente receber tarde — ver ERRATA E-10 e {@code prazoCorporativo}.
     */
    private UUID gravarCorporativa(Connection conexao, Ciclo ciclo, ExigenciaResolvida e,
                                   UUID tipoId, String ator) {
        String sql = """
                INSERT INTO exigencia (empresa_id, competencia, tipo_id, evento, status,
                                       prazo_calculado, criticidade, condicional_grupo,
                                       responsavel, origem, criado_por)
                VALUES (?, ?, ?, ?, 'PENDENTE', ?, ?, ?, ?, 'MATRIZ', ?)
                ON CONFLICT (empresa_id, competencia, tipo_id, evento)
                    WHERE empresa_id IS NOT NULL
                DO UPDATE SET prazo_calculado = LEAST(exigencia.prazo_calculado,
                                                      EXCLUDED.prazo_calculado),
                              atualizado_em = now(), atualizado_por = EXCLUDED.criado_por
                RETURNING id, (xmax = 0) AS inserida
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, ciclo.empresaId());
            ps.setString(2, ciclo.competencia());
            ps.setObject(3, tipoId);
            ps.setString(4, e.evento());
            ps.setObject(5, e.prazo() != null ? e.prazo() : fimDaCompetencia(ciclo));
            ps.setString(6, e.criticidade());
            ps.setString(7, e.condicionalGrupo());
            ps.setString(8, e.responsavel());
            ps.setString(9, ator);
            try (ResultSet rs = ps.executeQuery()) {
                // `inserida` distingue criação de reaproveitamento: o segundo
                // contrato da mesma empresa não cria exigência nova, e contá-la
                // como criada faria o painel dizer que há 15 CNDs onde há uma.
                return rs.next() && rs.getBoolean(2) ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException ex) {
            throw new Sgdf.FalhaDePersistencia("falha ao gravar a exigência corporativa "
                    + e.tipo(), ex);
        }
    }

    /** Uma pendência aberta por exigência — {@code ux_pendencia_aberta}. */
    private boolean abrirPendencia(Connection conexao, UUID exigenciaId, LocalDate prazo) {
        String sql = """
                INSERT INTO pendencia (exigencia_id, prazo)
                SELECT ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM pendencia
                                  WHERE exigencia_id = ? AND resolvida_em IS NULL)
                """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, exigenciaId);
            ps.setObject(2, prazo);
            ps.setObject(3, exigenciaId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao abrir a pendência", e);
        }
    }

    // --- leituras do cadastro ------------------------------------------------

    Ciclo ciclo(UUID cicloId) {
        String sql = """
                SELECT c.id, c.competencia, c.versao_matriz_id, cs.id, cs.numero,
                       m.codigo, cs.calendario_uf, cs.empresa_id, c.ateste_em::date
                FROM   ciclo c
                JOIN   contrato_servico cs ON cs.id = c.contrato_servico_id
                JOIN   modalidade m        ON m.id = cs.modalidade_id
                WHERE  c.id = ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new Sgdf.FalhaDePersistencia("ciclo " + cicloId + " não existe", null);
                }
                return new Ciclo(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getObject(8, UUID.class), rs.getObject(9, LocalDate.class));
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o ciclo", e);
        }
    }

    Map<String, TipoDoCadastro> tipos() {
        String sql = """
                SELECT codigo, escopo, evento, criticidade, condicional_grupo
                FROM   tipo_documental WHERE ativo
                """;
        Map<String, TipoDoCadastro> tipos = new LinkedHashMap<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tipos.put(rs.getString(1), new TipoDoCadastro(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)));
            }
            return tipos;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os tipos documentais", e);
        }
    }

    /**
     * As regras DA VERSÃO que o ciclo congelou — nunca as vigentes agora.
     *
     * <p>É a linha que sustenta a F0-05. Ver o javadoc da classe.
     */
    List<Regra> regrasDaVersao(UUID versaoMatrizId) {
        String sql = """
                SELECT t.codigo, t.evento, r.alvo,
                       coalesce(m.codigo, cs.numero) AS alvo_id,
                       r.obrigatoriedade, r.criticidade,
                       r.prazo->>'ancora', r.prazo->>'tipo_dia', r.prazo->'offset',
                       r.responsavel_titular, r.vigencia_ini, r.vigencia_fim
                FROM   regra_exigibilidade r
                JOIN   tipo_documental t      ON t.id = r.tipo_id
                LEFT   JOIN modalidade m      ON m.id = r.alvo_modalidade_id
                LEFT   JOIN contrato_servico cs ON cs.id = r.alvo_contrato_id
                WHERE  r.versao_matriz_id = ?
                """;
        List<Regra> regras = new ArrayList<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, versaoMatrizId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // O offset vem como texto do jsonb e é convertido AQUI, com
                    // a fábrica estrita: `true` no cadastro não pode virar 1.
                    regras.add(new Regra(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6),
                            Prazo.Cadastrado.deValores(rs.getString(7), rs.getString(8),
                                    inteiro(rs.getString(9))),
                            rs.getString(10), rs.getObject(11, LocalDate.class),
                            rs.getObject(12, LocalDate.class)));
                }
            }
            return regras;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as regras da versão", e);
        }
    }

    /** O jsonb entrega texto; só um inteiro puro vira offset. */
    private static Object inteiro(String bruto) {
        if (bruto == null) {
            return null;
        }
        return bruto.matches("-?\\d+") ? Integer.valueOf(bruto) : bruto;
    }

    List<Alocacao> alocacoes(UUID contratoId) {
        String sql = """
                SELECT p.matricula, a.inicio, a.fim
                FROM   alocacao a
                JOIN   profissional p ON p.id = a.profissional_id
                WHERE  a.contrato_servico_id = ?
                """;
        List<Alocacao> alocacoes = new ArrayList<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, contratoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    alocacoes.add(new Alocacao(rs.getString(1), rs.getObject(2, LocalDate.class),
                            rs.getObject(3, LocalDate.class)));
                }
            }
            return alocacoes;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as alocações", e);
        }
    }

    Map<String, UUID> profissionaisDoContrato(UUID contratoId) {
        Map<String, UUID> mapa = new HashMap<>();
        String sql = """
                SELECT DISTINCT p.matricula, p.id
                FROM   alocacao a JOIN profissional p ON p.id = a.profissional_id
                WHERE  a.contrato_servico_id = ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, contratoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString(1), rs.getObject(2, UUID.class));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os profissionais", e);
        }
    }

    /**
     * Feriados nacionais mais os da UF do contrato.
     *
     * <p>A resolução por escopo acontece aqui e não no {@link Calendario}: o
     * cálculo do prazo recebe o conjunto já resolvido e continua sendo função
     * pura, testável contra a suíte sem banco.
     */
    Calendario calendario(String uf) {
        String sql = "SELECT data FROM calendario_feriados WHERE uf IS NULL OR uf = ?";
        Set<LocalDate> feriados = new LinkedHashSet<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, uf);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    feriados.add(rs.getObject(1, LocalDate.class));
                }
            }
            return new Calendario(feriados);
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o calendário de feriados", e);
        }
    }

    private UUID tipoId(Connection conexao, String codigo) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT id FROM tipo_documental WHERE codigo = ?")) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new Sgdf.FalhaDePersistencia("tipo " + codigo + " não existe", null);
                }
                return rs.getObject(1, UUID.class);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao localizar o tipo " + codigo, e);
        }
    }

    private static Map<String, String> contexto(Ciclo ciclo) {
        Map<String, String> contexto = new HashMap<>();
        if (ciclo.ateste() != null) {
            contexto.put("ateste", ciclo.ateste().toString());
        }
        return contexto;
    }

    private static LocalDate fimDaCompetencia(Ciclo ciclo) {
        return java.time.YearMonth.parse(ciclo.competencia()).atEndOfMonth();
    }

    /** O ciclo e o contrato dele, no que a materialização precisa. */
    record Ciclo(UUID id, String competencia, UUID versaoMatrizId, UUID contratoId,
                 String numero, String modalidade, String calendarioUf, UUID empresaId,
                 LocalDate ateste) {
    }

    /**
     * O que a materialização fez.
     *
     * @param resolvidas quantas a regra determinou
     * @param criadas    quantas não existiam — a diferença é reaproveitamento
     *                   corporativo ou reexecução, e as duas são normais
     */
    public record Materializacao(UUID cicloId, UUID versaoMatriz, int resolvidas, int criadas,
                                 int pendencias, List<String> alertas) {

        public Materializacao {
            alertas = List.copyOf(alertas);
        }
    }
}
