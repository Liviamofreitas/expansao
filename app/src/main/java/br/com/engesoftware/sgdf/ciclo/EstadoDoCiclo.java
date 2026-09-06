package br.com.engesoftware.sgdf.ciclo;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Os estados do ciclo e as transições permitidas — cap. 6.2.
 *
 * <p>O capítulo escreve o caminho feliz numa linha: <i>"ABERTO → EM_COLETA →
 * PRONTO (toda exigência bloqueante em PUBLICADO/DISPENSADO) → ATESTADO
 * (registro do ateste, dispara o relógio D+3) → FATURADO (NF emitida) →
 * FECHADO. Estados de exceção: BLOQUEADO e REABERTO."</i>
 *
 * <p><b>Por que uma tabela fechada e não um campo de texto.</b> {@code status}
 * no banco é um {@code CHECK} sobre oito valores: ele impede escrever
 * "FATRADO", e não impede escrever FECHADO num ciclo que nunca foi atestado. O
 * conjunto de valores válidos e o conjunto de <i>movimentos</i> válidos são
 * coisas diferentes, e é o segundo que o cap. 6.2 define. Sem esta tabela, cada
 * caminho de código que escreve {@code status} carrega a sua própria ideia de
 * ordem — e basta um deles discordar para o ciclo chegar a FATURADO sem ateste,
 * que é justamente o marco de onde o indicador D+3 conta.
 *
 * <p><b>A circularidade que não existe.</b> Ler "PRONTO exige toda bloqueante
 * em PUBLICADO" e "publicar o book exige o ciclo pronto" ao mesmo tempo sugere
 * um nó: nada publica porque nada está pronto. Não é o caso, e a ordem
 * desfaz: o book publica sobre exigências <b>CONCILIADAS</b> (é o que
 * {@code ConsultaDoPainel.motivosDeBloqueio} verifica, aceitando CONCILIADO), e
 * a publicação é o que move CONCILIADO → PUBLICADO (cap. 6.1, última linha).
 * Só depois disso o ciclo tem como ir a PRONTO. Os dois portões existem, são
 * diferentes, e ficam em métodos diferentes:
 *
 * <ul>
 *   <li>{@code ConsultaDoPainel.motivosDeBloqueio} — <i>posso publicar o
 *       book?</i> Aceita CONCILIADO.</li>
 *   <li>{@code RepositorioDeCiclo.bloqueantesEmAberto} — <i>posso declarar o
 *       ciclo PRONTO?</i> Exige PUBLICADO ou DISPENSADO, como o cap. 6.2.</li>
 * </ul>
 *
 * <p>Confundir os dois seria fácil e caro: o portão frouxo no lugar do rígido
 * deixa a medição sair ao cliente com bloqueante pendente — exatamente o que a
 * F3-04 existe para impedir.
 */
public enum EstadoDoCiclo {

    /** Criado, com a matriz congelada. Ainda não varreu nada. */
    ABERTO,

    /** Varrendo, triando e conciliando. O estado de trabalho. */
    EM_COLETA,

    /** Toda bloqueante resolvida. Pré-condição do envio da medição ao cliente. */
    PRONTO,

    /** O cliente atestou. Aqui começa o relógio do D+3 (cap. 21). */
    ATESTADO,

    /** NF emitida. */
    FATURADO,

    /** Encerrado. */
    FECHADO,

    /** Exceção: divergência bloqueante travou o ciclo. */
    BLOQUEADO,

    /** Exceção: republicação; incrementa a versão do book. */
    REABERTO;

    /**
     * O que sai de cada estado.
     *
     * <p><b>O que este mapa deliberadamente não tem.</b> Não há atalho de
     * ABERTO para PRONTO: um ciclo que nunca coletou nada não pode estar
     * completo, e a única forma de chegar a PRONTO sem passar por EM_COLETA
     * seria um ciclo sem exigência nenhuma — que é um erro de materialização,
     * não um ciclo pronto. Não há volta de FECHADO para EM_COLETA: reabrir é
     * REABERTO, e a distinção é o que faz a versão do book incrementar. E
     * BLOQUEADO não vai direto a ATESTADO — sair do bloqueio devolve ao fluxo,
     * não o pula.
     */
    private static final Map<EstadoDoCiclo, Set<EstadoDoCiclo>> PERMITIDAS = Map.of(
            ABERTO,    EnumSet.of(EM_COLETA, BLOQUEADO),
            EM_COLETA, EnumSet.of(PRONTO, BLOQUEADO),
            PRONTO,    EnumSet.of(ATESTADO, BLOQUEADO, REABERTO),
            ATESTADO,  EnumSet.of(FATURADO, BLOQUEADO, REABERTO),
            FATURADO,  EnumSet.of(FECHADO, REABERTO),
            FECHADO,   EnumSet.of(REABERTO),
            BLOQUEADO, EnumSet.of(EM_COLETA, PRONTO),
            REABERTO,  EnumSet.of(EM_COLETA, PRONTO));

    /** Se o cap. 6.2 permite este movimento. Ficar parado não é movimento. */
    public boolean podeIrPara(EstadoDoCiclo destino) {
        return destino != null && destinos().contains(destino);
    }

    /** Para onde este estado pode ir — o que a tela oferece como ação. */
    public Set<EstadoDoCiclo> destinos() {
        return Set.copyOf(PERMITIDAS.getOrDefault(this, EnumSet.noneOf(EstadoDoCiclo.class)));
    }

    /** Cap. 6.2: PRONTO exige toda bloqueante em PUBLICADO ou DISPENSADO. */
    public boolean exigeBloqueantesResolvidas() {
        return this == PRONTO;
    }

    /** Cap. 6.2: ATESTADO é o registro do ateste, não uma declaração avulsa. */
    public boolean exigeAteste() {
        return this == ATESTADO;
    }

    /** Cap. 6.2: FATURADO é "NF emitida" — sem data de emissão não há fato. */
    public boolean exigeNotaFiscal() {
        return this == FATURADO;
    }

    /** Aceita o texto do banco; nulo e desconhecido viram nulo, nunca um estado. */
    public static EstadoDoCiclo de(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return valueOf(texto.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
