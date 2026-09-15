package br.com.engesoftware.sgdf.persistencia;

import br.com.engesoftware.sgdf.classificacao.Bonus;
import br.com.engesoftware.sgdf.classificacao.Candidato;
import br.com.engesoftware.sgdf.classificacao.Decisao;
import br.com.engesoftware.sgdf.coleta.ArquivoColetado;
import br.com.engesoftware.sgdf.coleta.EstadoConhecido;
import br.com.engesoftware.sgdf.coleta.ResultadoVarredura;
import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import br.com.engesoftware.sgdf.pipeline.DocumentoProcessado;
import br.com.engesoftware.sgdf.pipeline.Pipeline;
import br.com.engesoftware.sgdf.privacidade.Mascara;
import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A varredura que lê a pasta, identifica os documentos e <b>não grava nada</b>.
 *
 * <p><b>Por que este modo existe.</b> Apontar a varredura para uma pasta real
 * pela primeira vez é uma decisão de uma via só: os documentos entram no banco,
 * a trilha do cap. 16 registra que entraram, e a temporalidade (A08) passa a
 * valer sobre eles. Se a identificação estiver ruim — regra mal calibrada,
 * emissor que mudou o layout, pasta com material de outra competência — o
 * conserto é apagar documento de um sistema cujo esquema foi construído para
 * <i>não</i> deixar apagar. A pasta do Departamento de Pessoal tem contracheque,
 * FOPAG, relação de VA/VR e a guia do FGTS com CPF, nome e remuneração
 * individual de cada colaborador; entrar com isso errado não é um mau teste, é
 * um incidente de privacidade.
 *
 * <p>A simulação dá a mesma informação sem a consequência: percorre a pasta,
 * baixa, passa cada arquivo pelo <b>mesmo</b> {@link Pipeline} da ingestão real
 * e relata o que identificaria. Quem opera vê a qualidade do reconhecimento
 * sobre os arquivos de verdade <i>antes</i> de qualquer documento existir no
 * sistema.
 *
 * <p><b>O mesmo Pipeline, e isso é o requisito, não um detalhe.</b> Uma
 * simulação que reimplementasse a identificação responderia sobre si mesma. Ela
 * também carrega o bônus de alias exatamente como a {@link VarreduraDeCiclo}
 * carrega (RA-19): sem isso, a prévia divergiria da execução real justamente
 * nos arquivos que a triagem já ensinou, que são os que mais interessam.
 *
 * <p><b>Duas diferenças deliberadas em relação à execução real</b>, ambas a
 * favor de quem lê o relatório:
 * <ol>
 *   <li><b>Ignora o delta do cap. 8.1.</b> A varredura real pula o que já
 *       registrou com a mesma versão; a simulação olha a pasta INTEIRA, sempre.
 *       Uma prévia que esconde metade dos arquivos porque eles já foram
 *       ingeridos não serve para conferir identificação — e o operador não teria
 *       como distinguir "não está mais lá" de "não te mostrei".
 *   <li><b>Não registra o painel de organização.</b> Cópia de conflito e
 *       temporário aparecem no relatório, mas não viram linha em nenhuma tabela:
 *       gravar isso já seria gravar.
 * </ol>
 *
 * <p><b>A promessa de não gravar não depende de disciplina.</b> Todo o trabalho
 * roda dentro de {@link Sgdf#emLeituraEstrita} — o servidor recusa qualquer
 * escrita e o bloco termina em rollback mesmo no caminho feliz. Ver o javadoc de
 * lá para por que uma promessa dessas sustentada só por revisão de código não
 * sobrevive ao primeiro método auxiliar acrescentado por alguém apressado.
 *
 * <p><b>O relatório é mascarado.</b> Nome de arquivo e motivo de veredito passam
 * por {@link Mascara#texto} antes de sair — a F2-06 exige que CPF nunca apareça
 * em claro em UI ou log, e um relatório de simulação é as duas coisas. Fica o
 * risco residual de NOME de pessoa no nome do arquivo ("contracheque JOAO DA
 * SILVA.pdf"): a máscara não o pega, porque reconhecer nome próprio em texto
 * livre sem lista de referência erra dos dois lados. Está registrado como tal e
 * não disfarçado de resolvido.
 */
public class SimulacaoDeVarredura {

    /**
     * O delta do cap. 8.1 DESLIGADO — a prévia olha a pasta inteira, sempre.
     *
     * <p>Mora aqui, e não solto no controlador, porque é uma decisão de projeto
     * com justificativa (ver o javadoc da classe) e não um argumento
     * conveniente. Quem quiser mudá-la tropeça na justificativa primeiro.
     *
     * <p><b>Lacuna conhecida:</b> nenhum teste automatizado prova que o
     * controlador passa ISTO e não o estado real da origem. A cobertura da
     * {@code VarreduraController} não tem hoje um servidor WebDAV de mentira, e
     * afirmar que a garantia está testada seria falso. Está no ACHADOS.
     */
    public static final EstadoConhecido SEM_DELTA = caminho -> null;

    private final Sgdf sgdf;
    private final Pipeline pipeline;

    public SimulacaoDeVarredura(Sgdf sgdf, Pipeline pipeline) {
        this.sgdf = sgdf;
        this.pipeline = pipeline;
    }

    /**
     * Identifica o que a varredura encontraria, sem gravar.
     *
     * @param cicloId  ciclo cujo conjunto de exigências abertas serve de gabarito
     * @param resultado o que a {@code Varredura} coletou — ela já rodou o antivírus
     */
    public Simulada simular(UUID cicloId, String competencia, String raiz,
                            ResultadoVarredura resultado) {
        return sgdf.emLeituraEstrita(conexao -> executar(cicloId, competencia, raiz, resultado));
    }

    private Simulada executar(UUID cicloId, String competencia, String raiz,
                              ResultadoVarredura resultado) {
        RepositorioDeAlias aliases = RepositorioDeAlias.doCadastro(sgdf);
        Set<String> tiposExigidos = tiposComExigenciaAberta(cicloId);

        List<Item> itens = new ArrayList<>();
        for (ArquivoColetado arquivo : resultado.coletados) {
            String nome = VarreduraDeCiclo.nomeDe(arquivo.caminho());
            try {
                Bonus bonus = aliases.doNome(nome);
                DocumentoProcessado p = pipeline.processar(
                        nome, arquivo.conteudo(), VeredictoAntivirus.limpo(),
                        // SEM HASHES CONHECIDOS, E DE PROPÓSITO. Passar os do
                        // ciclo faria a V7 reprovar por duplicidade os arquivos
                        // que a varredura real já ingeriu numa rodada anterior —
                        // e "duplicado" na prévia leria como problema no arquivo
                        // quando é apenas "isto já está no sistema". A simulação
                        // responde sobre IDENTIFICAÇÃO; a duplicidade é decisão
                        // da ingestão, que continua sendo dela.
                        Set.of(), bonus);
                itens.add(itemDe(p, arquivo, tiposExigidos));
            } catch (RuntimeException e) {
                itens.add(Item.naoProcessado(Mascara.texto(nome), arquivo.tamanho(),
                        e.getClass().getSimpleName()));
            }
        }
        itens.sort(Comparator.comparing(Item::nome));

        List<Descartado> descartados = new ArrayList<>();
        resultado.ignorados.forEach(i -> descartados.add(new Descartado(
                Mascara.texto(i.caminho()), "IGNORADO",
                i.motivo() + (i.detalhe() == null ? "" : ": " + Mascara.texto(i.detalhe())))));
        resultado.conflitos.forEach(c -> descartados.add(new Descartado(
                Mascara.texto(c.caminho()), "CONFLITO_DE_SINCRONIA",
                "cópia de conflito: nunca vira documento (cap. 8.1)")));
        resultado.infectados.forEach(r -> descartados.add(new Descartado(
                Mascara.texto(r.caminho()), "INFECTADO", r.assinatura())));
        resultado.falhas.forEach(f -> descartados.add(new Descartado(
                Mascara.texto(f.caminho()), "FALHA_NA_COLETA", Mascara.texto(f.erro()))));
        descartados.sort(Comparator.comparing(Descartado::caminho));

        Map<String, Long> porTipo = new LinkedHashMap<>();
        for (Item i : itens) {
            if (i.tipo() != null) {
                porTipo.merge(i.tipo(), 1L, Long::sum);
            }
        }

        List<String> exigenciasSemDocumento = new ArrayList<>(tiposExigidos);
        exigenciasSemDocumento.removeAll(porTipo.keySet());
        exigenciasSemDocumento.sort(Comparator.naturalOrder());

        return new Simulada(raiz, competencia, resultado.visitados(), itens, descartados,
                porTipo, exigenciasSemDocumento, resultado.truncada,
                resultado.motivoTruncamento, aliases.desligadoPorque());
    }

    private Item itemDe(DocumentoProcessado p, ArquivoColetado arquivo,
                        Set<String> tiposExigidos) {
        String nome = Mascara.texto(p.nomeArquivo());
        List<String> evidencias = p.tipoReconhecido()
                .flatMap(c -> c.melhor())
                .map(Candidato::evidencias)
                .orElse(List.of())
                .stream().map(Mascara::texto).toList();

        List<String> reprovacoes = p.reprovacoes().stream()
                .map(r -> r.codigo() + ": " + Mascara.texto(r.motivo()))
                .toList();

        // OS DEMAIS CANDIDATOS VIAJAM JUNTO, E É O DADO MAIS ÚTIL DA PRÉVIA.
        //
        // "TRIAGEM" sozinho não diz nada a quem vai calibrar a regra. "TRIAGEM,
        // porque CND_RFB marcou 0,62 e CNDT marcou 0,58" diz exatamente onde o
        // limiar está e o que aconteceria se ele mudasse.
        List<String> outros = p.tipoReconhecido()
                .map(c -> c.candidatos().stream()
                        .skip(1).limit(3)
                        .map(x -> x.identidade() + " %.3f".formatted(x.score()))
                        .toList())
                .orElse(List.of());

        String tipo = p.tipo();
        Decisao decisao = p.decisao();
        return new Item(nome, arquivo.tamanho(),
                p.mime() == null ? null : p.mime().name(),
                decisao == null ? "BARRADO" : decisao.name(),
                tipo,
                p.tipoReconhecido().flatMap(c -> c.melhor()).map(Candidato::score).orElse(null),
                p.tipoReconhecido().map(c -> Mascara.texto(c.motivo())).orElse(null),
                evidencias, outros, reprovacoes,
                tipo != null && tiposExigidos.contains(tipo));
    }

    /**
     * Os códigos de tipo que o ciclo ainda espera.
     *
     * <p>Mesmo recorte de status que o {@code GravadorDoPipeline} usa para achar
     * a exigência candidata. Se os dois divergirem, a prévia diria "cobre uma
     * exigência" sobre um arquivo que a ingestão deixaria sem vínculo — e a
     * simulação teria mentido exatamente no ponto que ela existe para conferir.
     */
    private Set<String> tiposComExigenciaAberta(UUID cicloId) {
        String sql = """
                SELECT DISTINCT t.codigo
                FROM   exigencia e
                JOIN   tipo_documental t ON t.id = e.tipo_id
                WHERE  e.ciclo_id = ?
                  AND  e.status IN ('PENDENTE', 'REJEITADO', 'EM_TRIAGEM')
                ORDER  BY t.codigo
                """;
        try (PreparedStatement ps = sgdf.conexao().prepareStatement(sql)) {
            ps.setObject(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> codigos = new LinkedHashSet<>();
                while (rs.next()) {
                    codigos.add(rs.getString(1));
                }
                return codigos;
            }
        } catch (SQLException e) {
            throw new Sgdf.FalhaDePersistencia("falha ao ler as exigências do ciclo", e);
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Um arquivo, e o que a identificação disse dele.
     *
     * @param score          do melhor candidato, ou nulo quando nenhum pontuou
     * @param outrosCandidatos até três seguintes, para calibrar limiar
     * @param cobreExigencia o tipo identificado corresponde a uma exigência aberta
     */
    public record Item(String nome, long tamanho, String mime, String decisao, String tipo,
                       Double score, String motivo, List<String> evidencias,
                       List<String> outrosCandidatos, List<String> reprovacoesUnitarias,
                       boolean cobreExigencia) {

        public Item {
            evidencias = List.copyOf(evidencias);
            outrosCandidatos = List.copyOf(outrosCandidatos);
            reprovacoesUnitarias = List.copyOf(reprovacoesUnitarias);
        }

        static Item naoProcessado(String nome, long tamanho, String erro) {
            return new Item(nome, tamanho, null, "FALHA_NO_PROCESSAMENTO", null, null,
                    erro, List.of(), List.of(), List.of(), false);
        }
    }

    /** O que a varredura viu e não trouxe, com o motivo em português. */
    public record Descartado(String caminho, String classe, String motivo) {}

    /**
     * O relatório da simulação.
     *
     * @param exigenciasSemDocumento tipos que o ciclo espera e nenhum arquivo
     *                               desta pasta cobriria
     */
    public record Simulada(String raiz, String competencia, int visitados, List<Item> itens,
                           List<Descartado> descartados, Map<String, Long> porTipo,
                           List<String> exigenciasSemDocumento, boolean truncada,
                           String motivoTruncamento, String aliasDesligado) {

        public Simulada {
            itens = List.copyOf(itens);
            descartados = List.copyOf(descartados);
            porTipo = Map.copyOf(porTipo);
            exigenciasSemDocumento = List.copyOf(exigenciasSemDocumento);
        }

        public long comDecisao(Decisao decisao) {
            return itens.stream().filter(i -> decisao.name().equals(i.decisao())).count();
        }

        /**
         * Quantos entrariam sozinhos no sistema se isto não fosse simulação.
         *
         * <p>É o número que decide se a varredura real pode ser ligada. Baixo
         * demais, a regra precisa de calibragem antes; alto demais sem ninguém
         * ter conferido a amostra também é motivo de desconfiança.
         */
        public long automaticos() {
            return comDecisao(Decisao.AUTOMATICA);
        }
    }
}
