package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.RegistroDeAcesso;
import br.com.engesoftware.sgdf.seguranca.Ator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra a concessão de quem entrou — SEC-10, história F3-06.
 *
 * <p>É a única forma de o papel AUDITORIA aparecer na própria recertificação: a
 * trilha registra quem escreve, e ele não escreve nada por definição do
 * cap. 15.1. Ver a V017.
 *
 * <p><b>Esta classe é cola, de propósito.</b> Toda a decisão — o que vale como
 * observação, a ordenação dos arrays, o que fazer quando a gravação falha —
 * está no {@link RegistroDeAcesso}. Aqui sobram as linhas de adaptação ao
 * Spring, que a RA-15 registrava como não exercitadas pela suíte: os testes de
 * fronteira chamam os controladores diretamente, sem passar por interceptor.
 *
 * <p><b>Agora elas são exercitadas</b> — {@code TestesDeRecertificacao} chama
 * {@link #afterCompletion} com requisição e resposta postiças, e verifica as
 * quatro decisões que vivem aqui: o 4xx não vira acesso observado, o ator nulo
 * não vira linha, o caminho feliz grava, e <b>uma falha de gravação não sobe</b>.
 * O último é o que justifica a classe inteira ser tão pequena: era a única
 * promessa que ninguém media.
 */
@Configuration
public class ObservadorDeAcesso implements WebMvcConfigurer, HandlerInterceptor {

    private final AtorDaRequisicao atores;
    private final RegistroDeAcesso registro;

    public ObservadorDeAcesso(AtorDaRequisicao atores, RegistroDeAcesso registro) {
        this.atores = atores;
        this.registro = registro;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(this).addPathPatterns("/api/**");
    }

    /**
     * Depois de responder, e não antes.
     *
     * <p>Em {@code preHandle} a gravação entraria antes de a requisição ser
     * autorizada, e um 403 deixaria registrado um "acesso observado" de quem foi
     * barrado. A recertificação passaria a listar tentativas como concessões —
     * que é o oposto do que ela mede.
     */
    @Override
    public void afterCompletion(HttpServletRequest requisicao, HttpServletResponse resposta,
                                Object manipulador, Exception excecao) {
        try {
            if (resposta.getStatus() >= 400) {
                return;
            }
            Ator ator = atores.atual();
            if (ator == null) {
                return;
            }
            registro.observar(ator);
        } catch (RuntimeException e) {
            // A ÚLTIMA REDE, E ELA TEM DE EXISTIR AQUI TAMBÉM.
            //
            // O RegistroDeAcesso já não deixa escapar nada, mas as duas linhas
            // antes dele podem: ler o status de uma resposta já reciclada pelo
            // contêiner, ou resolver o ator quando o contexto de segurança já
            // saiu da thread. Deixar subir daqui devolveria erro a quem fez uma
            // requisição que DEU CERTO — a recertificação derrubando o produto
            // que ela existe para revisar, que é exatamente o que a nota do
            // RegistroDeAcesso promete não acontecer.
            //
            // Silencioso não é: o contador de RegistroDeAcesso.Falhas não vê
            // esta exceção, então ela é contada aqui.
            RegistroDeAcesso.Falhas.contarFalhaDeCola(e);
        }
    }
}
