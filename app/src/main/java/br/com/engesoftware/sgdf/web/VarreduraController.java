package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.coleta.ResultadoVarredura;
import br.com.engesoftware.sgdf.coleta.Varredura;
import br.com.engesoftware.sgdf.persistencia.ConsultaDoPainel;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeColeta;
import br.com.engesoftware.sgdf.persistencia.SimulacaoDeVarredura;
import br.com.engesoftware.sgdf.persistencia.VarreduraDeCiclo;
import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Autorizador;
import br.com.engesoftware.sgdf.seguranca.Permissao;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * O gatilho da varredura — cap. 8.1, e o que faltava para o sistema ler algo.
 *
 * <p><b>Sob demanda, e não agendado.</b> A RA-07 registra que os gatilhos
 * automáticos continuam parados na F3-01: pôr o sistema a cobrar áreas antes de
 * a divergência estar medida é o risco P01 do cap. 22. Isso vale para a RÉGUA
 * DE NOTIFICAÇÃO, que cobra gente. A varredura não cobra ninguém — ela lê uma
 * pasta e registra o que encontrou. Deixá-la também parada até a F3-01 ser
 * decidida manteria o sistema incapaz de medir a própria divergência, que é
 * justamente o que a F3-01 espera para decidir. Um gate que impede a coleta da
 * evidência que ele mesmo exige nunca abre.
 *
 * <p>Por isso: chamada explícita, por gente com permissão, com o resultado na
 * resposta. O agendamento continua sendo decisão de governança, e o dia em que
 * for tomada o {@code Agendador} já tem onde ligar.
 *
 * <p><b>{@code CONDUZIR_CICLO}, e isso é uma leitura.</b> O cap. 15.1 não
 * nomeia a varredura. Ela é o ato operacional que enche o ciclo, e quem conduz
 * o ciclo é quem responde por ele — a mesma leitura que o
 * {@code CicloController} já declara para essa permissão. A consequência é
 * concreta e fica registrada: o PUBLICADOR_AP <b>não</b> dispara varredura,
 * porque não tem CONDUZIR_CICLO. Alargar isso é decisão de cadastro, não de
 * código.
 */
@RestController
@RequestMapping("/api")
public class VarreduraController {

    /** O identificador da origem em {@code documento.origem}. */
    private static final String ORIGEM = "OWNCLOUD";

    private final AtorDaRequisicao atores;
    private final ConsultaDoPainel painel;
    private final RepositorioDeColeta coleta;
    private final VarreduraDeCiclo ingestao;
    private final SimulacaoDeVarredura simulacao;
    private final Varredura varredura;

    public VarreduraController(AtorDaRequisicao atores, ConsultaDoPainel painel,
                               RepositorioDeColeta coleta, VarreduraDeCiclo ingestao,
                               SimulacaoDeVarredura simulacao,
                               ObjectProvider<Varredura> varredura) {
        this.atores = atores;
        this.painel = painel;
        this.coleta = coleta;
        this.ingestao = ingestao;
        this.simulacao = simulacao;
        this.varredura = varredura.getIfAvailable();
    }

    /**
     * Varre a pasta do contrato do ciclo e ingere o que encontrar.
     *
     * <p>Idempotente por construção: o delta do cap. 8.1 pula o que já está
     * registrado com a mesma versão, e a ingestão reconhece o hash repetido.
     * Chamar duas vezes seguidas não duplica documento — a segunda devolve
     * zero inéditos, que é informação e não erro.
     */
    @PostMapping("/ciclos/{cicloId}/varredura")
    public Map<String, Object> varrer(@PathVariable UUID cicloId) {
        Ator ator = exigir(Permissao.CONDUZIR_CICLO, cicloId);

        // A RECUSA VEM ANTES DE QUALQUER TRABALHO, E DIZ QUAL VARIÁVEL FALTA.
        //
        // Sem repositório configurado a varredura devolveria zero documentos —
        // exatamente o que ela devolve quando a pasta está vazia. Dois estados
        // opostos com a mesma resposta: "não há nada lá" e "não sei olhar".
        // Esta base já pagou caro por essa confusão mais de uma vez.
        if (varredura == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "a varredura não está configurada: falta SGDF_WEBDAV_BASE (e as "
                    + "credenciais SGDF_WEBDAV_USUARIO/SENHA). Isto NÃO é 'nenhum "
                    + "documento encontrado' — é o sistema não tendo onde olhar.");
        }

        RepositorioDeColeta.Alvo alvo = coleta.alvoDe(cicloId);
        if (alvo == null) {
            // Inalcançável na prática: o exigir() acima já derruba ciclo
            // inexistente. Fica porque o dia em que a autorização mudar, isto
            // é o que impede um NullPointerException virar 500.
            throw new AcessoNegado("ciclo " + cicloId + " não existe");
        }
        if (alvo.pastaOrigem() == null || alvo.pastaOrigem().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "o contrato do ciclo não tem pasta_origem no cadastro; "
                    + "sem ela não há onde varrer");
        }

        ResultadoVarredura resultado = varredura.varrer(alvo.pastaOrigem(), alvo.competencia(),
                coleta.estadoDe(ORIGEM));

        VarreduraDeCiclo.Ingerida ingerida = ingestao.ingerir(cicloId, alvo.contratoId(),
                alvo.competencia(), resultado, ator.identificador());

        // A resposta conta as duas metades: o que a varredura VIU e o que a
        // ingestão FEZ. Só a segunda esconderia arquivo ignorado por extensão,
        // infectado e falha de download — que são exatamente os casos em que
        // alguém precisa ir olhar a pasta.
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("origem", ORIGEM);
        corpo.put("raiz", alvo.pastaOrigem());
        corpo.put("competencia", alvo.competencia());
        corpo.put("coletados", resultado.coletados.size());
        corpo.put("inalterados", resultado.inalterados.size());
        corpo.put("ignorados", resultado.ignorados.size());
        corpo.put("infectados", resultado.infectados.size());
        corpo.put("falhas_na_coleta", resultado.falhas.size());
        corpo.put("processados", ingerida.processados());
        corpo.put("ineditos", ingerida.ineditos());
        corpo.put("em_triagem", ingerida.emTriagem());
        corpo.put("sem_exigencia", ingerida.semExigencia());
        corpo.put("falhas_na_ingestao", ingerida.falhas());
        corpo.put("truncada", ingerida.truncada());
        corpo.put("motivo_truncamento", ingerida.motivoTruncamento());
        corpo.put("alias_desligado", ingerida.aliasDesligado());
        return corpo;
    }

    /**
     * O que a varredura encontraria — <b>sem gravar nada</b>.
     *
     * <p>Este é o endpoint do PRIMEIRO teste contra uma pasta de verdade. Ele
     * responde à única pergunta que importa antes de ligar a ingestão: <i>o
     * sistema reconhece estes documentos?</i> E responde sem tornar a resposta
     * irreversível — ver {@link SimulacaoDeVarredura} para por que uma primeira
     * varredura real numa pasta de Departamento de Pessoal é uma decisão de via
     * única.
     *
     * <p><b>POST, e não GET, apesar de não gravar.</b> A operação baixa a pasta
     * inteira e roda antivírus e extração sobre cada arquivo: é cara, não é
     * cacheável e não deve aparecer em barra de endereço, histórico de
     * navegador ou log de proxy com o identificador do ciclo. GET prometeria
     * uma inocuidade que esta chamada não tem.
     *
     * <p><b>Mesma permissão da varredura real.</b> A prévia lê os mesmos bytes
     * dos mesmos arquivos; só não os guarda. Fosse mais frouxa, seria o caminho
     * mais curto para ler documento de contrato alheio sem ter CONDUZIR_CICLO —
     * um controle contornado pela porta que existe para não gravar nada.
     */
    @PostMapping("/ciclos/{cicloId}/varredura/simulacao")
    public Map<String, Object> simular(@PathVariable UUID cicloId) {
        exigir(Permissao.CONDUZIR_CICLO, cicloId);

        if (varredura == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "a varredura não está configurada: falta SGDF_WEBDAV_BASE (e as "
                    + "credenciais SGDF_WEBDAV_USUARIO/SENHA). Isto NÃO é 'nenhum "
                    + "documento encontrado' — é o sistema não tendo onde olhar.");
        }

        RepositorioDeColeta.Alvo alvo = coleta.alvoDe(cicloId);
        if (alvo == null) {
            throw new AcessoNegado("ciclo " + cicloId + " não existe");
        }
        if (alvo.pastaOrigem() == null || alvo.pastaOrigem().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "o contrato do ciclo não tem pasta_origem no cadastro; "
                    + "sem ela não há onde varrer");
        }

        // SEM DELTA: a prévia olha a pasta INTEIRA, sempre. Ver o javadoc da
        // SimulacaoDeVarredura — esconder o que já foi ingerido faria o
        // relatório de conferência conferir metade.
        ResultadoVarredura resultado = varredura.varrer(alvo.pastaOrigem(),
                alvo.competencia(), SimulacaoDeVarredura.SEM_DELTA);

        SimulacaoDeVarredura.Simulada s = simulacao.simular(cicloId, alvo.competencia(),
                alvo.pastaOrigem(), resultado);

        Map<String, Object> corpo = new LinkedHashMap<>();
        // O AVISO VEM PRIMEIRO, NO CORPO, E NÃO SÓ NO NOME DA ROTA. Quem lê o
        // JSON num terminal vê a primeira linha; quem monta uma tela em cima
        // dele tem um campo para exibir. "gravou" é a pergunta que alguém vai
        // fazer olhando este relatório, e ela tem que estar respondida nele.
        corpo.put("modo", "SIMULACAO");
        corpo.put("gravou", false);
        corpo.put("origem", ORIGEM);
        corpo.put("raiz", s.raiz());
        corpo.put("competencia", s.competencia());
        corpo.put("visitados", s.visitados());
        corpo.put("lidos", s.itens().size());
        corpo.put("automaticos", s.automaticos());
        corpo.put("em_triagem", s.comDecisao(br.com.engesoftware.sgdf.classificacao
                .Decisao.TRIAGEM));
        corpo.put("nao_reconhecidos", s.comDecisao(br.com.engesoftware.sgdf.classificacao
                .Decisao.NAO_RECONHECIDO));
        corpo.put("por_tipo", s.porTipo());
        corpo.put("exigencias_sem_documento", s.exigenciasSemDocumento());
        corpo.put("itens", s.itens());
        corpo.put("descartados", s.descartados());
        corpo.put("truncada", s.truncada());
        corpo.put("motivo_truncamento", s.motivoTruncamento());
        corpo.put("alias_desligado", s.aliasDesligado());
        return corpo;
    }

    // -------------------------------------------------------------------------

    /** Autoriza com o contrato DO CICLO no alvo — nunca um recebido do cliente. */
    private Ator exigir(Permissao permissao, UUID cicloId) {
        UUID contrato = painel.contratoDoCiclo(cicloId);
        if (contrato == null) {
            throw new AcessoNegado("ciclo " + cicloId + " não existe");
        }
        Ator ator = atores.atual();
        Autorizador.Decisao decisao = Autorizador.pode(ator, permissao,
                Autorizador.Alvo.doContrato(contrato));
        if (decisao.negada()) {
            throw new AcessoNegado(decisao.motivo());
        }
        return ator;
    }
}
