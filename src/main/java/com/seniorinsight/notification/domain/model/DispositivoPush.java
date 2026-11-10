package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

public record DispositivoPush(
        UUID usuarioId,
        String identificadorAparelho,
        String token,
        Plataforma plataforma,
        Instant atualizadoEm
) {

    public static final int TAMANHO_MAXIMO_TOKEN = 4096;
    public static final int TAMANHO_MAXIMO_IDENTIFICADOR = 255;

    public DispositivoPush {
        if (usuarioId == null) {
            throw new IllegalArgumentException("O usuário do token de push é obrigatório.");
        }
        if (identificadorAparelho == null || identificadorAparelho.isBlank()
                || identificadorAparelho.length() > TAMANHO_MAXIMO_IDENTIFICADOR) {
            throw new IllegalArgumentException("identificadorAparelho deve ter de 1 a 255 caracteres.");
        }
        if (token == null || token.isBlank() || token.length() > TAMANHO_MAXIMO_TOKEN) {
            throw new IllegalArgumentException("token deve ter de 1 a 4096 caracteres.");
        }
        if (plataforma == null) {
            throw new IllegalArgumentException("plataforma deve ser ANDROID ou IOS.");
        }
    }
}
