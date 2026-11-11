package com.seniorinsight.notification.adapter.in.web;

import java.time.Instant;

import com.seniorinsight.notification.application.AcessoNegadoException;
import com.seniorinsight.notification.application.AlertaNaoEncontradoException;
import com.seniorinsight.notification.application.NotificacaoNaoEncontradaException;
import com.seniorinsight.notification.application.TokenPushNaoEncontradoException;
import com.seniorinsight.notification.domain.model.PrazoAvaliacaoVencidoException;
import com.seniorinsight.notification.domain.port.RedeProtecaoIndisponivelException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Erros no formato {@code Erro} do contrato: timestamp, status, error, message e path. */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Erro> requisicaoInvalida(Exception exception, HttpServletRequest request) {
        String mensagem = exception instanceof IllegalArgumentException
                ? exception.getMessage()
                : "Corpo da requisição inválido.";
        return resposta(HttpStatus.BAD_REQUEST, mensagem, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Erro> parametroInvalido(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro '" + exception.getName() + "' inválido.", request);
    }

    @ExceptionHandler({TokenPushNaoEncontradoException.class, AlertaNaoEncontradoException.class,
            NotificacaoNaoEncontradaException.class})
    ResponseEntity<Erro> naoEncontrado(RuntimeException exception, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<Erro> acessoNegado(AcessoNegadoException exception, HttpServletRequest request) {
        return resposta(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(PrazoAvaliacaoVencidoException.class)
    ResponseEntity<Erro> prazoVencido(PrazoAvaliacaoVencidoException exception, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(RedeProtecaoIndisponivelException.class)
    ResponseEntity<Erro> redeIndisponivel(RedeProtecaoIndisponivelException exception, HttpServletRequest request) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível confirmar a rede do idoso. Tente novamente.", request);
    }

    private static ResponseEntity<Erro> resposta(HttpStatus status, String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new Erro(Instant.now(), status.value(), status.getReasonPhrase(), mensagem, request.getRequestURI()));
    }

    public record Erro(Instant timestamp, int status, String error, String message, String path) {
    }
}
