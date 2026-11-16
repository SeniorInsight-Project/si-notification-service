package com.seniorinsight.notification.adapter.in.rabbit;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.notification.application.EnviarSosPorPushUseCase;
import com.seniorinsight.notification.application.ProcessarEventoSinalUseCase;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.domain.model.EventoSinal;
import com.seniorinsight.notification.domain.model.EventoSos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome a fila de notificações e despacha pelo {@code type} do envelope: SOS, pedido de exclusão (HU-022) ou sinal
 * do relógio (HU-028). Tipo desconhecido vai para a dead-letter, sem ser confirmado.
 */
@Component
public class EventoListener {

    private static final Logger log = LoggerFactory.getLogger(EventoListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessarEventoSosUseCase processarEventoSos;
    private final EnviarSosPorPushUseCase enviarSosPorPush;
    private final TratadorPedidoExclusao tratadorExclusao;
    private final LeitorEventoSinal leitorSinal;
    private final ProcessarEventoSinalUseCase processarEventoSinal;

    EventoListener(
            ObjectMapper objectMapper,
            ProcessarEventoSosUseCase processarEventoSos,
            EnviarSosPorPushUseCase enviarSosPorPush,
            TratadorPedidoExclusao tratadorExclusao,
            LeitorEventoSinal leitorSinal,
            ProcessarEventoSinalUseCase processarEventoSinal) {
        this.objectMapper = objectMapper;
        this.processarEventoSos = processarEventoSos;
        this.enviarSosPorPush = enviarSosPorPush;
        this.tratadorExclusao = tratadorExclusao;
        this.leitorSinal = leitorSinal;
        this.processarEventoSinal = processarEventoSinal;
    }

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consumir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            JsonNode envelope = objectMapper.readTree(message.getBody());
            String tipo = envelope.path("type").asText("");
            if (LeitorPedidoExclusao.TIPO.equals(tipo)) {
                tratadorExclusao.tratar(envelope);
            } else if (LeitorEventoSinal.suporta(tipo)) {
                tratarSinal(leitorSinal.ler(envelope));
            } else if (CloudEventSos.TIPO.equals(tipo)) {
                tratarSos(objectMapper.treeToValue(envelope, CloudEventSos.class));
            } else {
                throw new IllegalArgumentException("Tipo de evento não suportado nesta fila: " + tipo);
            }
        } catch (Exception exception) {
            log.error("Falha ao processar o evento; mensagem {} encaminhada para dead-letter.", deliveryTag, exception);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void tratarSos(CloudEventSos evento) {
        if (evento == null || evento.data() == null) {
            throw new IllegalArgumentException("O evento SOS não traz o campo data.");
        }
        if (!evento.data().disparo()) {
            log.debug("Evento {} ignorado: so o disparo do SOS gera notificacao.", evento.id());
        } else {
            tratarDisparo(evento.data().toDomain());
        }
    }

    private void tratarSinal(EventoSinal evento) {
        boolean novo = processarEventoSinal.processar(evento);
        log.info("Evento de sinal {} ({}) {}.", evento.idEvento(), evento.tipo(),
                novo ? "registrado e avisado" : "ja registrado; nenhum aviso repetido");
    }

    private void tratarDisparo(EventoSos evento) {
        boolean novo = processarEventoSos.processar(evento);
        log.info("Evento SOS {} {}.", evento.idEvento(), novo ? "persistido para notificacao" : "ja registrado");
        // Roda tambem em duplicata: se o envio falhou antes, as entregas ja criadas sao puladas.
        int enviados = enviarSosPorPush.enviar(evento.idEvento());
        log.info("SOS {} enviado por push a {} familiar(es).", evento.idEvento(), enviados);
    }
}
