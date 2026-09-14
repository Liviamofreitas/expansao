package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeTemporalidade;
import br.com.engesoftware.sgdf.retencao.Temporalidade;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O estado da retenção — a evidência que o DPO precisa ler (A08 / LGPD-02).
 *
 * <p><b>Somente leitura, e a ausência de um endpoint de aprovação é a decisão
 * mais importante desta classe.</b> Aprovar uma temporalidade é autorizar a
 * destruição definitiva de prova fiscal e de dado pessoal de ±890 pessoas. O
 * cap. 15.1 não nomeia papel para isso, e a única permissão técnica que caberia
 * — {@code CONFIGURAR_SISTEMA} — é da TI. Expor a aprovação atrás dela poria a
 * área que opera o sistema decidindo por quanto tempo se guarda dado pessoal,
 * que é exatamente a inversão de governança que a ISO/IEC 27001 e a LGPD pedem
 * para não acontecer. A aprovação é ato de cadastro, registrado, feito por quem
 * a norma interna (pendência A11) nomear. Ver PENDENCIAS, A08.
 *
 * <p>Lê-se com {@code AUDITAR}: é relatório de conformidade, não operação.
 */
@RestController
@RequestMapping("/api")
public class RetencaoController {

    private final AtorDaRequisicao atores;
    private final RepositorioDeTemporalidade temporalidades;

    public RetencaoController(AtorDaRequisicao atores,
                              RepositorioDeTemporalidade temporalidades) {
        this.atores = atores;
        this.temporalidades = temporalidades;
    }

    /**
     * Cada classe, o seu impedimento e a sua última execução.
     *
     * <p>O campo {@code impedimento} vem preenchido para toda classe que existe e
     * não está sendo cumprida — hoje, todas as quatro. <b>É ele, e não a
     * contagem de itens, que responde se a política está viva:</b> um relatório
     * que mostrasse só "0 itens eliminados" leria como "nada precisava ser
     * eliminado", que é a conclusão errada e confortável.
     */
    @GetMapping("/retencao")
    public Map<String, Object> situacao() {
        exigir(Permissao.AUDITAR);

        Map<Temporalidade.Alvo, RepositorioDeTemporalidade.UltimoExpurgo> ultimos =
                new LinkedHashMap<>();
        for (RepositorioDeTemporalidade.UltimoExpurgo u : temporalidades.ultimoPorAlvo()) {
            ultimos.put(u.alvo(), u);
        }

        List<Map<String, Object>> classes = new ArrayList<>();
        int naoCumpridas = 0;
        for (Temporalidade t : temporalidades.todas()) {
            String impedimento = t.impedimento();
            if (impedimento != null) {
                naoCumpridas++;
            }
            RepositorioDeTemporalidade.UltimoExpurgo u = ultimos.get(t.alvo());
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("classe", t.classe());
            linha.put("alvo", t.alvo() == null ? null : t.alvo().name());
            linha.put("marco", t.marco() == null ? null : t.marco().name());
            linha.put("prazo_meses", t.prazoMeses());
            linha.put("acao", t.acao() == null ? null : t.acao().name());
            linha.put("fundamento", t.fundamento());
            linha.put("aprovada", t.aprovada());
            linha.put("aprovado_em", t.aprovadoEm() == null ? null : t.aprovadoEm().toString());
            linha.put("aprovado_por", t.aprovadoPor());
            linha.put("impedimento", impedimento);
            linha.put("nunca_executou", u == null || u.nuncaExecutou());
            linha.put("ultima_execucao",
                    u == null || u.executadoEm() == null ? null : u.executadoEm().toString());
            linha.put("ultimo_corte",
                    u == null || u.corte() == null ? null : u.corte().toString());
            linha.put("ultimos_itens", u == null ? null : u.itens());
            classes.add(linha);
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("classes", classes);
        resposta.put("nao_cumpridas", naoCumpridas);
        // A RESSALVA VAI NA RESPOSTA, NÃO NO MANUAL. É a mesma decisão do
        // relatório de recertificação (RA-16): quem lê isto está prestes a
        // concluir alguma coisa sobre conformidade, e o limite do que o
        // documento prova tem de estar na primeira linha que ele lê.
        resposta.put("ressalva", naoCumpridas == 0
                ? "Todas as classes declaradas estão aprovadas e sendo aplicadas. Esta "
                + "lista cobre apenas as classes DECLARADAS: dado que não tem classe "
                + "não tem prazo, e não é apagado por ninguém."
                : naoCumpridas + " classe(s) de temporalidade existem e NÃO estão sendo "
                + "cumpridas (pendência A08). Enquanto isso durar, o SGDF retém dado "
                + "pessoal sem termo final definido — art. 6º, III e art. 16 da LGPD. "
                + "Aprovar cada classe é ato de cadastro do jurídico/DPO, não deploy.");
        return resposta;
    }

    private Ator exigir(Permissao permissao) {
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao);
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }
}
