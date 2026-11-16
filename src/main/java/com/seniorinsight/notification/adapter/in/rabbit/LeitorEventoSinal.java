package com.seniorinsight.notification.adapter.in.rabbit;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.seniorinsight.notification.domain.model.EventoSinal;
import org.springframework.stereotype.Component;

/**
 * Valida o envelope de {@code telemetry.signal.lost} e {@code telemetry.signal.recovered} (si-contracts/events) e o
 * converte em {@link EventoSinal}.
 */
@Component
class LeitorEventoSinal {

    static final String TIPO_PERDA = "telemetry.signal.lost";
    static final String TIPO_RETORNO = "telemetry.signal.recovered";

    private static final String SPEC_VERSION = "1.0";
    private static final String SOURCE = "/telemetry-service";
    private static final String DATA_CONTENT_TYPE = "application/json";
    private static final String BASE_SCHEMA =
            "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/";

    static boolean suporta(String tipo) {
        return TIPO_PERDA.equals(tipo) || TIPO_RETORNO.equals(tipo);
    }

    EventoSinal ler(JsonNode envelope) {
        String tipo = texto(envelope, "type");
        exigir(suporta(tipo), "type não é um evento de sinal");
        exigir(SPEC_VERSION.equals(texto(envelope, "specversion")), "specversion diferente de " + SPEC_VERSION);
        exigir(SOURCE.equals(texto(envelope, "source")), "source diferente de " + SOURCE);
        exigir(DATA_CONTENT_TYPE.equals(texto(envelope, "datacontenttype")),
                "datacontenttype diferente de " + DATA_CONTENT_TYPE);
        exigir((BASE_SCHEMA + tipo + ".v1.json").equals(texto(envelope, "dataschema")),
                "dataschema fora do contrato");
        UUID idEvento = uuid(envelope, "id");
        instante(envelope, "time");

        JsonNode data = envelope.path("data");
        exigir(data.isObject(), "data ausente");
        UUID idosoId = uuid(data, "idosoId");
        exigir(idosoId.equals(uuid(envelope, "subject")), "subject diferente do idosoId");

        if (TIPO_PERDA.equals(tipo)) {
            instante(data, "ultimaSincronizacao");
            return new EventoSinal(idEvento, EventoSinal.Tipo.PERDIDO, idosoId, instante(data, "detectadoEm"));
        }
        return new EventoSinal(idEvento, EventoSinal.Tipo.RECUPERADO, idosoId, instante(data, "sincronizadoEm"));
    }

    private static Instant instante(JsonNode no, String campo) {
        try {
            return Instant.parse(String.valueOf(texto(no, campo)));
        } catch (DateTimeParseException exception) {
            throw new EnvelopeInvalidoException(campo + " não é uma data e hora");
        }
    }

    private static UUID uuid(JsonNode no, String campo) {
        try {
            return UUID.fromString(String.valueOf(texto(no, campo)));
        } catch (IllegalArgumentException exception) {
            throw new EnvelopeInvalidoException(campo + " não é um UUID");
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor != null && valor.isTextual() ? valor.asText() : null;
    }

    private static void exigir(boolean condicao, String motivo) {
        if (!condicao) {
            throw new EnvelopeInvalidoException(motivo);
        }
    }
}
