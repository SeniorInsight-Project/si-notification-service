package com.seniorinsight.notification.adapter.in.rabbit;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.notification.application.EnviarSosPorPushUseCase;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.domain.model.EventoSos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SosEventListener {

    private static final Logger log = LoggerFactory.getLogger(SosEventListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessarEventoSosUseCase processarEventoSos;
    private final EnviarSosPorPushUseCase enviarSosPorPush;

    public SosEventListener(
            ObjectMapper objectMapper,
            ProcessarEventoSosUseCase processarEventoSos,
            EnviarSosPorPushUseCase enviarSosPorPush) {
        this.objectMapper = objectMapper;
        this.processarEventoSos = processarEventoSos;
        this.enviarSosPorPush = enviarSosPorPush;
    }

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consumir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            CloudEventSos evento = objectMapper.readValue(message.getBody(), CloudEventSos.class);
            if (evento == null || evento.data() == null) {
                throw new IllegalArgumentException("O evento SOS não traz o campo data.");
            }
            if (!CloudEventSos.TIPO.equals(evento.type()) || !evento.data().disparo()) {
                log.debug("Evento {} ignorado: so o disparo do SOS gera notificacao.", evento.id());
            } else {
                tratarDisparo(evento.data().toDomain());
            }
        } catch (Exception exception) {
            log.error("Falha ao processar evento SOS; mensagem {} encaminhada para dead-letter.", deliveryTag, exception);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void tratarDisparo(EventoSos evento) {
        boolean novo = processarEventoSos.processar(evento);
        log.info("Evento SOS {} {}.", evento.idEvento(), novo ? "persistido para notificacao" : "ja registrado");
        // Roda tambem em duplicata: se o envio falhou antes, as entregas ja criadas sao puladas.
        int enviados = enviarSosPorPush.enviar(evento.idEvento());
        log.info("SOS {} enviado por push a {} familiar(es).", evento.idEvento(), enviados);
    }
}
