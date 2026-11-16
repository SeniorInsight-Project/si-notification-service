package com.seniorinsight.notification.adapter.out.rabbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import com.seniorinsight.notification.ConfiguracaoDeIntegracao;
import com.seniorinsight.notification.PostgresIntegracaoTest;
import com.seniorinsight.notification.adapter.out.persistence.OutboxPublicadorAdapter;
import com.seniorinsight.notification.adapter.out.persistence.TransacaoSpring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OutboxPublicadorAdapter.class, TransacaoSpring.class, ConfiguracaoDeIntegracao.class})
class OutboxRelayIntegracaoTest extends PostgresIntegracaoTest {

    private static final String EXCHANGE = "si.events";

    @Autowired JdbcClient jdbc;
    @Autowired OutboxPublicadorAdapter publicador;
    @Autowired TransacaoSpring transacao;

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private OutboxRelay relay;
    private final UUID protocolo = UUID.randomUUID();

    @BeforeEach
    void prepararUmEventoPendente() {
        jdbc.sql("TRUNCATE notification.outbox_evento").update();
        relay = new OutboxRelay(jdbc, transacao, rabbit, EXCHANGE, 100, Duration.ofSeconds(1));
        transacao.executar(() -> publicador.publicar(protocolo));
    }

    private void broker(boolean ack) {
        doAnswer(invocacao -> {
            CorrelationData correlacao = invocacao.getArgument(3);
            correlacao.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "recusado"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private int tentativas() {
        return jdbc.sql("SELECT tentativas FROM notification.outbox_evento").query(Integer.class).single();
    }

    private boolean publicado() {
        return jdbc.sql("SELECT publicado_em IS NOT NULL FROM notification.outbox_evento").query(Boolean.class).single();
    }

    @Test
    void publicaOEventoPendenteNaExchangeComARoutingKeyDoTipoEMarcaPublicado() {
        broker(true);

        relay.publicarPendentes();

        ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(EXCHANGE), eq("lgpd.erasure.completed"), mensagem.capture(), any(CorrelationData.class));
        String corpo = new String(mensagem.getValue().getBody(), StandardCharsets.UTF_8);
        assertTrue(corpo.replace(" ", "").contains("\"subject\":\"" + protocolo + "\""));
        String idDoEnvelope = jdbc.sql("SELECT id::text FROM notification.outbox_evento").query(String.class).single();
        assertEquals(idDoEnvelope, mensagem.getValue().getMessageProperties().getMessageId());
        assertEquals("application/json", mensagem.getValue().getMessageProperties().getContentType());
        assertTrue(publicado());
    }

    @Test
    void naoRepublicaOQueJaFoiPublicado() {
        broker(true);

        relay.publicarPendentes();
        relay.publicarPendentes();

        verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void semConfirmacaoDoBrokerOEventoContinuaPendenteEContaATentativa() {
        broker(false);

        relay.publicarPendentes();

        assertFalse(publicado());
        assertEquals(1, tentativas());
    }

    @Test
    void comOBrokerForaDoArOEventoContinuaPendente() {
        doThrow(new AmqpConnectException(new RuntimeException("sem conexão")))
                .when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.publicarPendentes();

        assertFalse(publicado());
        assertEquals(1, tentativas());
    }

    @Test
    void semPendentesNaoFalaComOBroker() {
        jdbc.sql("TRUNCATE notification.outbox_evento").update();

        relay.publicarPendentes();

        verify(rabbit, never()).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
