package br.com.engesoftware.sgdf.limite;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Conta eventos numa janela que anda com o tempo — SEC-06.
 *
 * <p><b>Deslizante, e não por balde fixo.</b> Um contador que zera de minuto em
 * minuto aceita o dobro do limite na virada: 100 pedidos no segundo 59 e mais
 100 no segundo 61 passam, e o pico real é 200 em dois segundos. A janela
 * deslizante mede sempre os últimos N segundos, e por isso não tem virada.
 *
 * <p><b>O custo é lembrar de cada evento.</b> Guardar um instante por pedido é
 * caro se o limite for alto; para os limites desta API — dezenas por minuto num
 * sistema interno — é barato, e a alternativa (janela aproximada por
 * interpolação) trocaria exatidão por uma economia que ninguém precisa. Se um
 * dia o limite crescer uma ordem de grandeza, é aqui que se mexe.
 *
 * <p>Não é thread-safe por dentro: quem a usa serializa o acesso. Pôr um lock
 * aqui esconderia a decisão de concorrência dentro de uma estrutura de dados,
 * que é onde ela é mais difícil de encontrar depois.
 */
public final class JanelaDeslizante {

    private final Duration janela;
    private final int limite;
    private final Deque<Instant> eventos = new ArrayDeque<>();

    public JanelaDeslizante(Duration janela, int limite) {
        if (janela == null || janela.isZero() || janela.isNegative()) {
            throw new IllegalArgumentException("janela precisa ser positiva");
        }
        if (limite < 1) {
            throw new IllegalArgumentException("limite precisa ser ao menos 1: um limite "
                    + "de zero barra tudo, inclusive quem tem direito");
        }
        this.janela = janela;
        this.limite = limite;
    }

    /**
     * Registra um evento e diz se ele cabe.
     *
     * <p><b>O evento que estoura o limite NÃO entra na contagem.</b> Contá-lo
     * faria cada tentativa barrada empurrar a liberação para a frente: quem
     * insiste durante o bloqueio nunca sairia dele, e um cliente com retry
     * automático se auto-prenderia para sempre. O bloqueio pune o excesso, não
     * a insistência.
     */
    public boolean cabe(Instant agora) {
        expirar(agora);
        if (eventos.size() >= limite) {
            return false;
        }
        eventos.addLast(agora);
        return true;
    }

    /** Quantos eventos há na janela agora. */
    public int quantidade(Instant agora) {
        expirar(agora);
        return eventos.size();
    }

    /** Quanto falta para o mais antigo sair da janela — o Retry-After. */
    public Duration esperaAte(Instant agora) {
        expirar(agora);
        if (eventos.size() < limite) {
            return Duration.ZERO;
        }
        Duration espera = Duration.between(agora, eventos.peekFirst().plus(janela));
        // Arredonda para cima: um Retry-After de 0 s manda tentar de novo agora,
        // e a tentativa seria barrada de novo.
        return espera.isNegative() || espera.isZero() ? Duration.ofSeconds(1)
                : espera.plusSeconds(1);
    }

    private void expirar(Instant agora) {
        Instant corte = agora.minus(janela);
        while (!eventos.isEmpty() && !eventos.peekFirst().isAfter(corte)) {
            eventos.removeFirst();
        }
    }
}
