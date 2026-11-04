package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de acionamento do botão SOS, como recebido do produtor. Só os identificadores são
 * obrigatórios; os demais campos podem vir ausentes ou inválidos e são saneados pelo caso de uso.
 */
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

    public EventoSos {
        if (idEvento == null || idosoId == null) {
            throw new IllegalArgumentException("O evento SOS deve informar alarmeId e idosoId.");
        }
    }
}
