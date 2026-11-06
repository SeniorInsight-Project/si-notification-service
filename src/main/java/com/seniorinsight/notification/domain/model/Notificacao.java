package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Notificacao(
        UUID id,
        UUID idEvento,
        String tipo,
        UUID idosoId,
        String titulo,
        String mensagem,
        Instant ocorridoEm
) {
}
