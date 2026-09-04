package br.com.engesoftware.sgdf.conciliacao;

import br.com.engesoftware.sgdf.validacao.ResultadoDeValidacao;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeFormatos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R04 — nenhuma exigência do ciclo tem formato pendente, e os formatos do mesmo
 * tipo são da mesma competência.
 *
 * <p>Cap. 9: <i>"formatos_pendentes = ∅ e competência igual entre formatos do
 * mesmo tipo"</i>.
 *
 * <p><b>É V6 elevada ao ciclo</b>, e a diferença de altitude muda o que a
 * resposta serve para fazer. V6 diz que <i>esta</i> exigência está parcial; R04
 * diz que <i>o ciclo</i> não pode ser publicado, e quantas exigências faltam.
 * Quem lê a primeira é quem entrega o documento; quem lê a segunda é quem
 * decide fechar a competência.
 */
public final class R04CompletudeDeFormatos implements RegraDeConciliacao {

    /**
     * Uma exigência do ciclo, do ponto de vista dos formatos.
     *
     * @param tipo                  tipo documental
     * @param formatosExigidos      o que o cadastro pede
     * @param competenciaPorFormato o que chegou
     * @param bloqueante            se a falta desta exigência impede publicar
     */
    public record ExigenciaDoCiclo(String tipo, List<String> formatosExigidos,
                                   Map<String, String> competenciaPorFormato,
                                   boolean bloqueante) {
    }

    private final List<ExigenciaDoCiclo> exigencias;
    private final ResultadoDaConciliacao.Modo modoCadastrado;

    public R04CompletudeDeFormatos(List<ExigenciaDoCiclo> exigencias,
                                   ResultadoDaConciliacao.Modo modoCadastrado) {
        this.exigencias = List.copyOf(exigencias);
        this.modoCadastrado = modoCadastrado;
    }

    @Override
    public String codigo() {
        return "R04";
    }

    @Override
    public ResultadoDaConciliacao executar(DadosDoCiclo ciclo, Tolerancia tolerancia) {
        ResultadoDaConciliacao.Modo modo = RegraDeConciliacao.modoEfetivo(modoCadastrado, tolerancia);

        if (exigencias.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.NAO_APLICAVEL,
                    modo, null, null, null,
                    "o ciclo não tem exigência nenhuma materializada: R04 não tem o que conferir",
                    Map.of());
        }

        ValidacaoDeFormatos v6 = new ValidacaoDeFormatos();
        List<String> pendentesBloqueantes = new ArrayList<>();
        List<String> pendentesNaoBloqueantes = new ArrayList<>();
        List<String> incoerentes = new ArrayList<>();

        for (ExigenciaDoCiclo e : exigencias) {
            ResultadoDeValidacao r =
                    v6.validar(e.formatosExigidos(), e.competenciaPorFormato());
            if (!r.reprovado()) {
                continue;
            }
            if (r.detalhe().containsKey("competencias_divergentes")) {
                incoerentes.add(e.tipo() + ": "
                        + String.join(" e ", r.detalhe().get("competencias_divergentes")));
            } else {
                String falta = e.tipo() + ": faltam "
                        + String.join(", ", r.detalhe().getOrDefault("formatos_pendentes",
                                List.of()));
                if (e.bloqueante()) {
                    pendentesBloqueantes.add(falta);
                } else {
                    pendentesNaoBloqueantes.add(falta);
                }
            }
        }

        Map<String, List<String>> itens = new LinkedHashMap<>();
        itens.put("exigencias", List.of(exigencias.size() + " no ciclo"));
        if (!pendentesBloqueantes.isEmpty()) {
            itens.put("bloqueantes_pendentes", List.copyOf(pendentesBloqueantes));
        }
        if (!pendentesNaoBloqueantes.isEmpty()) {
            itens.put("nao_bloqueantes_pendentes", List.copyOf(pendentesNaoBloqueantes));
        }
        if (!incoerentes.isEmpty()) {
            itens.put("competencias_incoerentes", List.copyOf(incoerentes));
        }

        if (pendentesBloqueantes.isEmpty() && pendentesNaoBloqueantes.isEmpty()
                && incoerentes.isEmpty()) {
            return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.CONFORME,
                    modo, null, null, null, null, itens);
        }

        List<String> tudo = new ArrayList<>();
        if (!pendentesBloqueantes.isEmpty()) {
            tudo.add(pendentesBloqueantes.size() + " exigência(s) BLOQUEANTE(S) incompleta(s): "
                    + String.join("; ", pendentesBloqueantes));
        }
        if (!pendentesNaoBloqueantes.isEmpty()) {
            tudo.add(pendentesNaoBloqueantes.size() + " não bloqueante(s): "
                    + String.join("; ", pendentesNaoBloqueantes));
        }
        if (!incoerentes.isEmpty()) {
            tudo.add("formatos de competências diferentes em: " + String.join("; ", incoerentes));
        }
        return new ResultadoDaConciliacao(codigo(), ResultadoDaConciliacao.Situacao.DIVERGENTE,
                modo, null, null, null, String.join(". ", tudo), itens);
    }
}
