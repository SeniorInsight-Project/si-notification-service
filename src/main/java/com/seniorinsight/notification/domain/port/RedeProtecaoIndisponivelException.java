package com.seniorinsight.notification.domain.port;

public class RedeProtecaoIndisponivelException extends RuntimeException {

    public RedeProtecaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
