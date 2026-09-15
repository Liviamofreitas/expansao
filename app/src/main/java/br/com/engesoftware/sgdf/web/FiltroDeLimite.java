package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.limite.JanelaDeslizante;
import br.com.engesoftware.sgdf.limite.PoliticaDeUso;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeBloqueio;
import br.com.engesoftware.sgdf.seguranca.Ator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aplica os limites do SEC-06 na fronteira.
 *
 * <p><b>Depois da autenticação, e é por isso que ele é um filtro do Spring
 * Security e não um interceptor.</b> Antes dela não há ator, e limitar por IP
 * num sistema atrás de proxy corporativo barraria o escritório inteiro por causa
 * de uma pessoa (ver {@code PoliticaDeUso}).
 *
 * <p><b>O mapa em memória cresce, e isso é tratado.</b> Uma entrada por ator que
 * passou por aqui vazaria devagar num processo longo. A limpeza acontece na
 * própria passagem, e não por tarefa de fundo: uma faxina agendada é mais um job
 * cuja morte é silenciosa (achados § 46), e aqui o gatilho natural já existe —
 * alguém está pedindo alguma coisa.
 */
@Component
@Order(1)
public class FiltroDeLimite extends OncePerRequestFilter {

    /** Acima disto, limpa o que expirou antes de atender. */
    static final int ATORES_ANTES_DA_FAXINA = 1_000;

    private final AtorDaRequisicao atores;
    private final ObjectProvider<RepositorioDeBloqueio> bloqueios;
    private final Map<String, Contadores> porAtor = new ConcurrentHashMap<>();

    public FiltroDeLimite(AtorDaRequisicao atores,
                          ObjectProvider<RepositorioDeBloqueio> bloqueios) {
        this.atores = atores;
        this.bloqueios = bloqueios;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        // A saúde do serviço fica de fora: se o orquestrador for barrado pelo
        // limite, ele mata o pod — e o sistema se derruba sozinho exatamente
        // quando está sob carga.
        String caminho = requisicao.getRequestURI();
        // O prefixo "/actuator" saiu porque não existe mais: o actuator mora
        // em "/saude" (management.endpoints.web.base-path). Uma isenção para um
        // caminho que não existe é uma lista de rotas públicas que mente sobre
        // o que isenta — e esconde que a rota real nunca esteve isenta.
        return caminho.equals("/saude/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao,
                                    HttpServletResponse resposta, FilterChain corrente)
            throws ServletException, IOException {
        Ator ator = atores.atual();
        if (ator == null) {
            corrente.doFilter(requisicao, resposta);
            return;
        }
        String id = ator.identificador();
        Instant agora = Instant.now();

        RepositorioDeBloqueio repo = bloqueios.getIfAvailable();
        if (repo != null) {
            var bloqueio = repo.ativo(id);
            if (bloqueio.isPresent()) {
                recusar(resposta, 403, bloqueio.get().segundosRestantes(OffsetDateTime.now()),
                        "acesso temporariamente bloqueado por tentativas repetidas");
                return;
            }
        }

        Contadores c = porAtor.computeIfAbsent(id, x -> new Contadores());
        boolean escrita = PoliticaDeUso.escrita(requisicao.getMethod());
        synchronized (c) {
            c.ultimoUso = agora;
            JanelaDeslizante janela = escrita ? c.escritas : c.pedidos;
            if (!janela.cabe(agora)) {
                // 429 e não 403: o pedido é legítimo e a pessoa tem direito —
                // o que não cabe é o ritmo. Devolver 403 mandaria procurar
                // problema de permissão onde há problema de volume.
                recusar(resposta, 429, janela.esperaAte(agora).getSeconds(),
                        escrita ? "muitas escritas em pouco tempo"
                                : "muitos pedidos em pouco tempo");
                return;
            }
            // Escrita consome os dois: sem isto, 30 escritas por minuto somadas
            // a 120 leituras dariam 150 pedidos num teto declarado de 120.
            if (escrita) {
                c.pedidos.cabe(agora);
            }
        }

        if (porAtor.size() > ATORES_ANTES_DA_FAXINA) {
            limpar(agora);
        }
        corrente.doFilter(requisicao, resposta);
    }

    private static void recusar(HttpServletResponse resposta, int status, long esperaSegundos,
                                String motivo) throws IOException {
        resposta.setStatus(status);
        resposta.setHeader("Retry-After", String.valueOf(Math.max(1, esperaSegundos)));
        resposta.setContentType("application/json;charset=UTF-8");
        // O motivo é genérico de propósito: dizer "você excedeu 120 por minuto"
        // entrega o limite a quem está medindo onde ele fica.
        resposta.getWriter().write("{\"motivo\":\"" + motivo + "\"}");
    }

    private void limpar(Instant agora) {
        Instant corte = agora.minus(Duration.ofMinutes(10));
        porAtor.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().ultimoUso.isBefore(corte);
            }
        });
    }

    /** Duas janelas por ator: o volume total e o subconjunto de escrita. */
    static final class Contadores {
        final JanelaDeslizante pedidos = new JanelaDeslizante(Duration.ofMinutes(1),
                PoliticaDeUso.PEDIDOS_POR_MINUTO);
        final JanelaDeslizante escritas = new JanelaDeslizante(Duration.ofMinutes(1),
                PoliticaDeUso.ESCRITAS_POR_MINUTO);
        Instant ultimoUso = Instant.now();
    }
}
