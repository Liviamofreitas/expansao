package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.pipeline.DocumentoProcessado;
import br.com.engesoftware.sgdf.pipeline.Pipeline;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A metade do pipeline que grava — o que o {@code Pipeline} deliberadamente não
 * faz.
 *
 * <p>O {@code Pipeline} responde <i>o que o documento é</i>; isto responde
 * <i>a que obrigação ele serve</i>, e só aqui há ciclo para comparar. A
 * separação é o que permite medir a precisão de classificação sem banco
 * (achados § 42).
 *
 * <p><b>Um arquivo que falha não derruba o lote.</b> Uma varredura de trezentos
 * documentos que morre no primeiro PDF corrompido entrega zero. O laço trata
 * cada arquivo isolado, e o resultado carrega <b>quantos falharam</b> junto de
 * quantos entraram — uma contagem que esconde falhas é a mesma armadilha do
 * indicador que fica bonito porque o pior caso saiu da conta.
 *
 * <p><b>O terceiro destino, que não existia.</b> Um documento classificado com
 * confiança pode não ter exigência a que servir: o tipo não é exigido neste
 * ciclo, ou é de escopo PROFISSIONAL e há quarenta e duas exigências do mesmo
 * tipo sem nada que diga de quem é o contracheque. Ele não é desconhecido — o
 * sistema sabe o que ele é — e não é candidato de exigência nenhuma. Sem um
 * lugar, ele cairia em <b>vista nenhuma</b>: o painel de desconhecidos filtra
 * {@code tipo_id IS NULL} e ele tem tipo; a fila de triagem lista candidaturas e
 * ele não tem uma. É o mesmo defeito do agendador morto (§ 46), em menor escala
 * — uma ausência que não produz sintoma. Ver
 * {@link ConsultaDoPainel#classificadosSemExigencia}.
 */
public final class GravadorDoPipeline {

    private final Sgdf sgdf;
    private final RepositorioDeDocumento documentos;
    private final RepositorioDeValidacao validacoes;
    private final RepositorioDeTriagem triagem;

    public GravadorDoPipeline(Sgdf sgdf) {
        this.sgdf = sgdf;
        this.documentos = new RepositorioDeDocumento(sgdf);
        this.validacoes = new RepositorioDeValidacao(sgdf);
        this.triagem = new RepositorioDeTriagem(sgdf);
    }

    /**
     * Grava um documento processado e o encaminha.
     *
     * @param cicloId ciclo ao qual o arquivo pertence, pela pasta de origem
     * @param etag    versão na origem, para o delta da próxima varredura
     */
    public Ingestao gravar(UUID cicloId, DocumentoProcessado processado, String caminho,
                           String etag, String ator) {
        Pipeline.Encaminhamento destino = Pipeline.encaminhamentoDe(processado);
        if (destino == Pipeline.Encaminhamento.DESCARTADO) {
            // NÃO GRAVA O DOCUMENTO, E É DELIBERADO.
            //
            // Um arquivo reprovado em V1 é infectado, vazio, ou tem extensão que
            // mente sobre o conteúdo. Criar linha em `documento` para ele o
            // colocaria na mesma tabela dos documentos que valem — e a partir
            // daí toda consulta precisaria lembrar de excluí-lo. O registro
            // dele é o achado de organização, que é onde a varredura já põe o
            // que encontrou e não ingeriu.
            return new Ingestao(null, destino, false, null,
                    processado.reprovacoes().isEmpty() ? "descartado"
                            : processado.reprovacoes().get(0).motivo());
        }

        Documento documento = new Documento("OWNCLOUD", caminho, processado.nomeArquivo(),
                processado.hashSha256(), processado.tamanho(), processado.mime().mime,
                formatoDe(processado.nomeArquivo()), "PENDENTE",
                processado.textoExtraido().map(t -> t.origem().name().contains("OCR"))
                        .orElse(false),
                null, null, null, null, etag, ator);
        DocumentoGravado gravado = documentos.gravar(documento);

        // Vereditos sempre, inclusive no reprocessamento: a tabela é histórico,
        // e uma segunda execução com regra nova precisa deixar as duas leituras.
        validacoes.gravarTodas(gravado.id(), processado.vereditos(), null);

        String tipo = processado.tipo();
        if (tipo == null) {
            return new Ingestao(gravado.id(), destino, gravado.inedito(), null,
                    "não reconhecido: vai para o painel de organização (cap. 8.3)");
        }
        anotarTipo(gravado.id(), tipo, processado.classificacao().melhor()
                .map(c -> c.score()).orElse(0.0));

        List<UUID> candidatas = exigenciasCandidatas(cicloId, tipo);
        if (candidatas.size() != 1) {
            // Zero e "mais de uma" são causas diferentes com a mesma resposta:
            // o sistema não escolhe por conta própria a qual obrigação o
            // documento serve. Dizer QUAL das duas é o que torna o caso
            // resolvível por quem olhar.
            return new Ingestao(gravado.id(), destino, gravado.inedito(), null,
                    candidatas.isEmpty()
                            ? "o tipo " + tipo + " não é exigido neste ciclo"
                            : "há " + candidatas.size() + " exigências do tipo " + tipo
                                    + " no ciclo e nada no documento diz a qual delas ele "
                                    + "pertence (escopo profissional)");
        }

        UUID candidatura = triagem.abrirCandidatura(candidatas.get(0), gravado.id(),
                tipoIdDe(tipo), processado.classificacao().melhor().map(c -> c.score())
                        .orElse(0.0),
                destino == Pipeline.Encaminhamento.VINCULAVEL
                        ? "classificado com confiança; aguarda confirmação"
                        : "abaixo do limiar automático ou com reprovação unitária",
                null);
        return new Ingestao(gravado.id(), destino, gravado.inedito(), candidatura, null);
    }

    /**
     * Grava um lote, e um item que falha não derruba os outros.
     *
     * @param itens pares (processado, caminho, etag) já produzidos pelo pipeline
     */
    public Lote gravarLote(UUID cicloId, List<Item> itens, String ator) {
        List<Ingestao> ingeridos = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        for (Item item : itens) {
            try {
                ingeridos.add(gravar(cicloId, item.processado(), item.caminho(), item.etag(),
                        ator));
            } catch (RuntimeException e) {
                // O NOME DO ARQUIVO VAI NA FALHA, E É O QUE A TORNA ACIONÁVEL.
                //
                // "12 falharam" manda alguém procurar; "12 falharam, e são
                // estes" manda alguém resolver.
                falhas.add(item.processado().nomeArquivo() + ": "
                        + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " — " + e.getMessage()));
            }
        }
        return new Lote(List.copyOf(ingeridos), List.copyOf(falhas));
    }

    // -------------------------------------------------------------------------

    /** As exigências do ciclo que este tipo poderia satisfazer. */
    private List<UUID> exigenciasCandidatas(UUID cicloId, String tipoCodigo) {
        String sql = """
                SELECT e.id
                FROM   exigencia e
                JOIN   tipo_documental t ON t.id = e.tipo_id
                WHERE  e.ciclo_id = ? AND t.codigo = ?
                  AND  e.status IN ('PENDENTE', 'REJEITADO', 'EM_TRIAGEM')
                ORDER  BY e.id
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            ps.setString(2, tipoCodigo);
            try (ResultSet rs = ps.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
                return ids;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao procurar a exigência do tipo", e);
        }
    }

    private UUID tipoIdDe(String codigo) {
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(
                "SELECT id FROM tipo_documental WHERE codigo = ?")) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler o tipo " + codigo, e);
        }
    }

    private void anotarTipo(UUID documentoId, String tipoCodigo, double confianca) {
        String sql = """
                UPDATE documento SET tipo_id = (SELECT id FROM tipo_documental WHERE codigo = ?),
                                     confianca = ?
                WHERE id = ? AND tipo_id IS NULL
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setString(1, tipoCodigo);
            ps.setBigDecimal(2, java.math.BigDecimal.valueOf(confianca)
                    .setScale(3, java.math.RoundingMode.HALF_UP));
            ps.setObject(3, documentoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao anotar o tipo do documento", e);
        }
    }

    private static String formatoDe(String nomeArquivo) {
        int ponto = nomeArquivo.lastIndexOf('.');
        return ponto < 0 ? "pdf"
                : nomeArquivo.substring(ponto + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Um item do lote, já processado pelo pipeline. */
    public record Item(DocumentoProcessado processado, String caminho, String etag) {}

    /**
     * O que aconteceu com um arquivo.
     *
     * @param documentoId  nulo quando barrado antes de gravar
     * @param candidatura  nulo quando não há exigência a que servir
     * @param observacao   por que não virou candidatura; nulo quando virou
     */
    public record Ingestao(UUID documentoId, Pipeline.Encaminhamento destino, boolean inedito,
                           UUID candidatura, String observacao) {

        public boolean vinculouAExigencia() {
            return candidatura != null;
        }

        /** Classificado e sem exigência — o terceiro destino. Ver a nota da classe. */
        public boolean semExigencia() {
            return documentoId != null && candidatura == null
                    && destino != Pipeline.Encaminhamento.ORGANIZACAO;
        }
    }

    /**
     * O desfecho do lote.
     *
     * <p>{@code processados()} conta o que ENTROU, e é o número que vai ao
     * {@code Agendador}. As falhas vão separadas: somá-las ao total faria uma
     * varredura que perdeu metade dos arquivos reportar o mesmo número de uma
     * que não perdeu nenhum.
     */
    public record Lote(List<Ingestao> ingeridos, List<String> falhas) {

        public Lote {
            ingeridos = List.copyOf(ingeridos);
            falhas = List.copyOf(falhas);
        }

        public int processados() {
            return ingeridos.size();
        }

        public long ineditos() {
            return ingeridos.stream().filter(Ingestao::inedito).count();
        }

        public long emTriagem() {
            return ingeridos.stream().filter(Ingestao::vinculouAExigencia).count();
        }

        public long semExigencia() {
            return ingeridos.stream().filter(Ingestao::semExigencia).count();
        }

        public boolean completo() {
            return falhas.isEmpty();
        }
    }
}
