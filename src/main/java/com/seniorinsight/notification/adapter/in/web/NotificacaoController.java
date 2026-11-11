package com.seniorinsight.notification.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.application.ConfirmarLeituraNotificacaoUseCase;
import com.seniorinsight.notification.domain.model.LeituraNotificacao;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rota de notificações do contrato notification-v1 ({@code PUT /v1/notificacoes/{notificacaoId}/leitura}). */
@RestController
public class NotificacaoController {

    private final ConfirmarLeituraNotificacaoUseCase confirmarLeitura;

    public NotificacaoController(ConfirmarLeituraNotificacaoUseCase confirmarLeitura) {
        this.confirmarLeitura = confirmarLeitura;
    }

    @PutMapping("/v1/notificacoes/{notificacaoId}/leitura")
    public LeituraNotificacaoResponse confirmarLeitura(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificacaoId) {
        LeituraNotificacao leitura = confirmarLeitura.confirmar(UsuarioDoJwt.id(jwt), notificacaoId);
        return new LeituraNotificacaoResponse(leitura.notificacaoId(), leitura.lidaEm());
    }

    public record LeituraNotificacaoResponse(UUID notificacaoId, Instant lidaEm) {
    }
}
