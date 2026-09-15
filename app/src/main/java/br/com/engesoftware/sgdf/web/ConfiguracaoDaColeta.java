package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.classificacao.CargaDeRegras;
import br.com.engesoftware.sgdf.classificacao.Classificador;
import br.com.engesoftware.sgdf.coleta.Antivirus;
import br.com.engesoftware.sgdf.coleta.AntivirusClamd;
import br.com.engesoftware.sgdf.coleta.ClienteWebDav;
import br.com.engesoftware.sgdf.coleta.PoliticaDeArquivos;
import br.com.engesoftware.sgdf.coleta.Varredura;
import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.pipeline.Pipeline;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeSeguranca;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga a coleta ao mundo — o que faltava para o sistema ingerir um documento.
 *
 * <p><b>Todas as peças existiam e nenhuma tinha quem as construísse.</b>
 * {@code ClienteWebDav}, {@code Varredura}, {@code Pipeline} e
 * {@code VarreduraDeCiclo} estavam escritos, testados e sem um único
 * {@code new} fora dos testes. O sistema subia, abria ciclos, materializava
 * exigências, mostrava painel — e não conseguia ler um arquivo.
 *
 * <p><b>Tudo aqui é singleton, e é seguro.</b> Nenhuma destas peças guarda
 * estado de requisição: o cliente WebDAV guarda credencial e timeout, a
 * varredura guarda política e limites, o pipeline guarda as regras carregadas.
 * O que é por requisição — a conexão com o banco — mora no {@code Sgdf}, que
 * segue no {@code ConfiguracaoDoBanco} com escopo de requisição.
 */
@Configuration
public class ConfiguracaoDaColeta {

    /**
     * O cliente do repositório de documentos, ou <b>nenhum</b>.
     *
     * <p>Sem {@code SGDF_WEBDAV_BASE} este bean é {@code null} e a aplicação
     * SOBE ASSIM MESMO. É deliberado: HML e a suíte de banco não têm OwnCloud,
     * e recusar a subida por causa disso trocaria "a varredura não está
     * configurada" por "o sistema não liga" — o segundo sintoma é muito pior e
     * aponta para o lugar errado.
     *
     * <p>Quem paga o preço dessa escolha é o {@code VarreduraController}, que
     * recusa explicitamente dizendo QUAL variável falta. Um endpoint que
     * devolve "0 documentos" porque não há repositório configurado seria a
     * falha que se parece com sucesso — e já custou caro neste projeto.
     *
     * <p><b>A base carrega o caminho-raiz do WebDAV, não só o endereço</b>
     * (ADR-005). Numa OwnCloud, {@code
     * https://cloud.exemplo/remote.php/dav/files/svc-sgdf-leitura} — e o
     * {@code pasta_origem} do contrato é relativo a isso. É o que faz a troca
     * de nuvem ser uma variável de ambiente em vez de um UPDATE em todo caminho
     * já registrado.
     */
    @Bean
    ClienteWebDav clienteWebDav(
            @Value("${SGDF_WEBDAV_BASE:}") String base,
            @Value("${SGDF_WEBDAV_USUARIO:}") String usuario,
            @Value("${SGDF_WEBDAV_SENHA:}") String senha,
            @Value("${SGDF_WEBDAV_TIMEOUT_SEGUNDOS:30}") int timeoutSegundos) {
        if (base == null || base.isBlank()) {
            return null;
        }
        return new ClienteWebDav(URI.create(base), usuario, senha,
                Duration.ofSeconds(timeoutSegundos));
    }

    /**
     * O antivírus do SEC-05.
     *
     * <p>Sem clamd configurado devolve um que se declara <b>indisponível</b>, e
     * não um que aprova. A própria {@code VeredictoAntivirus} escreve a razão:
     * "antivírus fora do ar não pode ser lido como arquivo limpo" — a validação
     * V1 do cap. 8.4 exige antivírus limpo, e tratar indisponibilidade como
     * aprovação inverteria o sentido do controle. O documento segue com a marca
     * de não verificado e a trilha registra.
     */
    @Bean
    Antivirus antivirus(@Value("${SGDF_CLAMD_HOST:}") String host,
                        @Value("${SGDF_CLAMD_PORTA:3310}") int porta,
                        @Value("${SGDF_CLAMD_TIMEOUT_MS:30000}") int timeoutMs) {
        if (host == null || host.isBlank()) {
            return (rotulo, conteudo) -> VeredictoAntivirus.indisponivel(
                    "clamd não configurado (SGDF_CLAMD_HOST)");
        }
        return new AntivirusClamd(host, porta, timeoutMs);
    }

    // O PIPELINE SAIU DAQUI, E O MOTIVO É O REQUISITO "ENTRA VALENDO NA HORA".
    //
    // Ele era singleton construído com `CargaDeRegras.todas()` — regras
    // compiladas. Com o cadastro de tipo pela aplicação (ADR-004), um pipeline
    // criado na partida continuaria usando as regras de quando o processo
    // subiu: cadastrar um documento novo só passaria a valer no próximo
    // restart, e ninguém ligaria uma coisa à outra.
    //
    // Agora ele nasce por requisição, em ConfiguracaoDoBanco.varreduraDeCiclo,
    // com as regras lidas do banco naquele instante. Não virou bean com escopo
    // de requisição por um motivo concreto: `Pipeline` é `public final class`, e
    // proxy CGLIB não estende classe final — foi o defeito que derrubou a
    // aplicação em dezesseis classes nesta mesma sessão. Construir dentro da
    // fábrica evita o proxy inteiro.

    @Bean
    PoliticaDeArquivos politicaDeArquivos() {
        return PoliticaDeArquivos.padrao();
    }

    /**
     * A varredura, com limites explícitos.
     *
     * <p><b>Os limites não são precaução, são o que torna a varredura
     * chamável por HTTP.</b> Ela baixa e escaneia arquivo por arquivo; sem teto
     * de profundidade, de quantidade e de duração, uma pasta grande viraria uma
     * requisição que não termina — e o cliente desistiria sem saber se o
     * trabalho continuou do outro lado.
     *
     * <p>Os três são configuráveis porque o tamanho certo depende do contrato e
     * do repositório, e ninguém sabe qual é antes do primeiro ciclo real.
     */
    // `ObjectProvider` E NÃO `ClienteWebDav` DIRETO — medido subindo.
    //
    // Um @Bean que devolve null NÃO REGISTRA o bean, e o método seguinte que o
    // pede como parâmetro comum derruba o contexto inteiro na partida:
    // "Parameter 0 of method varredura required a bean of type ClienteWebDav
    // that could not be found". A aplicação deixaria de subir por FALTA DE
    // CONFIGURAÇÃO OPCIONAL, que é exatamente o que este desenho quer evitar.
    //
    // ObjectProvider pergunta sem exigir, e não depende da ordem em que o
    // Spring resolve os beans — ao contrário de @ConditionalOnBean, que aqui
    // seria uma armadilha de ordenação.
    @Bean
    Varredura varredura(ObjectProvider<ClienteWebDav> clienteWebDav, Antivirus antivirus,
                        PoliticaDeArquivos politica,
                        @Value("${SGDF_VARREDURA_PROFUNDIDADE:4}") int profundidade,
                        @Value("${SGDF_VARREDURA_MAX_ARQUIVOS:500}") int maximo,
                        @Value("${SGDF_VARREDURA_DURACAO_MINUTOS:10}") int minutos) {
        ClienteWebDav webdav = clienteWebDav.getIfAvailable();
        if (webdav == null) {
            return null;
        }
        return new Varredura(webdav, antivirus, politica,
                new Varredura.Limites(profundidade, maximo, Duration.ofMinutes(minutos)));
    }
}
