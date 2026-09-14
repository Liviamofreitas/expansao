package br.com.engesoftware.sgdf.retencao;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Por quanto tempo uma classe de dado se guarda, a partir de quando, e o que
 * acontece quando vence — pendência A08, requisito LGPD-02.
 *
 * <p><b>Esta classe não decide prazo nenhum.</b> Ela carrega o que o jurídico
 * decidiu. Prazo de guarda tem dois erros possíveis e nenhum deles é de
 * engenharia: curto demais destrói prova de regularidade trabalhista no meio de
 * uma reclamatória; longo demais é retenção indevida de dado pessoal de ±890
 * pessoas. A tabela {@code temporalidade} nasce com todas as classes
 * <b>propostas e não aprovadas</b>, e é assim que ela deve nascer.
 *
 * <p><b>{@link #aprovada()} não é um selo de qualidade — é a única porta.</b>
 * O banco copia {@code aprovado_em} para {@code expurgo.autorizado_em}, que é
 * NOT NULL: uma classe sem aprovação não consegue nem <i>registrar</i> um
 * expurgo, e o executor grava o registro antes de apagar, na mesma transação.
 * Logo, não há caminho — nem por bug, nem por pressa — que elimine dado sem que
 * alguém com nome tenha aprovado o prazo.
 */
public record Temporalidade(String id, String classe, Alvo alvo, Marco marco,
                            int prazoMeses, Acao acao, String fundamento,
                            OffsetDateTime aprovadoEm, String aprovadoPor) {

    /** O que a política governa. Lista fechada, espelhada no CHECK da V020. */
    public enum Alvo {

        /** SEC-10: quem acessou o quê, por dia. */
        ACESSO_OBSERVADO(Marco.REGISTRO, Acao.EXPURGAR),

        /** Cap. 11.1: a régua enviada. Sem dado do titular, sem anexo. */
        NOTIFICACAO(Marco.REGISTRO, Acao.EXPURGAR),

        /**
         * O documento coletado e o seu binário no bucket.
         *
         * <p><b>Sabe REVISAR e não sabe EXPURGAR, e a assimetria é a decisão.</b>
         * Listar documentos vencidos para uma pessoa olhar é reversível; apagar
         * evidência fiscal por decisão de agendador não é. Se o jurídico aprovar
         * EXPURGAR aqui, o sistema recusa <i>pelo nome</i> em vez de obedecer:
         * apagar o documento exige antes resolver a ordem com o book que o
         * atesta (cap. 11.2, selado sob LEGAL_HOLD) e com os campos extraídos
         * que sustentam a conciliação do cap. 9. É trabalho a fazer, e a recusa
         * é o que impede que ele seja pulado.
         */
        DOCUMENTO(Marco.DESLIGAMENTO_DO_PROFISSIONAL, Acao.REVISAR),

        /** Os valores lidos do documento. Segue o documento; sem executor próprio. */
        CAMPO_EXTRAIDO(null),

        /** O cadastro do titular. A anonimização do art. 16 ainda não existe. */
        PROFISSIONAL(Marco.DESLIGAMENTO_DO_PROFISSIONAL);

        private final Marco marcoDisponivel;
        private final java.util.Set<Acao> sabeFazer;

        Alvo(Marco marcoDisponivel, Acao... acoes) {
            this.marcoDisponivel = marcoDisponivel;
            this.sabeFazer = acoes.length == 0
                    ? java.util.EnumSet.noneOf(Acao.class)
                    : java.util.EnumSet.copyOf(java.util.Arrays.asList(acoes));
        }

        /**
         * A única data que este alvo tem para contar prazo.
         *
         * <p>É propriedade do <b>dado</b>, não da política: {@code
         * acesso_observado} guarda o dia em que o acesso aconteceu e mais nada;
         * pedir-lhe "conte da publicação do book" não é uma regra difícil, é uma
         * regra sem referente. Uma política que declara um marco que o alvo não
         * possui é recusada em vez de silenciosamente reinterpretada — porque a
         * reinterpretação mais provável seria cair no marco disponível, que é
         * justamente o erro do art. 7º, XXIX: contar da competência em vez do
         * desligamento apaga prova ainda exigível.
         */
        public Marco marcoDisponivel() {
            return marcoDisponivel;
        }

        /**
         * Se o SGDF sabe aplicar esta ação a este alvo hoje.
         *
         * <p><b>A capacidade é do PAR, não do alvo.</b> Saber listar documentos
         * vencidos não é saber apagá-los, e tratar as duas como a mesma
         * capacidade faria o sistema aceitar uma política de eliminação de
         * evidência fiscal por ter sido escrito o código de uma lista.
         *
         * <p><b>Falso aqui vira recusa nomeada, nunca zero silencioso.</b> Um
         * par aprovado pelo jurídico que o sistema não sabe tratar e que
         * simplesmente não aparecesse no resultado produziria o pior desfecho
         * possível: o DPO leria "expurgo executado, 0 itens" e concluiria que a
         * política está cumprida, quando ela nunca foi aplicada. A ausência tem
         * de ser dita.
         */
        public boolean sabeFazer(Acao acao) {
            return acao != null && sabeFazer.contains(acao);
        }

        public static Alvo de(String texto) {
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

    /**
     * De onde o prazo conta.
     *
     * <p><b>O marco importa mais que o prazo, e é onde se erra.</b> "Cinco anos"
     * não diz nada sem dizer cinco anos a partir de quê. A prescrição do
     * art. 7º, XXIX da Constituição corre da <i>extinção do contrato de
     * trabalho</i>, não da competência: um documento da competência 2020-01 de
     * alguém desligado em 2029 ainda é prova em 2034. Contado da competência,
     * ele teria sido apagado em 2025 — quatro anos antes de deixar de ser
     * exigível, e sem sintoma nenhum até alguém precisar dele.
     */
    public enum Marco {
        /** A data em que a própria linha nasceu. */
        REGISTRO,
        /** O último dia da competência. */
        FIM_DA_COMPETENCIA,
        /** A selagem da evidência. */
        PUBLICACAO_DO_BOOK,
        /** A extinção do vínculo — o marco do art. 7º, XXIX da CF. */
        DESLIGAMENTO_DO_PROFISSIONAL;

        public static Marco de(String texto) {
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

    /** O que acontece ao vencer. */
    public enum Acao {
        /** Apaga. */
        EXPURGAR,
        /** Art. 16: guarda o fato, perde o titular. */
        ANONIMIZAR,
        /**
         * Vence e vira lista para humano, não para o agendador.
         *
         * <p>É a ação proposta para o documento comprobatório, e a escolha é
         * deliberada: nenhum documento fiscal sai por decisão de agendador. O
         * custo de guardar demais é retenção indevida; o de apagar cedo é perder
         * a prova no meio de uma reclamatória, e esse não tem desfazer.
         */
        REVISAR;

        public static Acao de(String texto) {
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

    /** Aprovada por alguém, com nome e data. É a única porta do expurgo. */
    public boolean aprovada() {
        return aprovadoEm != null && aprovadoPor != null && !aprovadoPor.isBlank();
    }

    /**
     * A data-limite: tudo cujo marco seja <b>anterior</b> a ela venceu.
     *
     * <p>Estrito, não inclusivo. No dia exato em que o prazo completa, o dado
     * ainda está no último dia de guarda; apagá-lo ali é apagar um dia cedo, e
     * um dia cedo numa prescrição é a diferença entre ter e não ter a prova na
     * audiência.
     */
    public LocalDate corte(LocalDate hoje) {
        return hoje.minusMonths(prazoMeses);
    }

    /**
     * O que impede esta classe de ser aplicada hoje, ou nulo se nada impede.
     *
     * <p>Devolve texto e não booleano de propósito: "não foi aplicada" sem o
     * motivo manda o DPO procurar, e é justamente o relatório dele que esta
     * frase alimenta.
     */
    public String impedimento() {
        if (!aprovada()) {
            return "classe proposta e ainda não aprovada — o prazo de guarda é "
                    + "decisão do jurídico e do DPO, não do sistema (pendência A08)";
        }
        if (alvo.marcoDisponivel() != marco) {
            return "aprovada com marco " + marco + ", e o alvo " + alvo + " só possui "
                    + (alvo.marcoDisponivel() == null ? "marco nenhum"
                       : "a data de " + alvo.marcoDisponivel())
                    + " — a política não tem de onde contar o prazo";
        }
        if (!alvo.sabeFazer(acao)) {
            return "aprovada, e o SGDF ainda NÃO sabe aplicar a ação " + acao
                    + " ao alvo " + alvo + " — a política está válida e não está "
                    + "sendo cumprida";
        }
        return null;
    }
}
