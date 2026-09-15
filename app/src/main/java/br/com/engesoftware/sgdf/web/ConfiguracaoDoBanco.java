package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.Agendador;
import br.com.engesoftware.sgdf.persistencia.ConsultaDeAuditoria;
import br.com.engesoftware.sgdf.persistencia.ConsultaDeIndicadores;
import br.com.engesoftware.sgdf.persistencia.ConsultaDeRecertificacao;
import br.com.engesoftware.sgdf.persistencia.RegistroDeAcesso;
import br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeCadastro;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeCiclo;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeExcecao;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeOrganizacao;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeBloqueio;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeParametro;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeTemporalidade;
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
    br.com.engesoftware.sgdf.persistencia.RepositorioDeColeta repositorioDeColeta(Sgdf sgdf) {
        return new br.com.engesoftware.sgdf.persistencia.RepositorioDeColeta(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    br.com.engesoftware.sgdf.persistencia.RepositorioDeRegras repositorioDeRegras(Sgdf sgdf) {
        return new br.com.engesoftware.sgdf.persistencia.RepositorioDeRegras(sgdf);
    }

    // O PIPELINE NASCE AQUI, COM AS REGRAS DAQUELE INSTANTE.
    //
    // As regras vêm do banco a cada varredura — é o que faz "entra valendo na
    // hora" ser verdade sem cache nem invalidação. O custo é ler ~20 linhas ao
    // lado de baixar e escanear arquivos.
    //
    // `new Pipeline(...)` dentro da fábrica, e não um @Bean de Pipeline:
    // `Pipeline` é final e um bean com escopo de requisição precisaria de proxy
    // CGLIB, que não estende classe final.
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    br.com.engesoftware.sgdf.persistencia.VarreduraDeCiclo varreduraDeCiclo(
            Sgdf sgdf,
            br.com.engesoftware.sgdf.persistencia.RepositorioDeRegras regras,
            br.com.engesoftware.sgdf.coleta.PoliticaDeArquivos politica) {
        return new br.com.engesoftware.sgdf.persistencia.VarreduraDeCiclo(
                sgdf, montarPipeline(regras, politica));
    }

    /**
     * A prévia usa O MESMO pipeline da ingestão, montado pela mesma fábrica.
     *
     * <p>Duas construções paralelas divergiriam no dia em que uma delas ganhasse
     * um validador novo — e a simulação passaria a responder sobre uma cadeia
     * que não é a que grava. Aí ela deixa de ser prévia e vira opinião.
     */
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    br.com.engesoftware.sgdf.persistencia.SimulacaoDeVarredura simulacaoDeVarredura(
            Sgdf sgdf,
            br.com.engesoftware.sgdf.persistencia.RepositorioDeRegras regras,
            br.com.engesoftware.sgdf.coleta.PoliticaDeArquivos politica) {
        return new br.com.engesoftware.sgdf.persistencia.SimulacaoDeVarredura(
                sgdf, montarPipeline(regras, politica));
    }

    private br.com.engesoftware.sgdf.pipeline.Pipeline montarPipeline(
            br.com.engesoftware.sgdf.persistencia.RepositorioDeRegras regras,
            br.com.engesoftware.sgdf.coleta.PoliticaDeArquivos politica) {
        return new br.com.engesoftware.sgdf.pipeline.Pipeline(
                new br.com.engesoftware.sgdf.extracao.ExtratorPdfBox(),
                new br.com.engesoftware.sgdf.classificacao.Classificador(regras.ativas()),
                new br.com.engesoftware.sgdf.validacao.ValidacaoDeSeguranca(
                        politica.tamanhoMaximo()));
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeRascunho repositorioDeRascunho(Sgdf sgdf) {
        return new RepositorioDeRascunho(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeExcecao repositorioDeExcecao(Sgdf sgdf) {
        return new RepositorioDeExcecao(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeOrganizacao repositorioDeOrganizacao(Sgdf sgdf) {
        return new RepositorioDeOrganizacao(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeCiclo repositorioDeCiclo(Sgdf sgdf) {
        return new RepositorioDeCiclo(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    ConsultaDeIndicadores consultaDeIndicadores(Sgdf sgdf) {
        return new ConsultaDeIndicadores(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    ConsultaDeAuditoria consultaDeAuditoria(Sgdf sgdf) {
        return new ConsultaDeAuditoria(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeParametro repositorioDeParametro(Sgdf sgdf) {
        return new RepositorioDeParametro(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeTemporalidade repositorioDeTemporalidade(Sgdf sgdf) {
        return new RepositorioDeTemporalidade(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RepositorioDeBloqueio repositorioDeBloqueio(Sgdf sgdf) {
        return new RepositorioDeBloqueio(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    ConsultaDeRecertificacao consultaDeRecertificacao(Sgdf sgdf) {
        return new ConsultaDeRecertificacao(sgdf);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    RegistroDeAcesso registroDeAcesso(Sgdf sgdf) {
        return new RegistroDeAcesso(sgdf);
    }

    /**
     * O nome da instância vem do ambiente.
     *
     * <p>Com duas réplicas, é o que permite ver qual delas parou. Ausente, cai
     * para o hostname — que é melhor que uma constante igual nas duas.
     */
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    Agendador agendador(Sgdf sgdf) {
        String instancia = System.getenv("SGDF_INSTANCIA");
        if (instancia == null || instancia.isBlank()) {
            instancia = System.getenv().getOrDefault("HOSTNAME", "desconhecida");
        }
        return new Agendador(sgdf, instancia);
    }
}
