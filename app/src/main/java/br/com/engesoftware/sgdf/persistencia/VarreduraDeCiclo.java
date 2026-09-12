package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.coleta.ArquivoColetado;
import br.com.engesoftware.sgdf.coleta.ResultadoVarredura;
import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import br.com.engesoftware.sgdf.pipeline.DocumentoProcessado;
import br.com.engesoftware.sgdf.pipeline.Pipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Costura a coleta ao banco: varredura → pipeline → gravação.
 *
 * <p>É a última junta da cadeia origem → documento. Fecha, com o
 * {@code Agendador} e o {@code GravadorDoPipeline}, a metade de engenharia da
 * pendência RA-07 — os gatilhos continuam desligados, gated na F3-01.
 *
 * <p><b>Recebe o {@link ResultadoVarredura} pronto e não abre conexão nenhuma.</b>
 * Quem tem o {@code ClienteWebDav} varre e entrega o resultado. A separação é a
 * mesma que já se provou certa duas vezes nesta base — o {@code Pipeline} não
 * grava, o {@code Classificador} não lê arquivo — e paga o mesmo dividendo:
 * esta classe é exercitada ponta a ponta contra o banco <b>sem servidor
 * WebDAV</b>, que é a única forma de a costura ter teste de verdade.
 *
 * <p><b>Uma varredura incompleta não reporta sucesso.</b> O cap. 14.1 manda que
 * indisponibilidade da origem gere alerta e nunca perda: o que foi ingerido
 * fica, e o job termina em {@link Agendador.FalhaParcial} <i>com a contagem</i>.
 * Reportar SUCESSO sobre uma varredura truncada faria o alerta de job silencioso
 * calar justamente quando metade da pasta não foi lida — e o painel diria que a
 * competência está completa porque o sistema não olhou o resto.
 */
public final class VarreduraDeCiclo {

    private final Sgdf sgdf;
    private final Pipeline pipeline;

    public VarreduraDeCiclo(Sgdf sgdf, Pipeline pipeline) {
        this.sgdf = sgdf;
        this.pipeline = pipeline;
    }

    /**
     * Ingere o que a varredura trouxe.
     *
     * @param resultado o que o {@code Varredura} coletou, ignorou e não conseguiu
     * @return quantos documentos entraram — o número que vai ao {@code Agendador}
     */
    public Ingerida ingerir(UUID cicloId, UUID contratoId, String competencia,
                            ResultadoVarredura resultado, String ator) {
        // 1. O QUE A VARREDURA NÃO INGERIU VAI PARA O PAINEL DE ORGANIZAÇÃO
        //    ANTES DE QUALQUER OUTRA COISA.
        //
        // Se o lote falhar adiante, as cópias de conflito e os temporários já
        // estão registrados. Deixar para o fim faria a informação mais barata de
        // produzir ser a primeira a se perder.
        RepositorioDeOrganizacao.Registro organizacao =
                new RepositorioDeOrganizacao(sgdf).registrar(contratoId, competencia, resultado);

        // 2. Cada arquivo passa pelo pipeline. O antivírus já rodou na coleta —
        //    um arquivo que chegou em `coletados` foi verificado lá, e repetir a
        //    verificação aqui gastaria o dobro sem decidir nada de novo.
        Set<String> hashesDoCiclo = hashesJaVinculados(cicloId);
        List<GravadorDoPipeline.Item> itens = new ArrayList<>();
        List<String> falhasDeProcessamento = new ArrayList<>();
        for (ArquivoColetado arquivo : resultado.coletados) {
            try {
                DocumentoProcessado processado = pipeline.processar(
                        nomeDe(arquivo.caminho()), arquivo.conteudo(),
                        VeredictoAntivirus.limpo(), hashesDoCiclo, null);
                itens.add(new GravadorDoPipeline.Item(processado, arquivo.caminho(),
                        arquivo.versao()));
            } catch (RuntimeException e) {
                // DEFESA NÃO MEDIDA, E DITA COMO TAL.
                //
                // Extrair é a etapa mais frágil da cadeia (PDF malformado, fonte
                // exótica, arquivo truncado no meio do download), e um deles não
                // pode levar os outros. Só que HOJE o Pipeline já converte
                // ExtracaoInvalida em veredito V2 em vez de deixar subir —
                // removendo este catch, nenhuma asserção cai. Ele protege de um
                // Pipeline futuro que volte a propagar, não de um caso atual, e
                // chamá-lo de garantia verificada seria falso. Mesmo registro do
                // RA-12.
                falhasDeProcessamento.add(nomeDe(arquivo.caminho()) + ": "
                        + e.getClass().getSimpleName());
            }
        }

        GravadorDoPipeline.Lote lote = new GravadorDoPipeline(sgdf)
                .gravarLote(cicloId, itens, ator);

        List<String> falhas = new ArrayList<>(falhasDeProcessamento);
        falhas.addAll(lote.falhas());
        resultado.falhas.forEach(f -> falhas.add(f.caminho() + ": " + f.erro()));

        Ingerida ingerida = new Ingerida(lote.processados(), lote.ineditos(),
                lote.emTriagem(), lote.semExigencia(), organizacao,
                List.copyOf(falhas), resultado.truncada, resultado.motivoTruncamento);

        if (ingerida.incompleta()) {
            throw new Agendador.FalhaParcial(ingerida.motivoDaIncompletude(),
                    ingerida.processados());
        }
        return ingerida;
    }

    // -------------------------------------------------------------------------

    /** V7 é por exigência; no escopo do ciclo, é o conjunto de todas elas. */
    private Set<String> hashesJaVinculados(UUID cicloId) {
        String sql = """
                SELECT DISTINCT d.hash_sha256
                FROM   vinculo_exigencia_documento v
                JOIN   documento d ON d.id = v.documento_id
                JOIN   exigencia e ON e.id = v.exigencia_id
                WHERE  e.ciclo_id = ?
                """;
        try (java.sql.PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                Set<String> hashes = new java.util.LinkedHashSet<>();
                while (rs.next()) {
                    hashes.add(rs.getString(1));
                }
                return hashes;
            }
        } catch (java.sql.SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler os hashes do ciclo", e);
        }
    }

    static String nomeDe(String caminho) {
        int barra = caminho.lastIndexOf('/');
        return barra < 0 ? caminho : caminho.substring(barra + 1);
    }

    /**
     * O desfecho da varredura de um ciclo.
     *
     * @param truncada a varredura bateu num limite do cap. 8.1 e parou
     */
    public record Ingerida(int processados, long ineditos, long emTriagem, long semExigencia,
                           RepositorioDeOrganizacao.Registro organizacao, List<String> falhas,
                           boolean truncada, String motivoTruncamento) {

        public Ingerida {
            falhas = List.copyOf(falhas);
        }

        /**
         * Truncada ou com falha: o que foi ingerido vale, e o job não diz
         * SUCESSO.
         */
        public boolean incompleta() {
            return truncada || !falhas.isEmpty();
        }

        public String motivoDaIncompletude() {
            if (!incompleta()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            if (truncada) {
                sb.append("varredura truncada (")
                        .append(motivoTruncamento == null ? "limite do cap. 8.1"
                                : motivoTruncamento)
                        .append("): a pasta não foi lida inteira e o que falta não está "
                                + "no painel. ");
            }
            if (!falhas.isEmpty()) {
                sb.append(falhas.size()).append(" arquivo(s) não entraram: ")
                        .append(String.join("; ", falhas.subList(0, Math.min(5, falhas.size()))));
                if (falhas.size() > 5) {
                    sb.append("; e mais ").append(falhas.size() - 5);
                }
            }
            return sb.toString().strip();
        }
    }
}
