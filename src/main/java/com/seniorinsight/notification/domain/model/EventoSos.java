package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

public record EventoSos(
        UUID idEvento,
        UUID idosoId,
        String nomeIdoso,
        Double latitude,
        Double longitude,
        Integer nivelBateria,
        String observacao,
        Instant ocorridoEm
) {
}
