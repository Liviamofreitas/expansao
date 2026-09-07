package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.indicadores.Indicador;
import br.com.engesoftware.sgdf.persistencia.ConsultaDeAuditoria;
import br.com.engesoftware.sgdf.persistencia.ConsultaDeIndicadores;
import br.com.engesoftware.sgdf.persistencia.ConsultaDeRecertificacao;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indicadores e exportações do cap. 21 — história F3-05.
 *
 * <p>Critério de aceite: <i>"KPI/KRI calculados por competência, exportáveis em
 * CSV"</i>.
 *
 * <p><b>Só quem enxerga todos os contratos recebe o agregado, e a razão é a
 * mesma do D+3.</b> Filtrar um indicador de competência pelos contratos do ator
 * produz outro número com o mesmo nome — e dois números chamados "NF em D+3" na
 * mesma organização é pior que um só. Quem tem recorte de contrato tem o painel
 * do seu ciclo, que responde à pergunta dele.
 */
@RestController
@RequestMapping("/api")
public class IndicadorController {

    private final AtorDaRequisicao atores;
    private final ConsultaDeIndicadores indicadores;
    private final ConsultaDeAuditoria auditoria;
    private final ConsultaDeRecertificacao recertificacao;

    public IndicadorController(AtorDaRequisicao atores, ConsultaDeIndicadores indicadores,
                               ConsultaDeAuditoria auditoria,
                               ConsultaDeRecertificacao recertificacao) {
        this.atores = atores;
        this.indicadores = indicadores;
        this.auditoria = auditoria;
        this.recertificacao = recertificacao;
    }

    /** Os nove do capítulo, com população e situação. */
    @GetMapping("/indicadores")
    public List<Map<String, Object>> daCompetencia(@RequestParam String competencia) {
        exigirVisaoGlobal(Permissao.VER_PAINEL);
        return indicadores.daCompetencia(competencia).stream()
                .map(IndicadorController::comoMapa).toList();
    }

    /** O detalhamento que o capítulo pede em dois indicadores. */
    @GetMapping("/indicadores/detalhe")
    public Map<String, List<Object[]>> detalhe(@RequestParam String competencia) {
        exigirVisaoGlobal(Permissao.VER_PAINEL);
        return Map.of(
                "pendencias_vencidas_por_area",
                indicadores.pendenciasVencidasPorArea(competencia),
                "divergencias_por_regra_e_contrato",
                indicadores.divergenciasPorRegra(competencia));
    }

    @GetMapping(value = "/indicadores.csv", produces = "text/csv")
    public ResponseEntity<String> csv(@RequestParam String competencia) {
        exigirVisaoGlobal(Permissao.VER_PAINEL);
        return comoAnexo("indicadores-" + competencia + ".csv",
                indicadores.csv(competencia));
    }

    /** Cap. 13: {@code /auditoria?objeto=&ator=&periodo=}, papel AUDITORIA. */
    @GetMapping("/auditoria")
    public List<ConsultaDeAuditoria.Registro> trilha(
            @RequestParam(required = false) String objetoTipo,
            @RequestParam(required = false) String objetoId,
            @RequestParam(required = false) String ator,
            @RequestParam(required = false) OffsetDateTime desde,
            @RequestParam(required = false) OffsetDateTime ate,
            @RequestParam(defaultValue = "500") int limite) {
        exigir(Permissao.AUDITAR);
        return auditoria.consultar(
                new ConsultaDeAuditoria.Filtro(objetoTipo, objetoId, ator, desde, ate), limite);
    }

    @GetMapping(value = "/auditoria.csv", produces = "text/csv")
    public ResponseEntity<String> trilhaCsv(
            @RequestParam(required = false) String objetoTipo,
            @RequestParam(required = false) String objetoId,
            @RequestParam(required = false) String ator,
            @RequestParam(required = false) OffsetDateTime desde,
            @RequestParam(required = false) OffsetDateTime ate,
            @RequestParam(defaultValue = "5000") int limite) {
        Ator quem = exigir(Permissao.AUDITAR);
        // A exportação registra a si mesma na trilha — ver ConsultaDeAuditoria.csv.
        return comoAnexo("auditoria.csv", auditoria.csv(
                new ConsultaDeAuditoria.Filtro(objetoTipo, objetoId, ator, desde, ate), limite,
                quem.identificador(), papelDe(quem)));
    }

    /**
     * Recertificação trimestral — história F3-06, requisito SEC-10.
     *
     * <p>Exige AUDITAR, e não CONFIGURAR_SISTEMA: quem administra o sistema não
     * é quem revisa quem tem acesso a ele. Dar ao ADMIN_SISTEMA o relatório dos
     * próprios acessos seria pedir que ele se recertificasse.
     */
    @GetMapping("/recertificacao")
    public Map<String, Object> recertificacao(
            @RequestParam java.time.LocalDate desde,
            @RequestParam java.time.LocalDate ate) {
        exigir(Permissao.AUDITAR);
        List<ConsultaDeRecertificacao.Acesso> acessos = recertificacao.relatorio(desde, ate);
        return Map.of(
                // A RESSALVA VEM PRIMEIRO NO JSON TAMBÉM.
                //
                // Não é decoração: um relatório de acesso que não declara a
                // própria cobertura convida quem aprova a lê-lo como completo, e
                // assinar uma revisão parcial acreditando ter revisto tudo
                // produz a evidência de conformidade sem o controle.
                "cobertura", ConsultaDeRecertificacao.RESSALVA,
                "periodo", Map.of("desde", desde, "ate", ate),
                "atores_observados", acessos.size(),
                "a_revisar", acessos.stream().filter(a -> !a.atencao().isEmpty()).count(),
                "acessos", acessos);
    }

    @GetMapping(value = "/recertificacao.csv", produces = "text/csv")
    public ResponseEntity<String> recertificacaoCsv(
            @RequestParam java.time.LocalDate desde,
            @RequestParam java.time.LocalDate ate) {
        exigir(Permissao.AUDITAR);
        return comoAnexo("recertificacao-" + desde + "-a-" + ate + ".csv",
                recertificacao.csv(desde, ate));
    }

    // -------------------------------------------------------------------------

    static Map<String, Object> comoMapa(Indicador i) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("codigo", i.codigo());
        m.put("indicador", i.nome());
        m.put("valor", i.valor());
        m.put("unidade", i.unidade().name());
        m.put("numerador", i.numerador());
        m.put("denominador", i.denominador());
        m.put("meta", i.meta().toString());
        m.put("situacao", i.situacao().name());
        m.put("descricao", i.descricaoDoValor());
        m.put("fonte", i.fonte());
        m.put("observacao", i.observacao());
        return m;
    }

    private static ResponseEntity<String> comoAnexo(String nome, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                // O charset é explícito: sem ele o Excel em pt-BR lê UTF-8 como
                // Latin-1 e "Divergências" vira "DivergÃªncias" na coluna que a
                // pessoa vai ler.
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }

    private Ator exigir(Permissao permissao) {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }

    private void exigirVisaoGlobal(Permissao permissao) {
        Ator ator = exigir(permissao);
        if (!ator.enxergaTodosOsContratos()) {
            throw new AcessoNegado("os indicadores do cap. 21 são agregados da competência; "
                    + "um ator com recorte de contrato receberia outro número com o mesmo nome");
        }
    }

    private static String papelDe(Ator ator) {
        return ator.papeis().stream().map(Enum::name).sorted()
                .collect(java.util.stream.Collectors.joining("+"));
    }
}
