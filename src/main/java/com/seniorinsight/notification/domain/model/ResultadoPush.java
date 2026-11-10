package com.seniorinsight.notification.domain.model;

public enum ResultadoPush {
    ENVIADO,
    FALHA,
    /** O provedor informou que o token não existe mais; o dispositivo deve ser desativado. */
    TOKEN_INVALIDO
}
