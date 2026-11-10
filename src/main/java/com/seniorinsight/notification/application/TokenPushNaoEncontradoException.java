package com.seniorinsight.notification.application;

public class TokenPushNaoEncontradoException extends RuntimeException {

    public TokenPushNaoEncontradoException(String identificadorAparelho) {
        super("Nenhum token de push registrado para o aparelho informado.");
    }
}
