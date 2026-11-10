package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Entrega(
        UUID id,
        UUID notificacaoId,
        UUID destinatarioId,
        CanalEntrega canal,
        StatusEntrega status,
        Instant enviadoEm
) {

    public static Entrega pendente(UUID notificacaoId, UUID destinatarioId, CanalEntrega canal) {
        return new Entrega(UUID.randomUUID(), notificacaoId, destinatarioId, canal, StatusEntrega.PENDENTE, null);
    }
}
