package com.seniorinsight.notification.adapter.out.rabbit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

class DeadLetterQueueMonitorTest {

    private static final String FILA = "notification.dlq";

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DeadLetterQueueMonitor monitor = new DeadLetterQueueMonitor(amqpAdmin, registry, FILA);

    @Test
    void publicaQuantidadeDeMensagensNaDeadLetter() {
        when(amqpAdmin.getQueueInfo(FILA)).thenReturn(new QueueInformation(FILA, 3, 0));

        monitor.verificar();

        assertEquals(3.0, registry.get(DeadLetterQueueMonitor.METRICA).tag("fila", FILA).gauge().value());
    }

    @Test
    void atualizaMetricaQuandoADeadLetterEsvazia() {
        when(amqpAdmin.getQueueInfo(FILA))
                .thenReturn(new QueueInformation(FILA, 5, 0))
                .thenReturn(new QueueInformation(FILA, 0, 0));

        monitor.verificar();
        assertEquals(5.0, valorDaMetrica());
        monitor.verificar();
        assertEquals(0.0, valorDaMetrica());
    }

    @Test
    void mantemUltimoValorQuandoFilaNaoExiste() {
        when(amqpAdmin.getQueueInfo(FILA))
                .thenReturn(new QueueInformation(FILA, 2, 0))
                .thenReturn(null);

        monitor.verificar();
        assertDoesNotThrow(monitor::verificar);
        assertEquals(2.0, valorDaMetrica());
    }

    @Test
    void toleraRabbitIndisponivel() {
        when(amqpAdmin.getQueueInfo(FILA)).thenThrow(new AmqpConnectException(new RuntimeException("fora")));

        assertDoesNotThrow(monitor::verificar);
        assertEquals(0.0, registry.get(DeadLetterQueueMonitor.METRICA).gauge().value());
    }

    private double valorDaMetrica() {
        return registry.get(DeadLetterQueueMonitor.METRICA).tag("fila", FILA).gauge().value();
    }
}
