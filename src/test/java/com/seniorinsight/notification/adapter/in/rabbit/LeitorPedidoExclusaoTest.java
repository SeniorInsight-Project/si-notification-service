package com.seniorinsight.notification.adapter.in.rabbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.notification.domain.model.PedidoExclusao;
import com.seniorinsight.notification.domain.model.TipoTitular;
import org.junit.jupiter.api.Test;

class LeitorPedidoExclusaoTest {

    private static final String PROTOCOLO = "44b69553-caa4-48a5-9e4b-22da0c4f2090";
    private static final String TITULAR_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String TITULAR_SUB = "9b2f1c3e-7a44-4d51-8c0e-5d6a1f2b3c4d";

    private final ObjectMapper mapper = new ObjectMapper();
    private final LeitorPedidoExclusao leitor = new LeitorPedidoExclusao();

    private JsonNode envelope(String campo, String valor) throws Exception {
        String json = """
                {"specversion":"1.0","id":"176f6ee8-3920-4ea4-b558-b50ee934825a","source":"/report-service",
                 "type":"lgpd.erasure.requested","time":"2026-11-08T15:30:00Z","datacontenttype":"application/json",
                 "dataschema":"https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.requested.v1.json",
                 "subject":"%s",
                 "data":{"protocolo":"%s","tipoTitular":"IDOSO","titularId":"%s","titularSub":"%s"}}
                """.formatted(PROTOCOLO, PROTOCOLO, TITULAR_ID, TITULAR_SUB);
        JsonNode no = mapper.readTree(json);
        if (campo == null) {
            return no;
        }
        if (campo.startsWith("data.")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) no.get("data")).put(campo.substring(5), valor);
        } else {
            ((com.fasterxml.jackson.databind.node.ObjectNode) no).put(campo, valor);
        }
        return no;
    }

    @Test
    void leOPedidoDoIdoso() throws Exception {
        PedidoExclusao pedido = leitor.ler(envelope(null, null));

        assertEquals(new PedidoExclusao(UUID.fromString(PROTOCOLO), TipoTitular.IDOSO,
                UUID.fromString(TITULAR_ID), UUID.fromString(TITULAR_SUB)), pedido);
    }

    @Test
    void leOPedidoDoFamiliar() throws Exception {
        assertEquals(TipoTitular.FAMILIAR, leitor.ler(envelope("data.tipoTitular", "FAMILIAR")).tipo());
    }

    @Test
    void rejeitaEnvelopeForaDoContrato() throws Exception {
        String[][] invalidos = {
                {"specversion", "2.0"},
                {"source", "/care-service"},
                {"type", "lgpd.erasure.completed"},
                {"dataschema", "https://exemplo.com/outro.json"},
                {"id", "nao-e-uuid"},
                {"subject", "11111111-1111-1111-1111-111111111111"},
                {"data.protocolo", "nao-e-uuid"},
                {"data.tipoTitular", "MEDICO"},
                {"data.titularId", "nao-e-uuid"},
                {"data.titularSub", "nao-e-uuid"},
        };
        for (String[] invalido : invalidos) {
            JsonNode envelope = envelope(invalido[0], invalido[1]);
            assertThrows(EnvelopeInvalidoException.class, () -> leitor.ler(envelope), invalido[0]);
        }
    }

    @Test
    void rejeitaEnvelopeSemData() throws Exception {
        JsonNode envelope = envelope(null, null);
        ((com.fasterxml.jackson.databind.node.ObjectNode) envelope).remove("data");

        assertThrows(EnvelopeInvalidoException.class, () -> leitor.ler(envelope));
    }
}
