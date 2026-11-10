package com.seniorinsight.notification.adapter.in.web;

import java.time.Instant;

import com.seniorinsight.notification.application.TokenPushNaoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(TokenPushNaoEncontradoException.class)
    ResponseEntity<Erro> naoEncontrado(TokenPushNaoEncontradoException exception, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    private static ResponseEntity<Erro> resposta(HttpStatus status, String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new Erro(Instant.now(), status.value(), status.getReasonPhrase(), mensagem, request.getRequestURI()));
    }

    public record Erro(Instant timestamp, int status, String error, String message, String path) {
    }
}
