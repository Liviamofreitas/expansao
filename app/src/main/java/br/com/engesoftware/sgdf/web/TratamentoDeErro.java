package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.ConsultaDeAuditoria;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeCadastro;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeCiclo;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeExcecao;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeRascunho;
import br.com.engesoftware.sgdf.persistencia.RepositorioDeTriagem;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as recusas do domínio em status HTTP.
 *
 * <p><b>Estas mensagens vão para o corpo, e o 403 não vai.</b> A diferença é
 * deliberada: "reclassificar exige escolher o tipo" e "esta candidatura já foi
 * decidida" falam de coisas que quem chamou <i>já sabe que existem</i> — ele
 * está olhando para o item na fila. O motivo de um 403, não: dizer "o contrato X
 * está fora do seu escopo" revela que X existe a quem não pode vê-lo.
 *
 * <p>Por isso o {@link AcessoNegado} continua fora daqui, com a mensagem só no
 * log.
 */
@RestControllerAdvice
public class TratamentoDeErro {

    /** Pedido que não faz sentido sobre o alvo — o cliente pode corrigir e repetir. */
    @ExceptionHandler(RepositorioDeTriagem.DecisaoInvalida.class)
    ResponseEntity<Map<String, String>> decisaoInvalida(
            RepositorioDeTriagem.DecisaoInvalida e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("motivo", e.getMessage()));
    }

    /** Argumento malformado, recusado ainda no domínio (PedidoDeTriagem). */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> argumentoInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("motivo", e.getMessage()));
    }

    /**
     * Cap. 12: publicar criticidade bloqueante exige APROVADOR_DAF.
     *
     * <p>403, e o motivo VAI no corpo — ao contrário do 403 de escopo. Aqui não
     * há nada a esconder: quem abriu o rascunho já vê os itens dele, e a
     * mensagem diz exatamente qual mudança precisa de outra pessoa. Sem isso, a
     * negativa vira "peça a alguém" sem dizer o quê.
     */
    @ExceptionHandler(RepositorioDeRascunho.ExigeAprovacaoDaf.class)
    ResponseEntity<Map<String, String>> exigeDaf(RepositorioDeRascunho.ExigeAprovacaoDaf e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("motivo", e.getMessage()));
    }

    /**
     * Cap. 15.1: quem solicitou não decide; decidir exige APROVADOR_DAF.
     *
     * <p>403 com motivo no corpo, como o do DAF na matriz: quem recebe já sabe
     * que a exceção existe — ele a solicitou. Esconder o motivo aqui só faria
     * a pessoa tentar de novo sem entender.
     */
    @ExceptionHandler(RepositorioDeExcecao.SegregacaoDeFuncoes.class)
    ResponseEntity<Map<String, String>> segregacao(RepositorioDeExcecao.SegregacaoDeFuncoes e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("motivo", e.getMessage()));
    }

    @ExceptionHandler(RepositorioDeExcecao.ExcecaoInvalida.class)
    ResponseEntity<Map<String, String>> excecaoInvalida(RepositorioDeExcecao.ExcecaoInvalida e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("motivo", e.getMessage()));
    }

    @ExceptionHandler(RepositorioDeExcecao.ExcecaoJaDecidida.class)
    ResponseEntity<Map<String, String>> excecaoDecidida(
            RepositorioDeExcecao.ExcecaoJaDecidida e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("motivo", e.getMessage()));
    }

    /**
     * Cap. 6.2: o movimento não existe, ou a pré-condição deixou de valer.
     *
     * <p>422 e não 409: o pedido é sobre um ciclo que existe, e o que não vale
     * é o movimento em si. A mensagem diz de onde o ciclo pode sair, porque a
     * tela precisa se corrigir e não adivinhar.
     */
    @ExceptionHandler(RepositorioDeCiclo.TransicaoInvalida.class)
    ResponseEntity<Map<String, String>> transicaoInvalida(
            RepositorioDeCiclo.TransicaoInvalida e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("motivo", e.getMessage()));
    }

    /**
     * F3-04: ciclo com bloqueante em aberto não vai a PRONTO.
     *
     * <p>409 com a LISTA no corpo, e a lista é o ponto. "Não pode" manda a
     * pessoa procurar; "faltam FGT.GUIA e DCT.DECLARACAO" manda a pessoa
     * resolver — ou pedir exceção, que é a saída que o cap. 6.1 prevê.
     */
    @ExceptionHandler(RepositorioDeCiclo.BloqueantesEmAberto.class)
    ResponseEntity<Map<String, Object>> bloqueantes(RepositorioDeCiclo.BloqueantesEmAberto e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("motivo", e.getMessage(), "bloqueantes", e.abertas()));
    }

    /**
     * Cap. 13: consultar a trilha exige recorte.
     *
     * <p>400 e não 403: quem pediu tem o papel. O que falta é o filtro, e a
     * mensagem diz qual — sem ela a pessoa tentaria de novo igual.
     */
    @ExceptionHandler(ConsultaDeAuditoria.FiltroObrigatorio.class)
    ResponseEntity<Map<String, String>> filtroObrigatorio(
            ConsultaDeAuditoria.FiltroObrigatorio e) {
        return ResponseEntity.badRequest().body(Map.of("motivo", e.getMessage()));
    }

    /** Unicidade do cadastro — 409, com o que fazer. */
    @ExceptionHandler(RepositorioDeCadastro.JaCadastrado.class)
    ResponseEntity<Map<String, String>> jaCadastrado(RepositorioDeCadastro.JaCadastrado e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("motivo", e.getMessage()));
    }

    @ExceptionHandler(RepositorioDeCadastro.CadastroInvalido.class)
    ResponseEntity<Map<String, String>> cadastroInvalido(
            RepositorioDeCadastro.CadastroInvalido e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("motivo", e.getMessage()));
    }

    @ExceptionHandler(RepositorioDeRascunho.RascunhoJaFechado.class)
    ResponseEntity<Map<String, String>> rascunhoFechado(
            RepositorioDeRascunho.RascunhoJaFechado e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("motivo", e.getMessage()));
    }

    @ExceptionHandler(RepositorioDeRascunho.NumeroJaUsado.class)
    ResponseEntity<Map<String, String>> numeroJaUsado(RepositorioDeRascunho.NumeroJaUsado e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("motivo", e.getMessage()));
    }

    /**
     * Corrida entre duas pessoas na mesma fila — 409, não 500.
     *
     * <p>Não é falha do sistema: é duas pessoas olhando a mesma fila. O 409 diz
     * "alguém chegou antes", que é o que a tela precisa mostrar para recarregar
     * em vez de sugerir que houve erro.
     */
    @ExceptionHandler(RepositorioDeTriagem.CandidaturaJaDecidida.class)
    ResponseEntity<Map<String, String>> jaDecidida(
            RepositorioDeTriagem.CandidaturaJaDecidida e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("motivo", e.getMessage()));
    }
}
