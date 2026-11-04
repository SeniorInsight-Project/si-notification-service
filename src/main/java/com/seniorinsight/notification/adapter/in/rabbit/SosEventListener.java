package com.seniorinsight.notification.adapter.in.rabbit;

import java.io.IOException;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SosEventListener {

    private static final Logger log = LoggerFactory.getLogger(SosEventListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessarEventoSosUseCase processarEventoSos;
    private final Duration esperaAntesDeReenfileirar;

    public SosEventListener(
            ObjectMapper objectMapper,
            ProcessarEventoSosUseCase processarEventoSos,
            @Value("${notification.rabbitmq.retry-delay:5s}") Duration esperaAntesDeReenfileirar) {
        this.objectMapper = objectMapper;
        this.processarEventoSos = processarEventoSos;
        this.esperaAntesDeReenfileirar = esperaAntesDeReenfileirar;
    }

    /**
     * Com ack manual, toda mensagem precisa terminar em ack ou nack — inclusive quando surge uma
     * exceção inesperada; senão ela fica "unacked" presa no canal até o consumidor reiniciar.
     */
    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consumir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        SosEventPayload payload = null;
        try {
            payload = objectMapper.readValue(message.getBody(), SosEventPayload.class);
            if (payload == null) {
                throw new IllegalArgumentException("O payload do evento SOS não pode ser nulo.");
            }
            boolean novo = processarEventoSos.processar(payload.toDomain());
            if (novo) {
                log.info("Evento SOS {} persistido para notificação.", payload.alarmeId());
            } else {
                log.info("Evento SOS {} duplicado; nenhuma notificação adicional foi criada.", payload.alarmeId());
            }
        } catch (IOException | RuntimeException falha) {
            rejeitar(channel, deliveryTag, descrever(message, payload), falha);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void rejeitar(Channel channel, long deliveryTag, String mensagem, Exception falha) throws IOException {
        if (ClassificadorDeFalhas.ehTransitoria(falha)) {
            log.warn("Falha transitória ao processar evento SOS ({}); a mensagem volta para a fila em {}.",
                    mensagem, esperaAntesDeReenfileirar, falha);
            aguardarAntesDeReenfileirar();
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        log.error("Falha permanente ao processar evento SOS ({}); mensagem encaminhada para a dead-letter.",
                mensagem, falha);
        channel.basicNack(deliveryTag, false, false);
    }

    /**
     * Segura o consumidor antes de devolver a mensagem: enquanto o banco estiver fora do ar, nada
     * pode ser processado, e reenfileirar imediatamente só geraria um loop de reentregas e logs.
     */
    private void aguardarAntesDeReenfileirar() {
        if (esperaAntesDeReenfileirar.isZero() || esperaAntesDeReenfileirar.isNegative()) {
            return;
        }
        try {
            Thread.sleep(esperaAntesDeReenfileirar);
        } catch (InterruptedException interrupcao) {
            // Desligamento em andamento: devolve a mensagem para a fila sem esperar.
            Thread.currentThread().interrupt();
        }
    }

    /** Identifica a mensagem nos logs sem expor o corpo, que contém dados pessoais do idoso. */
    private static String descrever(Message message, SosEventPayload payload) {
        MessageProperties propriedades = message.getMessageProperties();
        return "messageId=" + propriedades.getMessageId()
                + ", deliveryTag=" + propriedades.getDeliveryTag()
                + ", redelivered=" + propriedades.isRedelivered()
                + ", alarmeId=" + (payload == null ? "desconhecido" : payload.alarmeId())
                + ", bytes=" + (message.getBody() == null ? 0 : message.getBody().length);
    }
}
