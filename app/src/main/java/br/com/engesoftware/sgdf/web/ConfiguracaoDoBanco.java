package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeCadastro;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeRascunho;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeTriagem;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.WebApplicationContext;

/**
 * Liga o {@code DataSource} do Spring aos repositórios em JDBC direto.
 *
 * <p>Uma conexão por requisição, devolvida ao pool no fim. É o mínimo que faz o
 * ADR-002 conviver com o framework: o Spring cuida do pool, os repositórios
 * cuidam do SQL.
 */
@Configuration
public class ConfiguracaoDoBanco {

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    Sgdf sgdf(DataSource dataSource) throws SQLException {
        return new Sgdf(dataSource.getConnection());
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    ConsultaDoPainel consultaDoPainel(Sgdf sgdf) {
        return new ConsultaDoPainel(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeTriagem repositorioDeTriagem(Sgdf sgdf) {
        return new RepositorioDeTriagem(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeCadastro repositorioDeCadastro(Sgdf sgdf) {
        return new RepositorioDeCadastro(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeRascunho repositorioDeRascunho(Sgdf sgdf) {
        return new RepositorioDeRascunho(sgdf);
    }
}
