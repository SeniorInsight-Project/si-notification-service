package com.seniorinsight.notification.adapter.in.web;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

final class UsuarioDoJwt {

    private UsuarioDoJwt() {
    }

    static UUID id(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("O sub do token não é um identificador de usuário válido.");
        }
    }
}
