package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Perda ou retorno do sinal do relógio, como publicado pelo si-telemetry-service. */
public record EventoSinal(UUID idEvento, Tipo tipo, UUID idosoId, Instant ocorridoEm) {

    public enum Tipo {
        PERDIDO,
        RECUPERADO
    }
}
