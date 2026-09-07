package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.notificacao.Ativacao;
import br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeParametro;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Papel;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ativação de notificações por contrato — história F3-02.
 *
 * <p><b>Ativar exige CONFIGURAR_SISTEMA e desativar não exige nada além de ver
 * o painel</b>, e a assimetria é deliberada. Ligar a cobrança faz o sistema
 * começar a mandar e-mail para pessoas; desligar apenas devolve o contrato ao
 * modo sombra, que é o estado seguro. Exigir o mesmo papel para as duas pontas
 * criaria a situação em que quem percebe o problema não pode pará-lo — e a
 * primeira coisa que se quer numa emergência é a porta de saída aberta.
 *
 * <p>Pela mesma razão o desligamento não pede motivo: atrito na direção segura
 * não protege ninguém.
 */
@RestController
@RequestMapping("/api")
public class NotificacaoController {

    private final AtorDaRequisicao atores;
    private final RepositorioDeParametro parametros;
    private final ConsultaDoPainel painel;

    public NotificacaoController(AtorDaRequisicao atores, RepositorioDeParametro parametros,
                                 ConsultaDoPainel painel) {
        this.atores = atores;
        this.parametros = parametros;
        this.painel = painel;
    }

    /** A tela da F3-02: cada contrato e a origem da sua situação. */
    @GetMapping("/notificacao/contratos")
    public List<Map<String, Object>> contratos() {
        exigir(Permissao.VER_PAINEL);
        return parametros.situacaoDosContratos().stream().map(s -> Map.<String, Object>of(
                "contrato_id", s.contratoId(),
                "numero", s.numero(),
                "cliente", s.cliente(),
                "envio_ativo", s.ativacao().envioAtivo(),
                "origem", s.ativacao().origem().name(),
                "motivo", s.ativacao().motivo())).toList();
    }

    @PostMapping("/notificacao/contratos/{contratoId}/ativar")
    public Map<String, Object> ativar(@PathVariable UUID contratoId,
                                      @RequestBody Decisao corpo) {
        Ator ator = exigir(Permissao.CONFIGURAR_SISTEMA);
        parametros.ativarEnvio(contratoId, corpo.motivo(), ator.identificador(), papelDe(ator));
        return situacao(contratoId);
    }

    @PostMapping("/notificacao/contratos/{contratoId}/desativar")
    public Map<String, Object> desativar(@PathVariable UUID contratoId) {
        Ator ator = exigir(Permissao.VER_PAINEL);
        parametros.desativarEnvio(contratoId, ator.identificador(), papelDe(ator));
        return situacao(contratoId);
    }

    // -------------------------------------------------------------------------

    private Map<String, Object> situacao(UUID contratoId) {
        Ativacao a = parametros.ativacaoDe(contratoId);
        return Map.of("envio_ativo", a.envioAtivo(), "origem", a.origem().name(),
                "motivo", a.motivo());
    }

    private Ator exigir(Permissao permissao) {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }

    private static String papelDe(Ator ator) {
        return ator.papeis().stream().map(Papel::name).sorted().collect(Collectors.joining("+"));
    }

    /** @param motivo obrigatório ao ativar: alguém vai perguntar quem autorizou */
    public record Decisao(String motivo) {}
}
