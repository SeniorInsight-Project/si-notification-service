package com.seniorinsight.notification.adapter.in.rabbit;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.seniorinsight.notification.domain.model.PedidoExclusao;
import com.seniorinsight.notification.domain.model.TipoTitular;
import org.springframework.stereotype.Component;

/** Valida o envelope de {@code lgpd.erasure.requested} (si-contracts/events) e o converte em {@link PedidoExclusao}. */
@Component
class LeitorPedidoExclusao {

    static final String TIPO = "lgpd.erasure.requested";

    private static final String SPEC_VERSION = "1.0";
    private static final String SOURCE = "/report-service";
    private static final String DATA_SCHEMA =
            "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/" + TIPO + ".v1.json";

    PedidoExclusao ler(JsonNode envelope) {
        exigir(SPEC_VERSION.equals(texto(envelope, "specversion")), "specversion diferente de " + SPEC_VERSION);
        exigir(SOURCE.equals(texto(envelope, "source")), "source diferente de " + SOURCE);
        exigir(TIPO.equals(texto(envelope, "type")), "type diferente de " + TIPO);
        exigir(DATA_SCHEMA.equals(texto(envelope, "dataschema")), "dataschema fora do contrato");
        uuid(envelope, "id");

        JsonNode data = envelope.path("data");
        exigir(data.isObject(), "data ausente");
        UUID protocolo = uuid(data, "protocolo");
        exigir(protocolo.equals(uuid(envelope, "subject")), "subject diferente do protocolo");
        return new PedidoExclusao(protocolo, tipoTitular(data), uuid(data, "titularId"), uuid(data, "titularSub"));
    }

    private static TipoTitular tipoTitular(JsonNode data) {
        try {
            return TipoTitular.valueOf(String.valueOf(texto(data, "tipoTitular")));
        } catch (IllegalArgumentException exception) {
            throw new EnvelopeInvalidoException("tipoTitular inválido");
        }
    }

    private static UUID uuid(JsonNode no, String campo) {
        String valor = texto(no, campo);
        try {
            return UUID.fromString(String.valueOf(valor));
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
