package com.seniorinsight.notification.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.application.RegistrarTokenPushUseCase;
import com.seniorinsight.notification.application.RemoverTokenPushUseCase;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Plataforma;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas de push token do contrato notification-v1 ({@code PUT/DELETE /v1/push-tokens}). */
@RestController
public class PushTokenController {

    private final RegistrarTokenPushUseCase registrar;
    private final RemoverTokenPushUseCase remover;

    public PushTokenController(RegistrarTokenPushUseCase registrar, RemoverTokenPushUseCase remover) {
        this.registrar = registrar;
        this.remover = remover;
    }

    @PutMapping("/v1/push-tokens")
    public TokenPushResponse registrar(@AuthenticationPrincipal Jwt jwt, @RequestBody RegistrarTokenPushRequest request) {
        DispositivoPush dispositivo = registrar.registrar(
                usuarioId(jwt), request.identificadorAparelho(), request.token(), request.plataforma());
        return new TokenPushResponse(dispositivo.identificadorAparelho(), dispositivo.plataforma(), dispositivo.atualizadoEm());
    }

    @DeleteMapping("/v1/push-tokens/{identificadorAparelho}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal Jwt jwt, @PathVariable String identificadorAparelho) {
        remover.remover(usuarioId(jwt), identificadorAparelho);
    }

    private static UUID usuarioId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("O sub do token não é um identificador de usuário válido.");
        }
    }

    public record RegistrarTokenPushRequest(String token, String identificadorAparelho, Plataforma plataforma) {
    }

    public record TokenPushResponse(String identificadorAparelho, Plataforma plataforma, Instant atualizadoEm) {
    }
}
