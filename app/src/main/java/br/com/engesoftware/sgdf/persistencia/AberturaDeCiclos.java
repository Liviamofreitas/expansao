package br.com.engesoftware.sgdf.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abre os ciclos da competência e materializa as exigências — F0-07, cap. 7.1.
 *
 * <p>Critério de aceite: <i>"no 1º dia útil, 15 ciclos abertos; exigência
 * corporativa única compartilhada"</i>.
 *
 * <p><b>Nada criava a linha de {@code ciclo}.</b> O {@code RepositorioDaMatriz}
 * sabia materializar exigências <i>de um ciclo que já existisse</i>, e o ciclo
 * só existia porque um fixture de teste o inseria. Em produção o sistema abriria
 * exatamente zero ciclos e o painel ficaria vazio — sem erro nenhum, que é o
 * modo de falha que esta base já encontrou três vezes (§ 46).
 *
 * <p><b>A competência é a CORRENTE, e a razão vem do prazo.</b> A âncora
 * {@code INICIO_COMPETENCIA} do cap. 7.3 resolve para o dia 1º da competência —
 * {@code {INICIO_COMPETENCIA, CORRIDO, 21}} é "até o dia 21" <i>desse mesmo
 * mês</i>. Um ciclo aberto para a competência anterior faria todo prazo ancorado
 * no início já nascer vencido. A leitura sai do modelo de prazo que já está
 * construído e verificado por 32 casos de conformidade, não de suposição.
 *
 * <p><b>Contrato fora de vigência não abre ciclo.</b> Abrir para um contrato
 * encerrado materializaria exigências que ninguém deve — e a área seria cobrada
 * por documento de um serviço que não presta mais.
 */
public final class AberturaDeCiclos {

    private final Sgdf sgdf;

    public AberturaDeCiclos(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * Abre o que falta para a competência e materializa.
     *
     * <p>Idempotente por {@code ciclo_unico}: rodar de novo no mesmo mês não
     * duplica nada, e o resultado diz quantos eram novos.
     */
    public Abertura abrir(YearMonth competencia, String ator) {
        String texto = competencia.toString();
        UUID versao = versaoCorrente();
        if (versao == null) {
            // RECUSA ALTA, E NÃO ABERTURA VAZIA.
            //
            // Abrir ciclos sem versão de matriz publicada criaria ciclos sem
            // exigência nenhuma — que se parecem, no painel, com ciclos
            // completos. O sistema estaria dizendo "não falta nada" sobre uma
            // competência que ele não sabe conferir.
            throw new SemMatrizPublicada("não há versão de matriz publicada: abrir ciclos "
                    + "agora criaria ciclos sem exigência, e um ciclo sem exigência aparece "
                    + "no painel como um ciclo completo");
        }

        List<UUID> contratos = contratosVigentes(competencia);
        List<UUID> abertos = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        int materializadas = 0;

        for (UUID contrato : contratos) {
            UUID ciclo = null;
            try {
                ciclo = criar(contrato, texto, versao, ator);
                if (ciclo == null) {
                    continue;   // já existia: idempotência, não erro
                }
                int criadas = new RepositorioDaMatriz(sgdf).materializar(ciclo, ator)
                        .criadas();
                if (criadas == 0) {
                    // UM CICLO SEM EXIGÊNCIA NÃO PODE FICAR DE PÉ.
                    //
                    // No painel ele se lê como um ciclo COMPLETO: nenhuma
                    // pendência, nenhum bloqueio, nada faltando. O sistema
                    // estaria dizendo "não falta nada" sobre uma competência que
                    // ele não conseguiu conferir — o pior resultado possível num
                    // sistema cuja função é notar o que falta.
                    //
                    // Descoberto ao ligar a abertura contra a carga real: os 12
                    // contratos abriam e materializavam ZERO, por defeito de
                    // dados (regra ambígua e empresa emitente ausente). O motor
                    // estava certo; o que faltava era recusar o resultado vazio.
                    desfazer(ciclo);
                    falhas.add(contrato + ": ciclo aberto sem exigência nenhuma e desfeito "
                            + "— um ciclo vazio aparece no painel como um ciclo completo");
                    continue;
                }
                abertos.add(ciclo);
                materializadas += criadas;
            } catch (RuntimeException e) {
                // Um contrato com cadastro incompleto não pode impedir os
                // outros quatorze de abrirem. Mesma regra do lote de ingestão.
                //
                // E O CICLO CRIADO ANTES DA FALHA TAMBÉM É DESFEITO.
                //
                // Materialização que lança deixa o ciclo existindo e vazio — e
                // um ciclo vazio aparece no painel como um ciclo completo,
                // exatamente como o que materializa zero sem erro. Foi o que a
                // carga real produziu: doze ciclos de pé, zero exigências, e
                // "nada falta" em todos.
                if (ciclo != null) {
                    desfazer(ciclo);
                }
                falhas.add(contrato + ": " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " — " + e.getMessage()));
            }
        }
        return new Abertura(texto, contratos.size(), List.copyOf(abertos), materializadas,
                List.copyOf(falhas));
    }

    // -------------------------------------------------------------------------

    /**
     * A última versão publicada — congelada no ciclo (cap. 7.1, passo 4).
     *
     * <p>Congelar é o que faz um reprocessamento usar a regra da época e não a
     * de hoje: sem isso, republicar o book de uma competência antiga aplicaria
     * regras que não valiam quando o documento foi exigido.
     */
    private UUID versaoCorrente() {
        String sql = "SELECT id FROM versao_matriz ORDER BY publicada_em DESC LIMIT 1";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getObject(1, UUID.class) : null;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a versão corrente da matriz", e);
        }
    }

    /** Vigente em QUALQUER dia da competência — não só no primeiro. */
    private List<UUID> contratosVigentes(YearMonth competencia) {
        String sql = """
                SELECT id FROM contrato_servico
                WHERE  vigencia_ini <= ?
                  AND  (vigencia_fim IS NULL OR vigencia_fim >= ?)
                ORDER  BY numero
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            // Um contrato que começa no dia 20 ou termina no dia 10 PRESTOU
            // serviço na competência e tem o que faturar. Exigir vigência no mês
            // inteiro deixaria de fora exatamente a entrada e a saída, que é
            // quando há rescisão e admissão a documentar.
            ps.setObject(1, competencia.atEndOfMonth());
            ps.setObject(2, competencia.atDay(1));
            try (ResultSet rs = ps.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
                return ids;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao listar os contratos vigentes", e);
        }
    }

    /**
     * Desfaz um ciclo que não materializou nada.
     *
     * <p>Não é rollback de transação: a materialização já comitou o que fez (ou
     * nada). É remoção explícita, e só é segura porque o ciclo acabou de ser
     * criado nesta execução e não tem nada pendurado.
     */
    private void desfazer(UUID ciclo) {
        sgdf.emTransacao(conexao -> {
            try (PreparedStatement ps = conexao.prepareStatement(
                    "DELETE FROM ciclo WHERE id = ? AND status = 'ABERTO'"
                    + " AND NOT EXISTS (SELECT 1 FROM exigencia e WHERE e.ciclo_id = ?)")) {
                ps.setObject(1, ciclo);
                ps.setObject(2, ciclo);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao desfazer o ciclo vazio", e);
            }
            return null;
        });
    }

    /** @return o id do ciclo criado, ou nulo se já existia */
    private UUID criar(UUID contrato, String competencia, UUID versao, String ator) {
        String sql = """
                INSERT INTO ciclo (contrato_servico_id, competencia, status, versao_matriz_id,
                                   criado_por)
                VALUES (?, ?, 'ABERTO', ?, ?)
                ON CONFLICT (contrato_servico_id, competencia) DO NOTHING
                RETURNING id
                """;
        return sgdf.emTransacao(conexao -> {
            UUID id;
            try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                ps.setObject(1, contrato);
                ps.setString(2, competencia);
                ps.setObject(3, versao);
                ps.setString(4, ator);
                try (ResultSet rs = ps.executeQuery()) {
                    id = rs.next() ? rs.getObject(1, UUID.class) : null;
                }
            } catch (SQLException e) {
                throw new Sgdf.FalhaDePersistencia("falha ao abrir o ciclo", e);
            }
            if (id != null) {
                TrilhaDeAuditoria.registrar(conexao, TrilhaDeAuditoria.Registro.sucesso(
                        ator, "SVC_AGENDADOR", "CICLO_ABERTO", "ciclo", id.toString(),
                        Map.of("competencia", List.of(competencia),
                                "versao_matriz", List.of(versao.toString()))));
            }
            return id;
        });
    }

    /**
     * O desfecho.
     *
     * @param contratosVigentes quantos foram considerados
     * @param abertos           os que não existiam — zero é normal ao reexecutar
     */
    public record Abertura(String competencia, int contratosVigentes, List<UUID> abertos,
                           int exigenciasCriadas, List<String> falhas) {

        public Abertura {
            abertos = List.copyOf(abertos);
            falhas = List.copyOf(falhas);
        }

        public boolean completa() {
            return falhas.isEmpty();
        }
    }

    /** Ciclo sem matriz é ciclo sem exigência, e ciclo sem exigência parece completo. */
    public static final class SemMatrizPublicada extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SemMatrizPublicada(String motivo) {
            super(motivo);
        }
    }
}
