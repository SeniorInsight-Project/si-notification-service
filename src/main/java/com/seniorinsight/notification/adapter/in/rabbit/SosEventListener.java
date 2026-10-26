package com.seniorinsight.notification.adapter.in.rabbit;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class SosEventListener {

    private static final Logger log = LoggerFactory.getLogger(SosEventListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessarEventoSosUseCase processarEventoSos;

    public SosEventListener(ObjectMapper objectMapper, ProcessarEventoSosUseCase processarEventoSos) {
        this.objectMapper = objectMapper;
        this.processarEventoSos = processarEventoSos;
    }

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consumir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            SosEventPayload payload = objectMapper.readValue(message.getBody(), SosEventPayload.class);
            if (payload == null) {
                throw new IllegalArgumentException("O payload do evento SOS não pode ser nulo.");
            }
            boolean novo = processarEventoSos.processar(payload.toDomain());
            if (novo) {
                log.info("Evento SOS {} persistido para notificacao.", payload.alarmeId());
            } else {
                log.info("Evento SOS {} duplicado; nenhuma notificacao adicional foi criada.", payload.alarmeId());
            }
        } catch (IOException | DataAccessException | IllegalArgumentException exception) {
            log.error("Falha permanente ao processar evento SOS; mensagem {} encaminhada para dead-letter.",
                    deliveryTag, exception);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }
}
