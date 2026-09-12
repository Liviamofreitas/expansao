package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.orquestracao.Job;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Executa um job uma vez só, registra o que aconteceu, e sabe quando calou.
 *
 * <p>Fecha a pendência RA-07 <b>pela metade que importa</b>: o mecanismo. Ligar
 * os gatilhos continua sendo decisão de governança, gated na F3-01 — pôr o
 * sistema a cobrar áreas antes de a divergência estar medida é o risco P01 do
 * cap. 22.
 *
 * <p><b>Lock consultivo do PostgreSQL, e não uma tabela de lock.</b> A escolha
 * é pelo modo de falha, não pela elegância. Uma tabela com lease exige escolher
 * um tempo de expiração, e os dois lados dessa escolha são ruins: curto demais e
 * um job lento perde o lock <i>enquanto ainda roda</i>, produzindo a execução
 * dupla que o lock existia para impedir; longo demais e uma instância que morreu
 * bloqueia o job pelo resto do lease. O lock consultivo é preso à <b>sessão</b>:
 * se a JVM morre, a conexão cai, e o PostgreSQL o libera na hora. Sem tempo para
 * calibrar, sem relógio para sincronizar.
 *
 * <p><b>Toda tentativa vira linha, inclusive a que não fez nada.</b> Ver a
 * V018: sem isso, "rodou e não achou" e "não rodou" são o mesmo estado
 * observável, e o segundo deixa o painel verde por nada ter acontecido.
 */
public final class Agendador {

    private final Sgdf sgdf;
    private final String instancia;

    public Agendador(Sgdf sgdf, String instancia) {
        this.sgdf = sgdf;
        this.instancia = instancia == null || instancia.isBlank() ? "desconhecida" : instancia;
    }

    /**
     * Roda o trabalho sob lock, e registra o desfecho qualquer que seja.
     *
     * @param trabalho devolve quantos itens tratou — zero é resposta legítima
     */
    public Execucao executar(Job job, Supplier<Integer> trabalho) {
        if (job == null) {
            throw new JobNaoAgendavel("job não informado");
        }
        if (!job.agendavel()) {
            // A PROIBIÇÃO DO CAP. 7.2, IMPOSTA EM VEZ DE LEMBRADA.
            //
            // Derivar eventos por calendário materializaria exigências de
            // férias, 13º e rescisão para uma competência cuja folha não
            // chegou — cobrando as áreas por eventos que talvez não tenham
            // acontecido, com denominador inventado.
            throw new JobNaoAgendavel(job + " é disparado por evento, não por calendário "
                    + "(cap. 7.2). Agendá-lo materializaria exigências de uma competência "
                    + "cuja folha ainda não chegou");
        }

        long id = abrir(job);
        boolean comLock = tentarLock(job);
        if (!comLock) {
            // Linha mesmo assim, e é informação: distingue agendador morto de
            // agendador vivo e disputado. CONCORRENTE repetido por horas é lock
            // preso, que é um sintoma diferente de silêncio.
            fechar(id, "CONCORRENTE", null, "outra instância já executava " + job);
            return new Execucao(job, "CONCORRENTE", 0, null);
        }
        try {
            int itens = trabalho.get();
            fechar(id, "SUCESSO", itens, null);
            return new Execucao(job, "SUCESSO", itens, null);
        } catch (RuntimeException e) {
            String motivo = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            // FECHA COMO FALHA E REPASSA. Engolir faria o job parecer nunca ter
            // acontecido; não fechar deixaria a linha em aberto para sempre, e o
            // alerta de job travado apontaria para uma execução que já morreu.
            fechar(id, "FALHA", null, motivo);
            throw e;
        } finally {
            liberarLock(job);
        }
    }

    /**
     * Os jobs que deveriam ter rodado e não rodaram — o alerta de silêncio.
     *
     * <p><b>É o único jeito de um agendador morto ser notado.</b> Todos os
     * outros sintomas da morte dele se parecem com saúde: nenhum documento novo,
     * nenhuma pendência nova, nenhum erro. Aqui a ausência é comparada com uma
     * expectativa declarada, e vira apontamento.
     *
     * <p>Job <b>nunca executado</b> entra na lista — e tem de entrar: um sistema
     * recém-implantado em que ninguém ligou o cron tem exatamente zero linhas, e
     * uma consulta que só olhasse a última execução não acharia nada para
     * reclamar.
     */
    public List<Silencio> silenciosos(OffsetDateTime agora) {
        List<Silencio> alertas = new ArrayList<>();
        for (Job job : Job.values()) {
            if (!job.agendavel()) {
                continue;
            }
            OffsetDateTime ultimoSucesso = ultimo(job, "SUCESSO");
            Duration esperada = job.cadenciaEsperada();
            if (ultimoSucesso == null) {
                alertas.add(new Silencio(job, null, null, esperada,
                        "nunca executado com sucesso"));
                continue;
            }
            Duration desde = Duration.between(ultimoSucesso.toInstant(), agora.toInstant());
            if (desde.compareTo(esperada) > 0) {
                alertas.add(new Silencio(job, ultimoSucesso, desde, esperada,
                        "último sucesso há " + desde.toHours() + "h, e a cadência esperada "
                                + "é de " + esperada.toHours() + "h"));
            }
        }
        return List.copyOf(alertas);
    }

    /**
     * Execuções abertas há mais tempo que a cadência — job travado.
     *
     * <p>Diferente do silêncio: aqui alguém começou e não terminou. A linha em
     * aberto segura o lock enquanto a sessão viver, e é o que explica um
     * CONCORRENTE que se repete.
     */
    public List<Travado> travados(OffsetDateTime agora) {
        String sql = """
                SELECT job, iniciada_em, instancia
                FROM   execucao_de_job
                WHERE  terminada_em IS NULL
                ORDER  BY iniciada_em
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Travado> travados = new ArrayList<>();
            while (rs.next()) {
                Job job = Job.de(rs.getString(1));
                OffsetDateTime inicio = rs.getObject(2, OffsetDateTime.class);
                Duration aberta = Duration.between(inicio.toInstant(), agora.toInstant());
                Duration limite = job == null || job.cadenciaEsperada() == null
                        ? Duration.ofHours(25) : job.cadenciaEsperada();
                if (aberta.compareTo(limite) > 0) {
                    travados.add(new Travado(rs.getString(1), inicio, aberta,
                            rs.getString(3)));
                }
            }
            return travados;
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as execuções em aberto", e);
        }
    }

    /** O histórico de um job — o que a tela de operação mostra. */
    public List<Execucao> historico(Job job, int limite) {
        String sql = """
                SELECT job, resultado, itens, detalhe
                FROM   execucao_de_job
                WHERE  job = ? AND terminada_em IS NOT NULL
                ORDER  BY iniciada_em DESC LIMIT ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, job.name());
            ps.setInt(2, Math.max(1, limite));
            try (ResultSet rs = ps.executeQuery()) {
                List<Execucao> execucoes = new ArrayList<>();
                while (rs.next()) {
                    Integer itens = rs.getObject(3, Integer.class);
                    execucoes.add(new Execucao(Job.de(rs.getString(1)), rs.getString(2),
                            itens == null ? 0 : itens, rs.getString(4)));
                }
                return execucoes;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o histórico do job", e);
        }
    }

    // -------------------------------------------------------------------------

    private long abrir(Job job) {
        String sql = "INSERT INTO execucao_de_job (job, instancia) VALUES (?, ?) RETURNING id";
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, job.name());
            ps.setString(2, instancia);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao abrir a execução do job", e);
        }
    }

    private void fechar(long id, String resultado, Integer itens, String detalhe) {
        String sql = """
                UPDATE execucao_de_job
                SET    terminada_em = now(), resultado = ?, itens = ?, detalhe = ?
                WHERE  id = ? AND terminada_em IS NULL
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, resultado);
            if (itens == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, itens);
            }
            ps.setString(3, detalhe);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao fechar a execução do job", e);
        }
    }

    /** Preso à SESSÃO: se a JVM morrer, o PostgreSQL o libera sozinho. */
    boolean tentarLock(Job job) {
        try (PreparedStatement ps = sgdf.conexao()
                .prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, chaveDo(job));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao tomar o lock do job", e);
        }
    }

    void liberarLock(Job job) {
        try (PreparedStatement ps = sgdf.conexao()
                .prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, chaveDo(job));
            ps.executeQuery().close();
        } catch (SQLException e) {
            // Não repassa: o trabalho já terminou e já foi registrado, e a
            // sessão liberará o lock ao cair de qualquer forma. Levantar aqui
            // transformaria um job bem-sucedido em falha.
            return;
        }
    }

    /**
     * Chave do lock a partir do nome do job.
     *
     * <p>Deliberadamente <b>não</b> {@code String.hashCode()}: ele é de 32 bits
     * e colide com facilidade, e duas chaves iguais fariam dois jobs diferentes
     * disputarem o mesmo lock — um deles nunca rodaria, e o sintoma seria
     * CONCORRENTE eterno num job que ninguém mais executa. O ordinal é único por
     * construção e estável enquanto o enum não for reordenado; o deslocamento
     * separa o espaço do SGDF de qualquer outro uso de lock consultivo no mesmo
     * banco.
     */
    static long chaveDo(Job job) {
        return 8_030_000L + job.ordinal();
    }

    /** Um desfecho. {@code itens} é zero em CONCORRENTE e em FALHA. */
    public record Execucao(Job job, String resultado, int itens, String detalhe) {

        public boolean sucesso() {
            return "SUCESSO".equals(resultado);
        }
    }

    /**
     * Um job que deveria ter rodado.
     *
     * @param ultimoSucesso nulo quando nunca rodou — o caso do sistema recém-ligado
     */
    public record Silencio(Job job, OffsetDateTime ultimoSucesso, Duration desde,
                           Duration cadenciaEsperada, String motivo) {

        /** Nunca rodou é pior que atrasou: não há nem evidência de que foi ligado. */
        public boolean nuncaExecutou() {
            return ultimoSucesso == null;
        }
    }

    /** Uma execução que começou e não terminou. */
    public record Travado(String job, OffsetDateTime iniciadaEm, Duration aberta,
                          String instancia) {
    }

    /** Cap. 7.2: há job que não se agenda. */
    public static final class JobNaoAgendavel extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JobNaoAgendavel(String motivo) {
            super(motivo);
        }
    }

    private OffsetDateTime ultimo(Job job, String resultado) {
        String sql = """
                SELECT max(terminada_em) FROM execucao_de_job
                WHERE job = ? AND resultado = ?
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, job.name());
            ps.setString(2, resultado);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, OffsetDateTime.class);
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler a última execução", e);
        }
    }
}
