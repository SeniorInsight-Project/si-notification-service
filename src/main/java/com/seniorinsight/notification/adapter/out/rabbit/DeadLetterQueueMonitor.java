package com.seniorinsight.notification.adapter.out.rabbit;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada mensagem na dead-letter é um SOS que não virou notificação. Este monitor consulta a fila
 * periodicamente, publica a métrica {@code notification.dlq.mensagens} e loga um WARN enquanto
 * houver mensagens paradas lá.
 */
@Component
public class DeadLetterQueueMonitor {

    static final String METRICA = "notification.dlq.mensagens";

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueMonitor.class);

    private final AmqpAdmin amqpAdmin;
    private final String fila;
    private final AtomicLong mensagens = new AtomicLong();

    public DeadLetterQueueMonitor(
            AmqpAdmin amqpAdmin,
            MeterRegistry meterRegistry,
            @Value("${notification.rabbitmq.dead-letter-queue}") String fila) {
        this.amqpAdmin = amqpAdmin;
        this.fila = fila;
        Gauge.builder(METRICA, mensagens, AtomicLong::get)
                .description("Eventos na fila de dead-letter do notification-service")
                .tag("fila", fila)
                .register(meterRegistry);
    }

    @Scheduled(
            initialDelayString = "${notification.rabbitmq.dead-letter-check-interval-ms:60000}",
            fixedDelayString = "${notification.rabbitmq.dead-letter-check-interval-ms:60000}")
    public void verificar() {
        QueueInformation informacao;
        try {
            informacao = amqpAdmin.getQueueInfo(fila);
        } catch (AmqpException falha) {
            log.warn("Não foi possível consultar a fila de dead-letter {}: {}", fila, falha.getMessage());
            return;
        }
        if (informacao == null) {
            log.warn("A fila de dead-letter {} não foi encontrada no RabbitMQ.", fila);
            return;
        }
        long total = informacao.getMessageCount();
        mensagens.set(total);
        if (total > 0) {
            log.warn("Há {} evento(s) SOS na fila de dead-letter {} que não viraram notificação. "
                    + "Verifique os logs de erro e reprocesse as mensagens.", total, fila);
        }
    }
}
