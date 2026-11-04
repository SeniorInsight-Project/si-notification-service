package com.seniorinsight.notification;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.seniorinsight.notification.adapter.out.rabbit.DeadLetterQueueMonitor;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fluxo ponta a ponta com a aplicação inteira, RabbitMQ e Postgres reais: publicação na exchange
 * si.events, consumo pela fila notification.q, gravação no banco e dead-letter feito pelo broker.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "notification.rabbitmq.retry-delay=100ms",
                // O teste chama o monitor diretamente; o agendamento não deve interferir.
                "notification.rabbitmq.dead-letter-check-interval-ms=3600000"
        })
@Testcontainers
class FluxoSosIT {

    private static final Duration ESPERA = Duration.ofSeconds(20);
    private static final String IDOSO = "1a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            // Diretório de dados em tmpfs: evita erro de permissão no .erlang.cookie em alguns hosts Docker.
            .withTmpFs(Map.of("/var/lib/rabbitmq", "rw,mode=1777"));

    @Value("${notification.rabbitmq.exchange}")
    private String exchange;

    @Value("${notification.rabbitmq.routing-key}")
    private String routingKey;

    @Value("${notification.rabbitmq.queue}")
    private String fila;

    @Value("${notification.rabbitmq.dead-letter-queue}")
    private String filaDeadLetter;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private DeadLetterQueueMonitor monitor;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TestRestTemplate http;

    @MockitoSpyBean
    private NotificacaoRepository repository;

    @BeforeEach
    void limparFilas() {
        amqpAdmin.purgeQueue(filaDeadLetter, false);
    }

    @Test
    void sosPublicadoViraNotificacaoComOContexto() {
        UUID alarme = UUID.randomUUID();

        publicar(routingKey, """
                {"alarmeId":"%s","idosoId":"%s","nomeIdoso":"Maria","latitude":-23.55,"longitude":-46.63,
                 "nivelBateria":42,"observacao":"caí na cozinha","acionadoEm":"2026-11-04T12:00:00Z"}
                """.formatted(alarme, IDOSO));

        await().atMost(ESPERA).until(() -> contarNotificacoes(alarme) == 1);
        Map<String, Object> linha = jdbcClient
                .sql("SELECT * FROM notification.notificacao WHERE evento_id = ?")
                .param(alarme)
                .query()
                .singleRow();
        assertEquals("SOS", linha.get("tipo"));
        assertEquals(UUID.fromString(IDOSO), linha.get("idoso_id"));
        assertEquals("Alerta de emergência", linha.get("titulo"));
        assertEquals("O botão SOS foi acionado por Maria.", linha.get("mensagem"));
        assertEquals(-23.55, linha.get("latitude"));
        assertEquals(-46.63, linha.get("longitude"));
        assertEquals(42, ((Number) linha.get("nivel_bateria")).intValue());
        assertEquals("caí na cozinha", linha.get("observacao"));
        assertEquals(Instant.parse("2026-11-04T12:00:00Z"), ocorridoEm(alarme));
        await().atMost(ESPERA).until(() -> mensagensProntas(fila) == 0);
        assertNull(rabbitTemplate.receive(filaDeadLetter));
    }

    @Test
    void eventoReentregueGeraUmaUnicaNotificacao() {
        UUID alarme = UUID.randomUUID();
        String evento = eventoMinimo(alarme);

        publicar(routingKey, evento);
        publicar(routingKey, evento);
        publicar(routingKey, evento);

        verify(repository, timeout(ESPERA.toMillis()).times(3))
                .registrarSeAusente(argThat(n -> n.idEvento().equals(alarme)));
        assertEquals(1, contarNotificacoes(alarme));
        assertNull(rabbitTemplate.receive(filaDeadLetter));
    }

    @Test
    void jsonInvalidoVaiParaADeadLetterPeloBroker() {
        String corpo = "{isto nao e json";

        publicar(routingKey, corpo);

        Message morta = rabbitTemplate.receive(filaDeadLetter, ESPERA.toMillis());
        assertNotNull(morta, "a mensagem deveria ter chegado na dead-letter");
        assertEquals(corpo, new String(morta.getBody(), StandardCharsets.UTF_8));
        List<Map<String, ?>> xDeath = morta.getMessageProperties().getXDeathHeader();
        assertNotNull(xDeath, "o broker deve registrar o motivo no cabeçalho x-death");
        assertEquals("rejected", xDeath.get(0).get("reason"));
        assertEquals(fila, xDeath.get(0).get("queue"));
    }

    @Test
    void eventoSemIdentificadoresVaiParaADeadLetter() {
        publicar(routingKey, "{\"nomeIdoso\":\"Maria\"}");

        assertNotNull(rabbitTemplate.receive(filaDeadLetter, ESPERA.toMillis()));
    }

    @Test
    void falhaPermanenteDoBancoVaiParaADeadLetter() {
        UUID alarme = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("constraint"))
                .when(repository).registrarSeAusente(any());

        publicar(routingKey, eventoMinimo(alarme));

        assertNotNull(rabbitTemplate.receive(filaDeadLetter, ESPERA.toMillis()));
        assertEquals(0, contarNotificacoes(alarme));
    }

    @Test
    void falhaTransitoriaReentregaAMensagemEOSosEhGravado() {
        UUID alarme = UUID.randomUUID();
        doThrow(new CannotGetJdbcConnectionException("banco fora do ar"))
                .doThrow(new CannotGetJdbcConnectionException("ainda fora do ar"))
                .doCallRealMethod()
                .when(repository).registrarSeAusente(any());

        publicar(routingKey, eventoMinimo(alarme));

        await().atMost(ESPERA).until(() -> contarNotificacoes(alarme) == 1);
        verify(repository, atLeast(3)).registrarSeAusente(argThat(n -> n.idEvento().equals(alarme)));
        assertNull(rabbitTemplate.receive(filaDeadLetter), "falha transitória não pode ir para a dead-letter");
    }

    @Test
    void eventoComOutraRoutingKeyNaoEhConsumido() {
        UUID ignorado = UUID.randomUUID();
        UUID marcador = UUID.randomUUID();

        publicar("queda.detectada", eventoMinimo(ignorado));
        publicar(routingKey, eventoMinimo(marcador));

        await().atMost(ESPERA).until(() -> contarNotificacoes(marcador) == 1);
        assertEquals(0, contarNotificacoes(ignorado));
    }

    @Test
    void monitorPublicaQuantasMensagensEstaoNaDeadLetter() {
        publicar(routingKey, "nao-json-1");
        publicar(routingKey, "nao-json-2");
        await().atMost(ESPERA).until(() -> mensagensProntas(filaDeadLetter) == 2);

        monitor.verificar();

        assertEquals(2.0, meterRegistry.get("notification.dlq.mensagens").gauge().value());
    }

    @Test
    void healthCheckFicaUpComBancoERabbitDisponiveis() {
        ResponseEntity<String> resposta = http.getForEntity("/actuator/health", String.class);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("{\"status\":\"UP\"}", resposta.getBody());
    }

    private void publicar(String chave, String corpo) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setMessageId(UUID.randomUUID().toString());
        rabbitTemplate.send(exchange, chave, new Message(corpo.getBytes(StandardCharsets.UTF_8), propriedades));
    }

    private static String eventoMinimo(UUID alarme) {
        return "{\"alarmeId\":\"%s\",\"idosoId\":\"%s\",\"nomeIdoso\":\"Ana\"}".formatted(alarme, IDOSO);
    }

    private int contarNotificacoes(UUID alarme) {
        return jdbcClient.sql("SELECT count(*) FROM notification.notificacao WHERE evento_id = ?")
                .param(alarme)
                .query(Integer.class)
                .single();
    }

    private Instant ocorridoEm(UUID alarme) {
        return jdbcClient.sql("SELECT ocorrido_em FROM notification.notificacao WHERE evento_id = ?")
                .param(alarme)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    private long mensagensProntas(String nomeDaFila) {
        var informacao = amqpAdmin.getQueueInfo(nomeDaFila);
        return informacao == null ? -1 : informacao.getMessageCount();
    }
}
