package com.seniorinsight.notification.adapter.in.rabbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.notification.application.EnviarSosPorPushUseCase;
import com.seniorinsight.notification.application.ExcluirTitularUseCase;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.domain.model.PedidoExclusao;
import com.seniorinsight.notification.domain.model.TipoTitular;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class EventoListenerExclusaoTest {

    private static final String PROTOCOLO = "44b69553-caa4-48a5-9e4b-22da0c4f2090";
    private static final String TITULAR_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String TITULAR_SUB = "9b2f1c3e-7a44-4d51-8c0e-5d6a1f2b3c4d";
    private static final int TENTATIVAS = 3;

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ExcluirTitularUseCase excluir = mock(ExcluirTitularUseCase.class);
    private final ProcessarEventoSosUseCase processarSos = mock(ProcessarEventoSosUseCase.class);
    private final EnviarSosPorPushUseCase enviarSos = mock(EnviarSosPorPushUseCase.class);
    private final Channel channel = mock(Channel.class);
    private final TratadorPedidoExclusao tratador =
            new TratadorPedidoExclusao(new LeitorPedidoExclusao(), excluir, intervalo -> { }, TENTATIVAS, Duration.ZERO);
    private final EventoListener listener = new EventoListener(mapper, processarSos, enviarSos, tratador);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(EventoListener.class);

    @BeforeEach
    void capturarLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(logs);
    }

    private Message pedido(String source) {
        String json = """
                {"specversion":"1.0","id":"176f6ee8-3920-4ea4-b558-b50ee934825a","source":"%s",
                 "type":"lgpd.erasure.requested","time":"2026-11-08T15:30:00Z","datacontenttype":"application/json",
                 "dataschema":"https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.requested.v1.json",
                 "subject":"%s",
                 "data":{"protocolo":"%s","tipoTitular":"IDOSO","titularId":"%s","titularSub":"%s"}}
                """.formatted(source, PROTOCOLO, PROTOCOLO, TITULAR_ID, TITULAR_SUB);
        MessageProperties propriedades = new MessageProperties();
        propriedades.setDeliveryTag(21L);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propriedades);
    }

    private long erros() {
        return logs.list.stream().filter(evento -> evento.getLevel() == Level.ERROR).count();
    }

    @Test
    void pedidoValidoExcluiOTitularEConfirmaAMensagem() throws Exception {
        listener.consumir(pedido("/report-service"), channel);

        verify(excluir).executar(new PedidoExclusao(UUID.fromString(PROTOCOLO), TipoTitular.IDOSO,
                UUID.fromString(TITULAR_ID), UUID.fromString(TITULAR_SUB)));
        verify(channel).basicAck(21L, false);
        verifyNoInteractions(processarSos, enviarSos);
        assertEquals(0, erros());
    }

    @Test
    void falhaTemporariaTentaDeNovoEConfirmaQuandoPassa() throws Exception {
        doThrow(new IllegalStateException("banco fora")).doNothing().when(excluir).executar(any());

        listener.consumir(pedido("/report-service"), channel);

        verify(excluir, times(2)).executar(any());
        verify(channel).basicAck(21L, false);
        verify(channel, never()).basicNack(any(Long.class), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void limiteDeTentativasEstouradoVaiParaDeadLetterComLogDeErro() throws Exception {
        doThrow(new IllegalStateException("banco fora")).when(excluir).executar(any());

        listener.consumir(pedido("/report-service"), channel);

        verify(excluir, times(TENTATIVAS)).executar(any());
        verify(channel).basicNack(21L, false, false);
        verify(channel, never()).basicAck(any(Long.class), any(Boolean.class));
        assertEquals(1, erros());
        assertTrue(logs.list.get(0).getFormattedMessage().contains("dead-letter"));
    }

    @Test
    void envelopeInvalidoVaiParaDeadLetterSemTentarDeNovoComLogDeErro() throws Exception {
        listener.consumir(pedido("/care-service"), channel);

        verify(excluir, never()).executar(any());
        verify(channel).basicNack(21L, false, false);
        assertEquals(1, erros());
    }

    @Test
    void naoPassaPeloSosQuandoOTipoEExclusao() throws Exception {
        doNothing().when(excluir).executar(any());

        listener.consumir(pedido("/report-service"), channel);

        verify(processarSos, never()).processar(any());
    }
}
