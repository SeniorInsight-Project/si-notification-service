package com.seniorinsight.notification.adapter.out.persistence;

import java.time.Clock;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.notification.domain.port.PublicadorExclusaoConcluida;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava {@code lgpd.erasure.completed} na tabela {@code outbox_evento}, na transação em andamento. O envelope segue
 * si-contracts/events/lgpd.erasure.completed.v1.json; o id dele é o da linha, que o relay usa como {@code message_id}.
 */
@Repository
public class OutboxPublicadorAdapter implements PublicadorExclusaoConcluida {

    static final String TIPO_EVENTO = "lgpd.erasure.completed";
    static final String SERVICO = "notification-service";

    private static final String TIPO_AGREGADO = "pedido_exclusao";
    private static final String DATA_SCHEMA =
            "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/" + TIPO_EVENTO + ".v1.json";

    record Dados(UUID protocolo, String servico) {
    }

    record Envelope(String specversion, UUID id, String source, String type, String time, String datacontenttype,
                    String dataschema, String subject, Dados data) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxPublicadorAdapter(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publicar(UUID protocolo) {
        UUID id = UUID.randomUUID();
        Envelope envelope = new Envelope("1.0", id, "/" + SERVICO, TIPO_EVENTO, clock.instant().toString(),
                "application/json", DATA_SCHEMA, protocolo.toString(), new Dados(protocolo, SERVICO));
        jdbc.sql("""
                INSERT INTO notification.outbox_evento (id, tipo_agregado, agregado_id, tipo_evento, payload)
                VALUES (:id, :tipoAgregado, :agregadoId, :tipoEvento, CAST(:payload AS jsonb))
                """)
                .param("id", id)
                .param("tipoAgregado", TIPO_AGREGADO)
                .param("agregadoId", protocolo)
                .param("tipoEvento", TIPO_EVENTO)
                .param("payload", paraJson(envelope))
                .update();
    }

    private String paraJson(Envelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o evento " + TIPO_EVENTO, exception);
        }
    }
}
