package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.persistencia.RepositorioDeCadastro;
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
