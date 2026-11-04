package com.seniorinsight.notification.adapter.in.rabbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.domain.model.EventoSos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;

class SosEventListenerTest {

    private static final long TAG = 7L;
    /** Mesmas configurações que o Spring Boot aplica (ignora campos desconhecidos, suporta java.time). */
    private static final ObjectMapper OBJECT_MAPPER = Jackson2ObjectMapperBuilder.json().build();
    private static final String EVENTO_VALIDO = """
            {"alarmeId":"7f1c2d9e-0a51-4a3c-9a43-1c2b3d4e5f60",
             "idosoId":"1a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d",
             "nomeIdoso":"Maria","latitude":-23.55,"longitude":-46.63,
             "nivelBateria":42,"acionadoEm":"2026-10-26T12:00:00Z"}
            """;

    private ProcessarEventoSosUseCase useCase;
    private Channel channel;
    private SosEventListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(ProcessarEventoSosUseCase.class);
        channel = mock(Channel.class);
        listener = new SosEventListener(OBJECT_MAPPER, useCase, Duration.ZERO);
    }

    @Test
    void entregaAoCasoDeUsoOEventoConvertido() throws Exception {
        when(useCase.processar(any())).thenReturn(true);

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        ArgumentCaptor<EventoSos> captor = ArgumentCaptor.forClass(EventoSos.class);
        verify(useCase).processar(captor.capture());
        assertEquals(UUID.fromString("7f1c2d9e-0a51-4a3c-9a43-1c2b3d4e5f60"), captor.getValue().idEvento());
        assertEquals("Maria", captor.getValue().nomeIdoso());
        assertEquals(42, captor.getValue().nivelBateria());
    }

    @Test
    void confirmaEventoComCamposDesconhecidos() throws Exception {
        when(useCase.processar(any())).thenReturn(true);

        listener.consumir(mensagem(EVENTO_VALIDO.replace("{\"alarmeId\"", "{\"versao\":2,\"alarmeId\"")), channel);

        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void mandaIdentificadorInvalidoParaDeadLetter() throws Exception {
        listener.consumir(mensagem("{\"alarmeId\":\"abc\",\"idosoId\":\"1a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d\"}"),
                channel);

        verificarDeadLetter();
        verify(useCase, never()).processar(any());
    }

    @Test
    void mandaCorpoVazioParaDeadLetter() throws Exception {
        listener.consumir(mensagem(""), channel);

        verificarDeadLetter();
    }

    @Test
    void esperaOIntervaloConfiguradoAntesDeReenfileirar() throws Exception {
        Duration espera = Duration.ofMillis(300);
        SosEventListener comEspera = new SosEventListener(OBJECT_MAPPER, useCase, espera);
        when(useCase.processar(any())).thenThrow(new QueryTimeoutException("timeout"));

        long inicio = System.nanoTime();
        comEspera.consumir(mensagem(EVENTO_VALIDO), channel);
        Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertTrue(decorrido.compareTo(espera) >= 0, "esperou só " + decorrido.toMillis() + " ms");
        verificarReenfileirada();
    }

    @Test
    void naoEsperaAntesDeMandarParaDeadLetter() throws Exception {
        SosEventListener comEspera = new SosEventListener(OBJECT_MAPPER, useCase, Duration.ofSeconds(30));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> comEspera.consumir(mensagem("{nao e json"), channel));
        verificarDeadLetter();
    }

    @Test
    void reenfileiraSemEsperarQuandoThreadEhInterrompidaEPreservaAInterrupcao() throws Exception {
        SosEventListener comEspera = new SosEventListener(OBJECT_MAPPER, useCase, Duration.ofSeconds(30));
        when(useCase.processar(any())).thenThrow(new QueryTimeoutException("timeout"));

        Thread.currentThread().interrupt();
        try {
            // assertTimeout (não preemptivo) roda na mesma thread, que é a que está interrompida.
            assertTimeout(Duration.ofSeconds(2), () -> comEspera.consumir(mensagem(EVENTO_VALIDO), channel));
            assertTrue(Thread.currentThread().isInterrupted(), "o sinal de interrupção deve ser preservado");
        } finally {
            Thread.interrupted();
        }
        verificarReenfileirada();
    }

    @Test
    void propagaFalhaDoAckParaOContainer() throws Exception {
        when(useCase.processar(any())).thenReturn(true);
        doThrow(new IOException("canal fechado")).when(channel).basicAck(TAG, false);

        assertThrows(IOException.class, () -> listener.consumir(mensagem(EVENTO_VALIDO), channel));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void confirmaEventoNovo() throws Exception {
        when(useCase.processar(any())).thenReturn(true);

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void confirmaEventoDuplicado() throws Exception {
        when(useCase.processar(any())).thenReturn(false);

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void mandaJsonInvalidoParaDeadLetter() throws Exception {
        listener.consumir(mensagem("{nao e json"), channel);

        verificarDeadLetter();
        verify(useCase, never()).processar(any());
    }

    @Test
    void mandaPayloadNuloParaDeadLetter() throws Exception {
        listener.consumir(mensagem("null"), channel);

        verificarDeadLetter();
    }

    @Test
    void mandaEventoSemIdentificadorParaDeadLetter() throws Exception {
        listener.consumir(mensagem("{\"nomeIdoso\":\"Maria\"}"), channel);

        verificarDeadLetter();
        verify(useCase, never()).processar(any());
    }

    @Test
    void mandaViolacaoDeConstraintParaDeadLetter() throws Exception {
        when(useCase.processar(any())).thenThrow(new DataIntegrityViolationException("constraint"));

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verificarDeadLetter();
    }

    @Test
    void mandaFalhaInesperadaParaDeadLetterEmVezDeDeixarSemAck() throws Exception {
        when(useCase.processar(any())).thenThrow(new IllegalStateException("bug"));

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verificarDeadLetter();
    }

    @Test
    void reenfileiraQuandoBancoEstaInacessivel() throws Exception {
        when(useCase.processar(any())).thenThrow(new CannotGetJdbcConnectionException("sem conexão"));

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verificarReenfileirada();
    }

    @Test
    void reenfileiraQuandoTransacaoNaoPodeSerAberta() throws Exception {
        when(useCase.processar(any())).thenThrow(new CannotCreateTransactionException("banco fora"));

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verificarReenfileirada();
    }

    @Test
    void reenfileiraEmTimeout() throws Exception {
        when(useCase.processar(any())).thenThrow(new QueryTimeoutException("timeout"));

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verificarReenfileirada();
    }

    @Test
    void reenfileiraQuandoCausaRaizEhTransitoria() throws Exception {
        when(useCase.processar(any())).thenThrow(
                new IllegalStateException("embrulho", new java.sql.SQLTransientConnectionException("conexão")));

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verificarReenfileirada();
    }

    @Test
    void naoTrataSqlExceptionGenericaComoTransitoria() throws Exception {
        when(useCase.processar(any())).thenThrow(new IllegalStateException("embrulho", new SQLException("erro")));

        listener.consumir(mensagem(EVENTO_VALIDO), channel);

        verificarDeadLetter();
    }

    private void verificarDeadLetter() throws Exception {
        verify(channel).basicNack(TAG, false, false);
        verifyNoMoreInteractions(channel);
    }

    private void verificarReenfileirada() throws Exception {
        verify(channel).basicNack(TAG, false, true);
        verifyNoMoreInteractions(channel);
    }

    private static Message mensagem(String corpo) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setDeliveryTag(TAG);
        propriedades.setMessageId("msg-1");
        return new Message(corpo.getBytes(StandardCharsets.UTF_8), propriedades);
    }
}
