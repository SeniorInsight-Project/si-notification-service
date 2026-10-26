package com.seniorinsight.notification.adapter.in.rabbit;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.EventoSos;

public record SosEventPayload(
        UUID alarmeId,
        UUID idosoId,
        String nomeIdoso,
        Double latitude,
        Double longitude,
        Integer nivelBateria,
        String observacao,
        Instant acionadoEm
) {

    public EventoSos toDomain() {
        return new EventoSos(alarmeId, idosoId, nomeIdoso, latitude, longitude, nivelBateria, observacao, acionadoEm);
    }
}
