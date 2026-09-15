package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Papel;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Grava o que o provedor de identidade concedeu, como o sistema viu — SEC-10.
 *
 * <p>Ver a V017 para por que a trilha não bastava: ela registra quem
 * <b>escreve</b>, e o papel AUDITORIA não escreve nada por definição do
 * cap. 15.1.
 *
 * <p><b>Falhar aqui não pode derrubar a requisição.</b> Este registro é
 * observação de acesso, não o efeito que o usuário pediu — e um erro ao gravá-lo
 * transformaria uma consulta ao painel em erro 500. A exceção é engolida de
 * propósito, e é o único lugar do sistema onde isso acontece; a alternativa,
 * deixar subir, faria a recertificação derrubar o produto que ela existe para
 * revisar.
 *
 * <p><b>O que a RA-15 corrigiu.</b> Até aqui este método devolvia {@code
 * boolean}, e {@code false} queria dizer duas coisas opostas: "já estava
 * registrada" — que é o caso normal e saudável, acontece em toda requisição
 * depois da primeira do dia — e "a gravação falhou". Com o mesmo valor para os
 * dois fatos, <b>nenhum chamador conseguia notar a diferença</b>, e uma falha
 * persistente de escrita produzia um relatório de recertificação incompleto sem
 * sintoma nenhum. É o sexto caso do mesmo padrão nesta base: um componente cuja
 * falha se parece com sucesso.
 *
 * <p>Agora o desfecho é nomeado, a falha é contada, e ela deixa duas marcas: uma
 * <b>na memória do processo</b> ({@link Falhas}), que alimenta a ressalva do
 * relatório, e uma <b>na trilha</b>, que é durável e atravessa instâncias.
 */
public class RegistroDeAcesso {

    /** O que aconteceu com a observação. Quatro fatos, e nenhum é o outro. */
    public enum Desfecho {
        /** Primeira do dia para esta concessão: linha nova. */
        GRAVADA,
        /** Já havia linha idêntica hoje. <b>É o caso normal, não é falha.</b> */
        JA_REGISTRADA,
        /** Não havia concessão a registrar — ator nulo ou sem papel. */
        SEM_CONCESSAO,
        /** A gravação falhou. O relatório de recertificação ficará incompleto. */
        FALHOU;

        public boolean falhou() {
            return this == FALHOU;
        }
    }

    private final Sgdf sgdf;

    public RegistroDeAcesso(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /** Registra a concessão do dia. */
    public Desfecho observar(Ator ator) {
        return observar(ator, LocalDate.now());
    }

    public Desfecho observar(Ator ator, LocalDate dia) {
        if (ator == null || ator.papeis().isEmpty()) {
            // Sem papel não há concessão a certificar, e o Autorizador já barrou
            // na fronteira. Gravar produziria linha que não diz nada.
            return Desfecho.SEM_CONCESSAO;
        }
        // ORDENADOS: {A,B} e {B,A} são a mesma concessão, e sem a ordem o índice
        // as trataria como duas — a deriva apareceria onde não houve mudança.
        String[] papeis = ator.papeis().stream().map(Papel::name).sorted()
                .toArray(String[]::new);
        UUID[] contratos = ator.contratos().stream()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toArray(UUID[]::new);

        String sql = """
                INSERT INTO acesso_observado (ator, dia, papeis, contratos)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (ator, dia, papeis, contratos) DO NOTHING
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, ator.identificador());
            ps.setObject(2, dia);
            ps.setArray(3, sgdf.conexao().createArrayOf("text", papeis));
            ps.setArray(4, sgdf.conexao().createArrayOf("uuid", contratos));
            return ps.executeUpdate() == 1 ? Desfecho.GRAVADA : Desfecho.JA_REGISTRADA;
        } catch (SQLException | RuntimeException e) {
            // TAMBÉM RuntimeException, E NÃO SÓ SQLException.
            //
            // O caminho mais provável de falha aqui nem chega ao driver: a
            // conexão vem de um bean de escopo de requisição, e fora de uma
            // requisição o proxy levanta IllegalState antes de haver SQL. Pegar
            // só SQLException deixaria esse caso subir de dentro do
            // afterCompletion — que é o único lugar onde a promessa "observar
            // acesso não derruba o acesso" tinha de valer.
            registrarFalha(ator, e);
            return Desfecho.FALHOU;
        }
    }

    /**
     * A falha deixa duas marcas, porque nenhuma das duas basta sozinha.
     *
     * <p>Na <b>memória do processo</b>: funciona mesmo com o banco inteiro fora
     * do ar, e é o que a ressalva do relatório lê. Some no restart e não
     * atravessa instâncias.
     *
     * <p>Na <b>trilha</b>: é durável e comum às instâncias, e serve justamente
     * ao caso mais traiçoeiro — a falha específica desta tabela (uma restrição,
     * um tipo de array) com o resto do banco saudável, que é a que dura meses
     * sem ninguém ver. <b>Uma vez por processo por dia</b>: sem esse limite, um
     * erro que se repete a cada requisição encheria a trilha append-only de
     * milhares de linhas idênticas e afogaria a auditoria que ela sustenta.
     */
    private void registrarFalha(Ator ator, Exception causa) {
        String motivo = causa.getClass().getSimpleName()
                + (causa.getMessage() == null ? "" : ": " + causa.getMessage());
        Falhas.contar(motivo);

        if (!Falhas.primeiraDoDia()) {
            return;
        }
        try {
            TrilhaDeAuditoria.registrar(sgdf.conexao(), new TrilhaDeAuditoria.Registro(
                    ator.identificador(),
                    ator.papeis().stream().map(Papel::name).sorted()
                            .reduce((a, b) -> a + "+" + b).orElse("SEM_PAPEL"),
                    "OBSERVAR_ACESSO", "acesso_observado", null, "ERRO",
                    Map.of("motivo", java.util.List.of(motivo),
                           "efeito", java.util.List.of(
                               "a recertificação do SEC-10 fica incompleta (RA-15)"))));
        } catch (RuntimeException e) {
            // O banco pode estar fora por inteiro, e aí esta escrita falha
            // também. O contador em memória já registrou; insistir aqui
            // transformaria a falha de observação na exceção que ela não pode
            // ser.
            return;
        }
    }

    /**
     * Quantas observações falharam <b>nesta instância</b>, desde quando e por quê.
     *
     * <p><b>Estático, e a razão não é conveniência.</b> A falha de observação é
     * uma propriedade do processo, não de um repositório de escopo de
     * requisição: cada requisição constrói um {@code RegistroDeAcesso} novo, e um
     * contador de instância morreria com ele — contaria sempre um, que é o mesmo
     * que não contar.
     *
     * <p><b>O limite fica dito:</b> reinicia com o processo e não atravessa
     * instâncias. É por isso que a falha também vai à trilha, que tem as duas
     * propriedades. Ver RA-15.
     */
    public static final class Falhas {

        private static final AtomicLong TOTAL = new AtomicLong();
        private static volatile OffsetDateTime primeira;
        private static volatile OffsetDateTime ultima;
        private static volatile String ultimoMotivo;
        private static volatile LocalDate diaRegistradoNaTrilha;

        private Falhas() {}

        static void contar(String motivo) {
            if (TOTAL.getAndIncrement() == 0) {
                primeira = OffsetDateTime.now();
            }
            ultima = OffsetDateTime.now();
            ultimoMotivo = motivo;
        }

        /** Verdadeiro uma única vez por dia, para não afogar a trilha. */
        static synchronized boolean primeiraDoDia() {
            LocalDate hoje = LocalDate.now();
            if (hoje.equals(diaRegistradoNaTrilha)) {
                return false;
            }
            diaRegistradoNaTrilha = hoje;
            return true;
        }

        /**
         * A falha que aconteceu ANTES de chegar ao registro — ler o status, ou
         * resolver o ator. O interceptor a engole para não derrubar uma
         * requisição bem-sucedida, e a conta aqui para que engolir não seja
         * calar.
         */
        public static void contarFalhaDeCola(RuntimeException e) {
            contar("na fronteira, antes de gravar — " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }

        public static long total() {
            return TOTAL.get();
        }

        public static OffsetDateTime primeira() {
            return primeira;
        }

        public static OffsetDateTime ultima() {
            return ultima;
        }

        public static String ultimoMotivo() {
            return ultimoMotivo;
        }

        /** Só para teste: o estado é do processo, e um caso não pode herdar o outro. */
        public static void zerar() {
            TOTAL.set(0);
            primeira = null;
            ultima = null;
            ultimoMotivo = null;
            diaRegistradoNaTrilha = null;
        }
    }
}
