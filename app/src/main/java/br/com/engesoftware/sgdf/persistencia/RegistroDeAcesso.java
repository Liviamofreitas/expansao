package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Papel;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

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
 * <p>O custo dessa escolha é real e fica dito: uma falha persistente de escrita
 * aqui produz um relatório incompleto <b>sem sintoma</b>. Registrado em
 * PENDENCIAS como RA-15.
 */
public final class RegistroDeAcesso {

    private final Sgdf sgdf;

    public RegistroDeAcesso(Sgdf sgdf) {
        this.sgdf = sgdf;
    }

    /** Registra a concessão do dia. Silencioso quando já foi registrada. */
    public void observar(Ator ator) {
        observar(ator, LocalDate.now());
    }

    /** @return true se gravou; false se já existia ou se falhou */
    public boolean observar(Ator ator, LocalDate dia) {
        if (ator == null || ator.papeis().isEmpty()) {
            // Sem papel não há concessão a certificar, e o Autorizador já barrou
            // na fronteira. Gravar produziria linha que não diz nada.
            return false;
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
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            // Ver a nota da classe: observar acesso não derruba o acesso.
            return false;
        }
    }
}
