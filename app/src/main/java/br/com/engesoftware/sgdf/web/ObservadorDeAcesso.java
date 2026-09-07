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
 * está no {@link RegistroDeAcesso}, que é exercitado direto contra o banco nos
 * testes. Aqui sobram cinco linhas de adaptação ao Spring, que a suíte não
 * cobre: os testes de fronteira chamam os controladores diretamente, sem
 * passar por interceptor. Fica dito em vez de suposto — registrado como RA-15.
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
        if (resposta.getStatus() >= 400) {
            return;
        }
        Ator ator = atores.atual();
        if (ator != null) {
            registro.observar(ator);
        }
    }
}
