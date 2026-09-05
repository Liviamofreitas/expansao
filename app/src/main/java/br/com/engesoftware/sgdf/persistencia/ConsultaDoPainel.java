package br.com.engesoftware.sgdf.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As consultas que as telas do Anexo 2 precisam.
 *
 * <p>Separadas dos repositórios de escrita de propósito: o que a tela pede é
 * agregado e recortado, e forçar isso pelos mesmos objetos que gravam produziria
 * ou consultas ineficientes ou objetos de domínio deformados para servir a uma
 * tela.
 */
public final class ConsultaDoPainel {

    private final Sgdf sgdf;

    public ConsultaDoPainel(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /**
     * De que contrato-serviço é o ciclo — nulo se o ciclo não existe.
     *
     * <p>Existe para que a autorização não dependa de o cliente <b>declarar</b>
     * o contrato. Um recorte que só é aplicado quando quem chama informa o alvo
     * não é um recorte: basta omitir o parâmetro para escapar dele. O alvo da
     * decisão tem de vir do dado, e o dado é a coluna {@code contrato_servico_id}
     * do próprio ciclo.
     *
     * <p>Ler esta coluna antes de autorizar não revela nada a quem não pode: o
     * identificador do contrato nunca chega à resposta, só ao {@code Autorizador}.
     */
    public UUID contratoDoCiclo(UUID cicloId) {
        String sql = "SELECT contrato_servico_id FROM ciclo WHERE id = ?";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o contrato do ciclo", e);
        }
    }

    /**
     * A barra segmentada da tela 1: quantas exigências em cada estado.
     *
     * <p>Critério de aceite da F1-07: <i>"a barra segmentada reflete os estados
     * reais"</i> — por isso a contagem vem do banco e não de um cálculo em
     * memória que poderia divergir do que está gravado.
     */
    public Map<String, Integer> estadosDoCiclo(UUID cicloId) {
        String sql = """
                SELECT status, count(*)
                FROM exigencia
                WHERE ciclo_id = ?
                GROUP BY status
                ORDER BY status
                """;
        Map<String, Integer> contagem = new LinkedHashMap<>();
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contagem.put(rs.getString(1), rs.getInt(2));
                }
            }
            return contagem;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao contar os estados do ciclo", e);
        }
    }

    /**
     * Por que o ciclo não pode publicar.
     *
     * <p>Critério de aceite da F1-07: <i>"bloqueio de publicação exibe
     * motivo"</i>. São duas fontes — exigência bloqueante que não chegou ao fim,
     * e conciliação divergente em modo BLOQUEIO — e a tela precisa das duas,
     * porque quem resolve cada uma é uma pessoa diferente.
     */
    public List<String> motivosDeBloqueio(UUID cicloId) {
        List<String> motivos = new ArrayList<>();
        String porExigencia = """
                SELECT t.codigo, e.status, count(*)
                FROM exigencia e
                JOIN tipo_documental t ON t.id = e.tipo_id
                WHERE e.ciclo_id = ?
                  AND e.criticidade = 'BLOQUEANTE'
                  AND e.status NOT IN ('PUBLICADO', 'DISPENSADO', 'CONCILIADO')
                GROUP BY t.codigo, e.status
                ORDER BY t.codigo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(porExigencia)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    motivos.add(rs.getString(1) + ": " + rs.getInt(3)
                            + " exigência(s) bloqueante(s) em " + rs.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as exigências bloqueantes", e);
        }
        motivos.addAll(new RepositorioDeConciliacao(sgdf).bloqueiosDoCiclo(cicloId));
        return motivos;
    }

    /**
     * Completude por tipo — história F2-03, cap. 12.
     *
     * <p>Critério de aceite: <i>"«faltam 3 contracheques de 42» calculado e
     * exibido"</i>. Sem isto, uma exigência de escopo PROFISSIONAL vira 42 linhas
     * soltas no painel, uma por trabalhador, e a tela fica ilegível justamente no
     * contrato maior — que é onde ela mais precisa ser lida.
     *
     * <p><b>Quatro baldes, não dois.</b> "Faltam 3 de 42" parece uma subtração e
     * não é. Um documento pode estar em quatro situações diferentes, e juntá-las
     * manda a pessoa errada atrás da coisa errada:
     *
     * <ul>
     *   <li><b>Entregue</b> — chegou e vale (RECEBIDO, VALIDADO, CONCILIADO,
     *       PUBLICADO).</li>
     *   <li><b>Ausente</b> — não chegou, ou chegou e foi recusado (PENDENTE,
     *       REJEITADO). É o que a AP cobra.</li>
     *   <li><b>Com problema</b> — chegou e está travado em outra mesa
     *       (EM_TRIAGEM, DIVERGENTE). Cobrar a área por isso é cobrar quem já
     *       entregou.</li>
     *   <li><b>Dispensada</b> — exceção aprovada (DISPENSADO). Contá-la como
     *       falta faz alguém correr atrás de um documento formalmente
     *       dispensado, contra a decisão do APROVADOR_DAF.</li>
     * </ul>
     *
     * <p><b>O grupo condicional entra na conta, e tem de entrar.</b> Cap. 7.5:
     * exigências do mesmo grupo são satisfeitas por qualquer uma — o termo de não
     * adesão satisfaz o vale-transporte daquele profissional. Contar sem isso
     * exibiria "faltam 12 relações de VT" com os 12 termos de não adesão
     * entregues ao lado. O agrupamento é <b>por profissional</b>: um termo de
     * fulano não satisfaz o VT de sicrano.
     */
    public List<Completude> completudePorTipo(UUID cicloId) {
        String sql = """
                WITH base AS (
                    SELECT ec.exigencia_id, ec.profissional_id, ec.status,
                           ec.condicional_grupo, t.codigo, t.nome, t.escopo
                    FROM   exigencia_do_ciclo ec
                    JOIN   tipo_documental t ON t.id = ec.tipo_id
                    WHERE  ec.ciclo_id = ?
                ),
                grupo_satisfeito AS (
                    SELECT condicional_grupo, profissional_id
                    FROM   base
                    WHERE  condicional_grupo IS NOT NULL
                      AND  status IN ('RECEBIDO', 'VALIDADO', 'CONCILIADO', 'PUBLICADO')
                    GROUP  BY 1, 2
                ),
                efetivo AS (
                    SELECT b.*,
                           CASE WHEN g.condicional_grupo IS NOT NULL THEN 'SATISFEITA'
                                ELSE b.status END AS efetivo
                    FROM   base b
                    LEFT   JOIN grupo_satisfeito g
                           ON g.condicional_grupo = b.condicional_grupo
                          AND g.profissional_id IS NOT DISTINCT FROM b.profissional_id
                )
                SELECT codigo, nome, escopo,
                       count(*)                                                AS esperadas,
                       count(*) FILTER (WHERE efetivo IN ('RECEBIDO', 'VALIDADO',
                                        'CONCILIADO', 'PUBLICADO', 'SATISFEITA')) AS entregues,
                       count(*) FILTER (WHERE efetivo IN ('EM_TRIAGEM', 'DIVERGENTE'))
                                                                                AS com_problema,
                       count(*) FILTER (WHERE efetivo = 'DISPENSADO')           AS dispensadas,
                       count(*) FILTER (WHERE efetivo IN ('PENDENTE', 'REJEITADO'))
                                                                                AS ausentes
                FROM   efetivo
                GROUP  BY codigo, nome, escopo
                ORDER  BY escopo, codigo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Completude> completude = new ArrayList<>();
                while (rs.next()) {
                    completude.add(new Completude(rs.getString(1), rs.getString(2),
                            rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                            rs.getInt(7), rs.getInt(8)));
                }
                return completude;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao calcular a completude do ciclo", e);
        }
    }

    /**
     * Quem falta, por matrícula — só para quem pode ver escopo profissional.
     *
     * <p>A contagem serve a todo mundo; a lista nominal, não. Matrícula identifica
     * uma pessoa, e "faltam os contracheques de 100787, 100792 e 100801" numa
     * tela aberta é dado pessoal exibido a quem o cap. 15.1 não autorizou. Quem
     * chama decide se pede, e o controlador só pede quando o ator tem
     * {@code VER_DOCUMENTO_PROFISSIONAL}.
     */
    public List<String> matriculasFaltantes(UUID cicloId, String tipoCodigo, int limite) {
        String sql = """
                SELECT p.matricula
                FROM   exigencia_do_ciclo ec
                JOIN   tipo_documental t ON t.id = ec.tipo_id
                JOIN   profissional p ON p.id = ec.profissional_id
                WHERE  ec.ciclo_id = ? AND t.codigo = ?
                  AND  ec.status IN ('PENDENTE', 'REJEITADO')
                ORDER  BY p.matricula
                LIMIT  ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            ps.setString(2, tipoCodigo);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> matriculas = new ArrayList<>();
                while (rs.next()) {
                    matriculas.add(rs.getString(1));
                }
                return matriculas;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao listar as matrículas faltantes", e);
        }
    }

    /**
     * A linha da tela: "CONTRACHEQUE — 39 de 42".
     *
     * @param esperadas   quantas exigências deste tipo o ciclo materializou
     * @param comProblema chegou e travou noutra mesa; não é falta da área
     * @param ausentes    o que a AP cobra
     */
    public record Completude(String tipo, String nome, String escopo, int esperadas,
                             int entregues, int comProblema, int dispensadas, int ausentes) {

        /**
         * O texto do critério de aceite.
         *
         * <p>Diz "faltam N de M" só quando falta; dizer "faltam 0 de 42" obriga
         * quem lê a fazer a subtração para descobrir que está tudo lá.
         */
        public String resumo() {
            if (ausentes == 0) {
                return esperadas + " de " + esperadas + " — completo";
            }
            return "faltam " + ausentes + " de " + esperadas;
        }

        /** Só é completo quando nada falta E nada está travado noutra mesa. */
        public boolean completo() {
            return ausentes == 0 && comProblema == 0;
        }
    }

    // A fila de triagem saiu daqui na F1-06.
    //
    // Ela era lida de `documento` sozinho, e `documento` não tem contrato — era
    // a origem do RA-01. Removida em vez de mantida "para compatibilidade":
    // uma consulta que lê a fila inteira sem recorte, deixada no código, é o
    // buraco disponível para o próximo chamador. Ver RepositorioDeTriagem.fila.

    /**
     * O painel de arquivos desconhecidos (tela 5, história F1-10).
     *
     * <p>Cap. 8.3: abaixo do limiar de triagem o arquivo é desconhecido — não
     * conta como entrega e aparece no painel de organização. Nunca é vinculado.
     */
    public List<Candidato> desconhecidos(int limite) {
        String sql = """
                SELECT d.id, d.nome_arquivo, d.caminho, '-', coalesce(d.confianca, 0), '-'
                FROM documento d
                WHERE d.tipo_id IS NULL
                ORDER BY d.criado_em DESC
                LIMIT ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<Candidato> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new Candidato(rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getString(3), rs.getString(4),
                            rs.getBigDecimal(5).doubleValue(), rs.getString(6)));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os arquivos desconhecidos", e);
        }
    }

    /**
     * Um arquivo à espera de decisão humana.
     *
     * @param sigilo do tipo proposto — decide se quem olha pode ver o conteúdo
     */
    public record Candidato(UUID documentoId, String nomeArquivo, String caminho,
                            String tipoProposto, double confianca, String sigilo) {
    }
}
