package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.ciclo.EstadoDoCiclo;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeCiclo;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Papel;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O ciclo pelo cap. 6.2 — histórias F3-03 e F3-04.
 *
 * <p><b>Duas permissões diferentes, e a diferença é do capítulo.</b> O ateste é
 * ato do GESTOR_CONTRATO (cap. 15.1: <i>"registrar ateste; acompanhar seu
 * ciclo"</i>) e usa {@code REGISTRAR_ATESTE}; conduzir o ciclo e registrar a NF
 * usam {@code CONDUZIR_CICLO}, que o capítulo não nomeia e cuja atribuição está
 * declarada como leitura em PENDENCIAS. Dar uma permissão só às duas coisas
 * faria o gestor do contrato mover o ciclo para FECHADO — que não é o que o
 * capítulo lhe dá.
 *
 * <p>O contrato do ciclo entra no alvo em <b>toda</b> operação, derivado do
 * próprio ciclo e nunca recebido por parâmetro. O painel já ensinou por quê
 * (achados § 30.1): um contrato que chega de fora é um recorte que o cliente
 * escolhe, e um recorte que o cliente escolhe não é restrição.
 */
@RestController
@RequestMapping("/api")
public class CicloController {

    private final AtorDaRequisicao atores;
    private final RepositorioDeCiclo ciclos;
    private final br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel painel;

    public CicloController(AtorDaRequisicao atores, RepositorioDeCiclo ciclos,
                           br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel painel) {
        this.atores = atores;
        this.ciclos = ciclos;
        this.painel = painel;
    }

    /** O estado e para onde ele pode ir — a tela oferece só o que existe. */
    @GetMapping("/ciclos/{cicloId}/estado")
    public Map<String, Object> estado(@PathVariable UUID cicloId) {
        exigir(Permissao.VER_PAINEL, cicloId);
        EstadoDoCiclo atual = ciclos.estadoDe(cicloId);
        return Map.of("estado", atual.name(),
                "destinos", atual.destinos().stream().map(Enum::name).sorted().toList(),
                "bloqueantes_em_aberto", ciclos.bloqueantesEmAberto(cicloId));
    }

    /**
     * Move o ciclo. F3-04: com bloqueante em aberto, não move.
     *
     * <p>{@code PATCH} seria o verbo REST natural e não é o certo aqui: mover o
     * ciclo não é editar um campo, é um ato com motivo que entra na trilha. O
     * cap. 13 escreve as ações de ciclo como POST em sub-recurso pela mesma
     * razão.
     */
    @PostMapping("/ciclos/{cicloId}/transicao")
    public RepositorioDeCiclo.Transicao mover(@PathVariable UUID cicloId,
                                              @RequestBody Movimento corpo) {
        Ator ator = exigir(Permissao.CONDUZIR_CICLO, cicloId);
        EstadoDoCiclo destino = EstadoDoCiclo.de(corpo.destino());
        if (destino == null) {
            throw new IllegalArgumentException("destino desconhecido: " + corpo.destino()
                    + "; o cap. 6.2 define " + List.of(EstadoDoCiclo.values()));
        }
        return ciclos.mover(cicloId, destino, ator.identificador(), papelDe(ator),
                corpo.motivo());
    }

    /** Cap. 13: {@code POST /ciclos/{id}/ateste} — marco do D+3, papel gestor. */
    @PostMapping("/ciclos/{cicloId}/ateste")
    public Map<String, String> ateste(@PathVariable UUID cicloId, @RequestBody Ateste corpo) {
        Ator ator = exigir(Permissao.REGISTRAR_ATESTE, cicloId);
        ciclos.registrarAteste(cicloId, corpo.quando(), corpo.forma(), corpo.documentoId(),
                ator.identificador(), papelDe(ator));
        return Map.of("registrado", "ateste");
    }

    /** O outro extremo do D+3. Registrar não move o ciclo: são dois atos. */
    @PostMapping("/ciclos/{cicloId}/nota-fiscal")
    public Map<String, String> notaFiscal(@PathVariable UUID cicloId,
                                          @RequestBody NotaFiscal corpo) {
        Ator ator = exigir(Permissao.CONDUZIR_CICLO, cicloId);
        ciclos.registrarNotaFiscal(cicloId, corpo.quando(), ator.identificador(), papelDe(ator));
        return Map.of("registrado", "nota_fiscal");
    }

    /**
     * O indicador do cap. 21, por competência — critério de aceite da F3-03.
     *
     * <p>Agregado sobre a competência inteira: não há alvo de contrato, e por
     * isso quem não enxerga todos os contratos não recebe esta visão. Filtrar o
     * agregado pelos contratos do ator daria um percentual diferente do da
     * política com o mesmo nome — dois números chamados "D+3" é pior que um só.
     */
    @GetMapping("/indicadores/d3")
    public Map<String, Object> d3(@RequestParam String competencia) {
        Ator ator = atores.atual();
        exigirGlobal(ator, Permissao.VER_PAINEL);
        RepositorioDeCiclo.IndicadorD3 i = ciclos.indicadorD3(competencia);
        return Map.of("competencia", i.competencia(),
                "faturados", i.faturados(),
                "dentro_do_prazo", i.dentroDoPrazo(),
                "percentual", String.valueOf(i.percentual()),
                "media_em_dias", String.valueOf(i.mediaEmDias()),
                "atinge_a_meta", i.atingeAMeta(),
                "por_contrato", ciclos.tempoPorContrato(competencia));
    }

    // -------------------------------------------------------------------------

    /** Autoriza com o contrato DO CICLO no alvo — nunca um recebido do cliente. */
    private Ator exigir(Permissao permissao, UUID cicloId) {
        UUID contrato = painel.contratoDoCiclo(cicloId);
        if (contrato == null) {
            throw new AcessoNegado("ciclo " + cicloId + " não existe");
        }
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao,
                Autorizador.Alvo.doContrato(contrato));
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }

    private void exigirGlobal(Ator ator, Permissao permissao) {
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        if (ator != null && !ator.enxergaTodosOsContratos()) {
            throw new AcessoNegado("o indicador do cap. 21 é agregado da competência; "
                    + "um ator com recorte de contrato receberia outro número com o mesmo nome");
        }
    }

    private static String papelDe(Ator ator) {
        return ator.papeis().stream().map(Papel::name).sorted().collect(Collectors.joining("+"));
    }

    /** @param motivo obrigatório: o cap. 6.2 exige ator E motivo na trilha */
    public record Movimento(String destino, String motivo) {}

    /** @param forma como o cliente atestou — e-mail, portal, ofício */
    public record Ateste(OffsetDateTime quando, String forma, UUID documentoId) {}

    public record NotaFiscal(OffsetDateTime quando) {}
}
