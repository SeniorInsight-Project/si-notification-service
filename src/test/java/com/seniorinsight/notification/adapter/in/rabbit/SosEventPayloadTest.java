package com.seniorinsight.notification.adapter.in.rabbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.notification.domain.model.EventoSos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * Usa o ObjectMapper configurado pelo Spring Boot — o mesmo que o listener recebe em produção —
 * para garantir o contrato do evento publicado pelo produtor do SOS.
 */
@JsonTest
class SosEventPayloadTest {

    private static final String ALARME = "7f1c2d9e-0a51-4a3c-9a43-1c2b3d4e5f60";
    private static final String IDOSO = "1a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void desserializaEventoCompletoEConverteParaODominio() throws IOException {
        String json = """
                {"alarmeId":"%s","idosoId":"%s","nomeIdoso":"Maria",
                 "latitude":-23.55,"longitude":-46.63,"nivelBateria":42,
                 "observacao":"caí","acionadoEm":"2026-11-04T12:00:00Z"}
                """.formatted(ALARME, IDOSO);

        EventoSos evento = objectMapper.readValue(json, SosEventPayload.class).toDomain();

        assertEquals(new EventoSos(UUID.fromString(ALARME), UUID.fromString(IDOSO), "Maria",
                -23.55, -46.63, 42, "caí", Instant.parse("2026-11-04T12:00:00Z")), evento);
    }

    @Test
    void ignoraCamposDesconhecidosParaOProdutorPoderEvoluirOEvento() throws IOException {
        String json = """
                {"alarmeId":"%s","idosoId":"%s","campoNovo":{"x":1},"versao":2}
                """.formatted(ALARME, IDOSO);

        SosEventPayload payload = objectMapper.readValue(json, SosEventPayload.class);

        assertEquals(UUID.fromString(ALARME), payload.alarmeId());
    }

    @Test
    void aceitaCamposOpcionaisAusentes() throws IOException {
        SosEventPayload payload = objectMapper.readValue(
                "{\"alarmeId\":\"%s\",\"idosoId\":\"%s\"}".formatted(ALARME, IDOSO), SosEventPayload.class);

        assertNull(payload.nomeIdoso());
        assertNull(payload.latitude());
        assertNull(payload.nivelBateria());
        assertNull(payload.acionadoEm());
    }

    @Test
    void converteHorarioComFusoParaInstante() throws IOException {
        SosEventPayload payload = objectMapper.readValue(
                "{\"alarmeId\":\"%s\",\"idosoId\":\"%s\",\"acionadoEm\":\"2026-11-04T09:00:00-03:00\"}"
                        .formatted(ALARME, IDOSO),
                SosEventPayload.class);

        assertEquals(Instant.parse("2026-11-04T12:00:00Z"), payload.acionadoEm());
    }

    @Test
    void rejeitaIdentificadorQueNaoEhUuid() {
        assertThrows(IOException.class, () -> objectMapper.readValue(
                "{\"alarmeId\":\"123\",\"idosoId\":\"%s\"}".formatted(IDOSO), SosEventPayload.class));
    }

    @Test
    void rejeitaCampoComTipoIncompativel() {
        assertThrows(IOException.class, () -> objectMapper.readValue(
                "{\"alarmeId\":\"%s\",\"idosoId\":\"%s\",\"nivelBateria\":\"cheia\"}".formatted(ALARME, IDOSO),
                SosEventPayload.class));
    }

    @Test
    void conversaoParaDominioExigeIdentificadores() {
        SosEventPayload semIdoso = new SosEventPayload(UUID.randomUUID(), null, "Maria",
                null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, semIdoso::toDomain);
    }
}
