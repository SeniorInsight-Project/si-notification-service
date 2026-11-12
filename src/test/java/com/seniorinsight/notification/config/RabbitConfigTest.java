package com.seniorinsight.notification.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();
    private final Queue fila = new Queue("notification.q");
    private final TopicExchange exchange = new TopicExchange("si.events");

    @Test
    void ligaAFilaAUmaRoutingKeyPorEvento() {
        Declarables bindings = config.notificationBindings(fila, exchange, List.of("sos.updated", "lgpd.erasure.requested"));

        List<String> chaves = bindings.getDeclarablesByType(Binding.class).stream().map(Binding::getRoutingKey).toList();
        assertEquals(List.of("sos.updated", "lgpd.erasure.requested"), chaves);
    }

    @Test
    void ignoraEspacosEChavesVazias() {
        Declarables bindings = config.notificationBindings(fila, exchange, List.of(" sos.updated ", ""));

        List<String> chaves = bindings.getDeclarablesByType(Binding.class).stream().map(Binding::getRoutingKey).toList();
        assertEquals(List.of("sos.updated"), chaves);
    }
}
