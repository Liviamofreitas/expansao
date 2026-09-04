package br.com.engesoftware.sgdf.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração de segurança HTTP.
 *
 * <p>Aqui só entra o que é <b>do protocolo</b>: quem está autenticado, sessão,
 * cabeçalhos. A decisão de quem pode o quê está no
 * {@code seguranca.Autorizador}, e é lá que o cap. 15.1 é lido.
 *
 * <p><b>Toda rota exige autenticação.</b> A lista de exceções tem dois itens e
 * nenhum devolve dado: a checagem de saúde e o descritor de erro. Uma lista de
 * rotas públicas que cresce é como um sistema fechado vira aberto sem que
 * ninguém tenha decidido isso.
 */
@Configuration
public class ConfiguracaoDeSeguranca {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(rotas -> rotas
                .requestMatchers("/saude").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            // API sem estado: o token é a sessão. Sem cookie, não há CSRF a
            // proteger — e desligar CSRF numa aplicação COM cookie seria outra
            // conversa.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(origensPermitidas()))
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        res.sendError(HttpStatus.UNAUTHORIZED.value(),
                                "autenticação exigida"))
                .accessDeniedHandler((req, res, ex) ->
                        res.sendError(HttpStatus.FORBIDDEN.value(), "acesso negado")));
        return http.build();
    }

    /**
     * CORS fechado por padrão.
     *
     * <p>A origem do front vem de configuração; sem ela, nenhuma origem é
     * aceita. {@code *} não aparece aqui de propósito: numa API que devolve
     * documento fiscal e dado pessoal, a origem permissiva é uma decisão, não
     * um padrão.
     */
    private static CorsConfigurationSource origensPermitidas() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.List.of());
        config.setAllowedMethods(java.util.List.of("GET", "POST"));
        config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", config);
        return fonte;
    }
}
