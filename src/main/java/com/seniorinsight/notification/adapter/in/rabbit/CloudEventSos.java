package com.seniorinsight.notification.adapter.in.rabbit;

/** Envelope CloudEvents do {@code sos.updated} (si-contracts/events/sos.updated.v1.json). */
public record CloudEventSos(String id, String type, SosEventPayload data) {

    static final String TIPO = "sos.updated";
}
