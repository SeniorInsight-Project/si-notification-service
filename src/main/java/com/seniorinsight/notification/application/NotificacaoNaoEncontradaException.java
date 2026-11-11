package com.seniorinsight.notification.application;

public class NotificacaoNaoEncontradaException extends RuntimeException {

    public NotificacaoNaoEncontradaException() {
        super("Notificação não encontrada.");
    }
}
