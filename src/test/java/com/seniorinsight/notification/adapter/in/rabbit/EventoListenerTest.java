package com.seniorinsight.notification.adapter.in.rabbit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.notification.application.EnviarSosPorPushUseCase;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.domain.model.EventoSos;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class EventoListenerTest {

    private static final String ALARME = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final ProcessarEventoSosUseCase processar = mock(ProcessarEventoSosUseCase.class);
    private final EnviarSosPorPushUseCase enviar = mock(EnviarSosPorPushUseCase.class);
    private final Channel channel = mock(Channel.class);
    private final EventoListener listener = new EventoListener(mapper, processar, enviar, mock(TratadorPedidoExclusao.class));

    private Message evento(String type, String status) {
        String json = """
                {"specversion":"1.0","id":"7c9e6679","source":"/care-service","type":"%s",
                 "subject":"%s","data":{"alarmeId":"%s","idosoId":"3fa85f64-5717-4562-b3fc-2c963f66afa6",
                 "nomeIdoso":"Maria","status":"%s","statusAnterior":null,
                 "acionadoEm":"2026-10-31T12:05:00Z","atualizadoEm":"2026-10-31T12:05:00Z"}}
                """.formatted(type, ALARME, ALARME, status);
        MessageProperties propriedades = new MessageProperties();
        propriedades.setDeliveryTag(7L);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propriedades);
    }

    @Test
    void disparoGeraNotificacaoEPush() throws Exception {
        when(processar.processar(any(EventoSos.class))).thenReturn(true);

        listener.consumir(evento("sos.updated", "DISPARADO"), channel);

        verify(processar).processar(any(EventoSos.class));
        verify(enviar).enviar(UUID.fromString(ALARME));
        verify(channel).basicAck(7L, false);
    }

    @Test
    void outrosEstadosSaoConfirmadosSemNotificar() throws Exception {
        listener.consumir(evento("sos.updated", "RESOLVIDO"), channel);

        verify(processar, never()).processar(any());
        verify(enviar, never()).enviar(any());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void mensagemInvalidaVaiParaDeadLetter() throws Exception {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setDeliveryTag(8L);

        listener.consumir(new Message("não é json".getBytes(StandardCharsets.UTF_8), propriedades), channel);

        verify(channel).basicNack(8L, false, false);
        verify(channel, never()).basicAck(any(Long.class), any(Boolean.class));
    }

    @Test
    void falhaNoEnvioVaiParaDeadLetter() throws Exception {
        when(enviar.enviar(any())).thenThrow(new IllegalStateException("care fora"));

        listener.consumir(evento("sos.updated", "DISPARADO"), channel);

        verify(channel).basicNack(7L, false, false);
    }
}
