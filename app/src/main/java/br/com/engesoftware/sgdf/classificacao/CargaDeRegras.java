package br.com.engesoftware.sgdf.classificacao;

import java.util.List;

/**
 * Carga inicial das regras de reconhecimento do bloco corporativo (cap. 8.5).
 *
 * <p><b>Isto é cadastro colocado em código como ponto de partida verificável,
 * não o lugar definitivo.</b> O cap. 1, princípio 2, manda que acrescentar um
 * tipo documental não exija compilar nada; o destino destas regras é a tabela
 * {@code regra_reconhecimento}, e o seed correspondente está em
 * {@code db/seed/V103}. Aqui elas existem para que a medição de precisão da
 * F1-03 seja reproduzível sem banco.
 *
 * <p>Toda âncora abaixo foi <b>lida no documento real</b> da massa de 06 e
 * 07/2026, no texto já normalizado. Nenhuma foi suposta a partir do nome do
 * tipo — o que estava suposto, na tabela do cap. 8.5, é justamente o que a
 * massa corrigiu.
 */
public final class CargaDeRegras {

    private CargaDeRegras() {}

    public static List<RegraDeReconhecimento> corporativas() {
        return List.of(
                // ---- Certidões -------------------------------------------------
                // O documento real é uma certidão POSITIVA COM EFEITOS DE
                // NEGATIVA: a âncora não pode exigir "negativa" no título.
                RegraDeReconhecimento.de("CER.CND_RFB", List.of(
                        Ancora.discriminante("tributos federais e a divida ativa da uniao", 3),
                        Ancora.de("procuradoria-geral da fazenda nacional", 2),
                        Ancora.de("secretaria da receita federal do brasil", 2),
                        Ancora.de("certidao (negativa|positiva com efeitos de negativa)", 1))),

                RegraDeReconhecimento.de("CER.CNDT", List.of(
                        Ancora.discriminante("certidao negativa de debitos trabalhistas", 3),
                        Ancora.de("justica do trabalho", 2),
                        Ancora.de("certidao no?:", 1),
                        Ancora.de("validade:", 1))),

                RegraDeReconhecimento.de("CER.CRF_FGTS", List.of(
                        Ancora.discriminante("certificado de regularidade do fgts", 3),
                        Ancora.de("caixa economica federal", 2),
                        Ancora.de("inscricao:", 1),
                        Ancora.de("lei 8\\.036", 1))),

                RegraDeReconhecimento.de("CER.CND_ESTADUAL", List.of(
                        Ancora.discriminante("subsecretaria da receita", 3),
                        Ancora.de("secretaria de estado de economia", 2),
                        Ancora.de("certidao negativa de debitos", 2),
                        Ancora.de("cf/df", 1))),

                RegraDeReconhecimento.de("CER.SICAF", List.of(
                        Ancora.discriminante(
                                "sistema de cadastramento unificado de fornecedores", 3),
                        Ancora.de("sicaf", 2),
                        Ancora.de("situacao do fornecedor", 2))),

                // As duas certidões do TJDFT compartilham o cabeçalho inteiro —
                // mesmo tribunal, mesma fórmula, mesmas instâncias. Só a
                // expressão da distribuição as separa, e por isso ela é
                // discriminante nas duas.
                RegraDeReconhecimento.de("CER.CND_CIVEL_CRIMINAL", List.of(
                        Ancora.discriminante("acoes civeis e criminais", 3),
                        Ancora.de("certidao (negativa|positiva) de distribuicao", 2),
                        Ancora.de("1a e 2a instancias", 1),
                        Ancora.de("tribunal de justica", 1))),

                RegraDeReconhecimento.de("CER.CND_FALENCIA", List.of(
                        Ancora.discriminante("falencias e recuperacoes judiciais", 3),
                        Ancora.de("certidao (negativa|positiva) de distribuicao", 2),
                        Ancora.de("1a e 2a instancias", 1),
                        Ancora.de("tribunal de justica", 1))),

                // ---- INSS e tributos ------------------------------------------
                // O discriminante NÃO pode ser nada da composição do DARF: o
                // comprovante bancário reproduz o DARF inteiro dentro dele, com
                // "composicao do documento de arrecadacao", "periodo de
                // apuracao" e "valor total do documento". Os dois marcavam 1,00.
                // O que só existe na DCTFWeb é o RECIBO DE TRANSMISSÃO — que é
                // exatamente o que o cap. 8.5 declara como âncora do tipo.
                RegraDeReconhecimento.de("INS.DCTFWEB", List.of(
                        Ancora.discriminante("dctfweb|recibo de entrega", 3),
                        Ancora.de("documento de arrecadacao de receitas federais", 2),
                        Ancora.de("periodo de apuracao", 1),
                        Ancora.de("composicao do documento de arrecadacao", 1))),

                // ---- FGTS ------------------------------------------------------
                RegraDeReconhecimento.de("FGT.GUIA", List.of(
                        Ancora.discriminante("guia do fgts digital", 3),
                        Ancora.de("valor a recolher", 2),
                        Ancora.de("identificador", 1),
                        Ancora.de("pagar este documento ate", 1))),

                RegraDeReconhecimento.de("FGT.RELATORIO_DIGITAL", List.of(
                        Ancora.discriminante("relacao de trabalhadores", 3),
                        Ancora.de("detalhe da guia a ser emitida", 2),
                        Ancora.de("qtd\\. trabalhadores fgts", 2),
                        Ancora.de("nome trabalhador", 1))),

                // ---- Folha e benefícios ---------------------------------------
                RegraDeReconhecimento.de("FOL.CONTRACHEQUE", List.of(
                        Ancora.discriminante("recibo de pagamento", 3),
                        Ancora.de("total de proventos", 2),
                        Ancora.de("total de descontos", 2),
                        Ancora.de("liquido a receber", 2))),

                RegraDeReconhecimento.de("BEN.RELACAO_VA_VR", List.of(
                        Ancora.discriminante("discriminacao dos beneficios", 3),
                        Ancora.de("relatorio de transacao", 2),
                        Ancora.de("total de bene ?ciarios", 2),
                        Ancora.de("disponibilizacao do beneficio", 1))));
    }

    /**
     * Comprovantes bancários.
     *
     * <p>Cap. 8.5 já registrava: <i>"sem âncora fixa — identificado pelo
     * pareamento"</i>. A massa real confirma e agrava: os comprovantes de INSS e
     * de IRRF do Santander são <b>textualmente indistinguíveis</b> — mesmo
     * cabeçalho, mesma expressão "comprovante de pagamento de darf", e o código
     * de receita que os separaria não está impresso no comprovante.
     *
     * <p>Por isso estas regras identificam a FAMÍLIA, não o tipo. Quem decide
     * qual obrigação o comprovante paga é o pareamento (R01/R02): valor, data e
     * identificador contra a guia. Uma regra que fingisse distinguir os dois
     * classificaria metade deles errado com score 1,00 — o pior resultado
     * possível, porque não pediria triagem.
     */
    public static List<RegraDeReconhecimento> comprovantesBancarios() {
        return List.of(
                RegraDeReconhecimento.de("CMP.DARF", List.of(
                        Ancora.discriminante("comprovante de pagamento de darf", 3),
                        Ancora.de("codigo de barras", 1),
                        Ancora.de("data de pagamento", 1),
                        Ancora.de("valor total", 1))),

                RegraDeReconhecimento.de("CMP.TRANSFERENCIA", List.of(
                        Ancora.discriminante("dados do pagamento", 2),
                        Ancora.de("pagador", 1),
                        Ancora.de("destinatario", 1))),

                RegraDeReconhecimento.de("CMP.BOLETO", List.of(
                        Ancora.discriminante("pagamento de boleto", 3),
                        Ancora.de("linha digitavel", 2),
                        Ancora.de("nosso numero", 1))),

                RegraDeReconhecimento.de("CMP.LOTE_SALARIOS", List.of(
                        Ancora.discriminante("lista de comprovantes", 3),
                        Ancora.de("numero do pagamento", 2),
                        Ancora.de("total compromissos", 2))));
    }

    /** Tudo o que a carga inicial conhece. */
    public static List<RegraDeReconhecimento> todas() {
        List<RegraDeReconhecimento> todas =
                new java.util.ArrayList<>(corporativas());
        todas.addAll(comprovantesBancarios());
        return List.copyOf(todas);
    }
}
