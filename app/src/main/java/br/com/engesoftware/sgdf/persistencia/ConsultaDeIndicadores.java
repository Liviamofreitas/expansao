package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.indicadores.Csv;
import br.com.engesoftware.sgdf.indicadores.Indicador;
import br.com.engesoftware.sgdf.indicadores.Meta;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Os nove indicadores do cap. 21, por competência — história F3-05.
 *
 * <p>Critério de aceite: <i>"KPI/KRI calculados por competência, exportáveis em
 * CSV"</i>.
 *
 * <p><b>Cada indicador tem uma população, e é sempre ali que a conta erra.</b>
 * O erro não aparece como número absurdo — aparece como número bonito. Os três
 * casos que este arquivo evita explicitamente:
 *
 * <ul>
 *   <li><b>Denominador que inclui quem ainda tem prazo.</b> O D+3 conta sobre
 *       ciclos <i>faturados</i>: incluir os que não faturaram faria o percentual
 *       cair no começo do mês e subir sozinho no fim, medindo o calendário.</li>
 *   <li><b>Denominador que inclui quem não foi julgado.</b> A precisão de
 *       triagem conta as decisões que <i>julgaram a sugestão de tipo</i>.
 *       ILEGÍVEL não diz nada sobre o tipo — o documento não pôde ser lido —, e
 *       somá-la ao denominador puniria o classificador por um defeito de
 *       digitalização.</li>
 *   <li><b>Zero de zero.</b> Toda razão devolve valor nulo sem população, e a
 *       situação vira SEM_POPULACAO. Um mês que começou hoje não descumpriu
 *       nada.</li>
 * </ul>
 */
public final class ConsultaDeIndicadores {

    private final Sgdf sgdf;

    public ConsultaDeIndicadores(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /** Os nove do capítulo, na ordem da tabela. */
    public List<Indicador> daCompetencia(String competencia) {
        List<Indicador> indicadores = new ArrayList<>();
        indicadores.add(nfEmD3(competencia));
        indicadores.add(tempoAtesteNf(competencia));
        indicadores.add(completudeNaPrimeiraConferencia(competencia));
        indicadores.add(pendenciasVencidas(competencia));
        indicadores.add(excecoesAprovadas(competencia));
        indicadores.add(divergenciasDeConciliacao(competencia));
        indicadores.add(idadeDaCertidaoNaNf(competencia));
        indicadores.add(precisaoDeClassificacao(competencia));
        indicadores.add(booksRepublicados(competencia));
        return List.copyOf(indicadores);
    }

    // --- 1 e 2: os dois do ciclo (já verificados na F3-03) --------------------

    private Indicador nfEmD3(String competencia) {
        int[] c = doisInteiros("""
                SELECT count(*) FILTER (WHERE nf_emitida_em IS NOT NULL),
                       count(*) FILTER (WHERE nf_emitida_em IS NOT NULL
                                          AND nf_emitida_em <= ateste_em + INTERVAL '3 days')
                FROM   ciclo WHERE competencia = ? AND ateste_em IS NOT NULL
                """, competencia);
        return Indicador.razao("D3", "NF em D+3 (política)", c[1], c[0], Meta.noMinimo("98"),
                "ciclo", "denominador: ciclos faturados. O que ainda não faturou não está "
                        + "fora do prazo — está fora da conta");
    }

    private Indicador tempoAtesteNf(String competencia) {
        String sql = """
                SELECT count(*), avg(EXTRACT(EPOCH FROM (nf_emitida_em - ateste_em)) / 86400.0)
                FROM   ciclo
                WHERE  competencia = ? AND ateste_em IS NOT NULL AND nf_emitida_em IS NOT NULL
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, competencia);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal media = rs.getBigDecimal(2);
                return Indicador.media("TEMPO_ATESTE_NF", "Tempo ateste → NF",
                        media == null ? null : media.setScale(2, RoundingMode.HALF_UP),
                        rs.getInt(1), Meta.noMaximo("2"), "ciclo", null);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao calcular o tempo ateste→NF", e);
        }
    }

    // --- 3: completude na 1ª conferência --------------------------------------

    /**
     * Exigências cujo documento já estava lá quando o prazo passou.
     *
     * <p><b>Leitura declarada.</b> O capítulo diz "satisfeitas na 1ª varredura
     * pós-prazo", e não há registro de varredura por exigência — nada roda por
     * agendador ainda (RA-07). Medir por "nunca escalonada" seria pior que não
     * medir: a régua de notificação é quem escalona, ela não roda, e o
     * indicador daria <b>100% justamente porque nada aconteceu</b>.
     *
     * <p>A leitura adotada não depende de agendador nenhum: o documento foi
     * vinculado até a data do prazo. É observável só com o que está gravado, e
     * responde à mesma pergunta — chegou a tempo ou foi preciso cobrar.
     */
    private Indicador completudeNaPrimeiraConferencia(String competencia) {
        int[] c = doisInteiros("""
                SELECT count(*),
                       count(*) FILTER (WHERE EXISTS (
                           SELECT 1 FROM vinculo_exigencia_documento v
                           WHERE v.exigencia_id = e.id
                             AND v.decidido_em::date <= e.prazo_calculado))
                FROM   exigencia e
                JOIN   ciclo c ON c.id = e.ciclo_id
                WHERE  c.competencia = ?
                """, competencia);
        return Indicador.razao("COMPLETUDE_1A", "Completude na 1ª conferência", c[1], c[0],
                Meta.monitorar(), "exigencia",
                "leitura declarada: documento vinculado até o prazo. Não usa 'nunca "
                        + "escalonada' porque a régua que escalona ainda não roda (RA-07), e "
                        + "o indicador daria 100% por nada ter acontecido");
    }

    // --- 4: pendências vencidas ------------------------------------------------

    private Indicador pendenciasVencidas(String competencia) {
        int total = umInteiro("""
                SELECT count(*)
                FROM   pendencia p
                JOIN   exigencia e ON e.id = p.exigencia_id
                JOIN   ciclo c ON c.id = e.ciclo_id
                WHERE  c.competencia = ? AND p.resolvida_em IS NULL
                  AND  p.prazo < current_date
                """, competencia);
        return Indicador.contagem("PENDENCIAS_VENCIDAS", "Pendências vencidas", total,
                Meta.exatamente("0"), "pendencia",
                "abertas com prazo vencido. O recorte por área está em porArea()");
    }

    /** O detalhamento que o capítulo pede: "contagem por responsável". */
    public List<Object[]> pendenciasVencidasPorArea(String competencia) {
        return linhas("""
                SELECT e.responsavel, count(*)
                FROM   pendencia p
                JOIN   exigencia e ON e.id = p.exigencia_id
                JOIN   ciclo c ON c.id = e.ciclo_id
                WHERE  c.competencia = ? AND p.resolvida_em IS NULL AND p.prazo < current_date
                GROUP  BY e.responsavel ORDER BY count(*) DESC, e.responsavel
                """, competencia, 2);
    }

    // --- 5 e 6: os dois KRI de contagem ---------------------------------------

    private Indicador excecoesAprovadas(String competencia) {
        int total = umInteiro("""
                SELECT count(*)
                FROM   excecao x
                JOIN   exigencia e ON e.id = x.exigencia_id
                JOIN   ciclo c ON c.id = e.ciclo_id
                WHERE  c.competencia = ? AND x.situacao = 'APROVADA'
                """, competencia);
        return Indicador.contagem("EXCECOES_APROVADAS", "Exceções aprovadas (KRI)", total,
                Meta.monitorar(), "excecao",
                "alta = controle contornado. Não há número certo: a leitura é a série");
    }

    private Indicador divergenciasDeConciliacao(String competencia) {
        int total = umInteiro("""
                SELECT count(*)
                FROM   conciliacao k
                JOIN   ciclo c ON c.id = k.ciclo_id
                WHERE  c.competencia = ? AND k.resultado = 'DIVERGENTE'
                """, competencia);
        return Indicador.contagem("DIVERGENCIAS", "Divergências de conciliação (KRI)", total,
                Meta.monitorar(), "conciliacao", "detalhamento por regra e contrato em "
                        + "divergenciasPorRegra()");
    }

    /** "contagem por regra e contrato" — o recorte do capítulo. */
    public List<Object[]> divergenciasPorRegra(String competencia) {
        return linhas("""
                SELECT r.codigo, cs.numero, count(*)
                FROM   conciliacao k
                JOIN   ciclo c ON c.id = k.ciclo_id
                JOIN   contrato_servico cs ON cs.id = c.contrato_servico_id
                JOIN   regra_conciliacao r ON r.id = k.regra_id
                WHERE  c.competencia = ? AND k.resultado = 'DIVERGENTE'
                GROUP  BY r.codigo, cs.numero ORDER BY count(*) DESC, r.codigo, cs.numero
                """, competencia, 3);
    }

    // --- 7: idade da certidão na emissão da NF ---------------------------------

    /**
     * Menor folga entre validade da certidão e emissão da NF — meta &gt; 5 dias.
     *
     * <p><b>O mínimo, não a média.</b> A média esconderia exatamente o caso que
     * o KRI existe para achar: nove certidões com 60 dias de folga e uma com um
     * dia dão média de 54, e é a de um dia que vence antes de o cliente pagar.
     * O risco é do pior caso, e o indicador tem de ser o pior caso.
     *
     * <p><b>O filtro é ter validade, não pertencer à família "certidão".</b> A
     * primeira versão comparava {@code t.familia = 'CERTIDAO'} e a carga real
     * grava <i>"Certidões e regularidade"</i> — a consulta lia zero linha e o
     * indicador respondia SEM_POPULACAO, que se lê como "não havia certidão"
     * e não como "meu filtro está errado". Um rótulo de exibição não é chave:
     * ele muda por decisão de quem cadastra, e a consulta quebraria em
     * silêncio de novo. Ter {@code validade_extraida} é a propriedade que o
     * indicador realmente precisa — é o vencimento que cria o risco.
     */
    private Indicador idadeDaCertidaoNaNf(String competencia) {
        String sql = """
                SELECT count(*), min(d.validade_extraida - c.nf_emitida_em::date)
                FROM   ciclo c
                JOIN   exigencia e ON e.ciclo_id = c.id
                JOIN   vinculo_exigencia_documento v ON v.exigencia_id = e.id
                JOIN   documento d ON d.id = v.documento_id
                WHERE  c.competencia = ? AND c.nf_emitida_em IS NOT NULL
                  AND  d.validade_extraida IS NOT NULL
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, competencia);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int populacao = rs.getInt(1);
                BigDecimal folga = rs.getBigDecimal(2);
                return Indicador.media("IDADE_CERTIDAO", "Folga da certidão na emissão da NF "
                        + "(KRI)", folga, populacao, Meta.noMinimo("5"), "documento",
                        "o MÍNIMO, não a média: a média esconde a certidão que vence antes "
                                + "de o cliente pagar");
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao calcular a folga da certidão", e);
        }
    }

    // --- 8: precisão de classificação -----------------------------------------

    /**
     * Confirmações que mantiveram a sugestão / decisões que a julgaram.
     *
     * <p><b>ILEGÍVEL fica fora do denominador, e é a decisão que faz a conta
     * medir o que diz medir.</b> "Este documento está ilegível" não é um
     * veredito sobre o tipo sugerido — é sobre a digitalização. Somá-la
     * debitaria do classificador um defeito de scanner, e a métrica pioraria
     * quando a origem mandasse fotocópia ruim.
     *
     * <p>REJEITADA entra: dizer "este documento não é desta exigência" É um
     * julgamento contra a sugestão.
     */
    private Indicador precisaoDeClassificacao(String competencia) {
        int[] c = doisInteiros("""
                SELECT count(*) FILTER (WHERE k.situacao IN ('CONFIRMADA', 'RECLASSIFICADA',
                                                             'REJEITADA')),
                       count(*) FILTER (WHERE k.situacao = 'CONFIRMADA')
                FROM   candidatura k
                JOIN   exigencia e ON e.id = k.exigencia_id
                JOIN   ciclo c ON c.id = e.ciclo_id
                WHERE  c.competencia = ?
                """, competencia);
        return Indicador.razao("PRECISAO_TRIAGEM", "Precisão de classificação", c[1], c[0],
                Meta.noMinimo("90"), "triagem",
                "denominador: decisões que julgaram a sugestão. ILEGÍVEL fica fora — não "
                        + "diz nada sobre o tipo, diz sobre a digitalização");
    }

    // --- 9: books republicados -------------------------------------------------

    private Indicador booksRepublicados(String competencia) {
        int total = umInteiro("""
                SELECT count(*)
                FROM   book b JOIN ciclo c ON c.id = b.ciclo_id
                WHERE  c.competencia = ? AND b.versao > 1
                """, competencia);
        return Indicador.contagem("BOOKS_REPUBLICADOS", "Books republicados (KRI)", total,
                Meta.monitorar(), "book",
                "versões > 1. Cada uma é um book que saiu ao cliente e precisou voltar");
    }

    // --- exportação -------------------------------------------------------------

    /** O CSV do critério de aceite. A neutralização de fórmula vive no {@link Csv}. */
    public String csv(String competencia) {
        Csv csv = new Csv("competencia", "codigo", "indicador", "valor", "unidade",
                "numerador", "denominador", "meta", "situacao", "fonte", "observacao");
        for (Indicador i : daCompetencia(competencia)) {
            csv.linha(competencia, i.codigo(), i.nome(), i.valor(), i.unidade(),
                    i.numerador(), i.denominador(), i.meta(), i.situacao(), i.fonte(),
                    i.observacao());
        }
        return csv.texto();
    }

    // -------------------------------------------------------------------------

    private int umInteiro(String sql, String competencia) {
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, competencia);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao calcular o indicador", e);
        }
    }

    private int[] doisInteiros(String sql, String competencia) {
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, competencia);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new int[] {rs.getInt(1), rs.getInt(2)};
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao calcular o indicador", e);
        }
    }

    private List<Object[]> linhas(String sql, String competencia, int colunas) {
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, competencia);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> linhas = new ArrayList<>();
                while (rs.next()) {
                    Object[] linha = new Object[colunas];
                    for (int i = 0; i < colunas; i++) {
                        linha[i] = rs.getObject(i + 1);
                    }
                    linhas.add(linha);
                }
                return linhas;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao detalhar o indicador", e);
        }
    }
}
