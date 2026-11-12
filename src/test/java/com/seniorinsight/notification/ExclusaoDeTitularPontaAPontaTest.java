package com.seniorinsight.notification;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.notification.application.RegistrarTokenPushUseCase;
import com.seniorinsight.notification.application.TitularExcluidoException;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.PushSender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;

/** Do evento no RabbitMQ até a confirmação na exchange, com PostgreSQL e RabbitMQ de verdade e o care simulado. */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/teste",
        "notification.exclusao.aviso-rede.intervalo=50ms",
        "notification.exclusao.processamento.intervalo=50ms",
        "notification.outbox.relay.intervalo=100ms"})
class ExclusaoDeTitularPontaAPontaTest extends PostgresIntegracaoTest {

    private static final String EXCHANGE = "si.events";
    private static final String FILA_CONFIRMACOES = "teste.confirmacoes";
    private static final Duration ESPERA = Duration.ofSeconds(20);

    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3-alpine");
    private static final HttpServer CARE = iniciarCareSimulado();
    private static final Map<UUID, AtomicInteger> CHAMADAS_REDE_EXCLUSAO = new ConcurrentHashMap<>();
    private static final UUID MEMBRO = UUID.randomUUID();

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void conectar(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.host", RABBIT::getHost);
        registro.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registro.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registro.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registro.add("notification.care.base-url", () -> "http://localhost:" + CARE.getAddress().getPort());
    }

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean PushSender pushSender;
    @MockitoBean EmailSender emailSender;

    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired JdbcClient jdbc;
    @Autowired RegistrarTokenPushUseCase registrarToken;
    private final ObjectMapper mapper = new ObjectMapper();

    private final UUID protocolo = UUID.randomUUID();
    private final UUID idoso = UUID.randomUUID();
    private final UUID sub = UUID.randomUUID();

    private static HttpServer iniciarCareSimulado() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress(0), 0);
            servidor.createContext("/internal/exclusoes/", CareSimulado::rede);
            servidor.createContext("/internal/idosos/", troca -> responder(troca, 200,
                    "[{\"usuarioId\":\"%s\",\"nomeCompleto\":\"Ana Lima\",\"email\":\"ana@exemplo.com\"}]".formatted(MEMBRO)));
            servidor.start();
            return servidor;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CareSimulado {

        /** O care responde 404 na primeira consulta de cada protocolo e depois 200, como quando ainda processa o pedido. */
        static void rede(HttpExchange troca) throws IOException {
            String[] partes = troca.getRequestURI().getPath().split("/");
            UUID protocolo = UUID.fromString(partes[3]);
            int chamada = CHAMADAS_REDE_EXCLUSAO.computeIfAbsent(protocolo, id -> new AtomicInteger()).incrementAndGet();
            if (chamada == 1) {
                responder(troca, 404, "{}");
            } else {
                responder(troca, 200, ("{\"protocolo\":\"%s\",\"primeiroNomeIdoso\":\"Sebastiana\","
                        + "\"membros\":[{\"usuarioId\":\"%s\",\"nomeCompleto\":\"Ana Lima\",\"email\":\"ana@exemplo.com\"}]}")
                        .formatted(protocolo, MEMBRO));
            }
        }
    }

    private static void responder(HttpExchange troca, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    @BeforeEach
    void prepararFilaDeConfirmacoes() {
        admin.purgeQueue("notification.dlq", false);
        Queue fila = new Queue(FILA_CONFIRMACOES, false, false, true);
        admin.declareQueue(fila);
        admin.declareBinding(BindingBuilder.bind(fila).to(new TopicExchange(EXCHANGE)).with("lgpd.erasure.completed"));
        admin.purgeQueue(FILA_CONFIRMACOES, false);
    }

    private String envelopePedido(UUID protocoloDoPedido, String tipoTitular, String source) {
        return """
                {"specversion":"1.0","id":"%s","source":"%s","type":"lgpd.erasure.requested",
                 "time":"2026-11-12T15:30:00Z","datacontenttype":"application/json",
                 "dataschema":"https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.requested.v1.json",
                 "subject":"%s",
                 "data":{"protocolo":"%s","tipoTitular":"%s","titularId":"%s","titularSub":"%s"}}
                """.formatted(UUID.randomUUID(), source, protocoloDoPedido, protocoloDoPedido, tipoTitular, idoso, sub);
    }

    private void publicar(String routingKey, String json) {
        Message mensagem = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(UUID.randomUUID().toString())
                .build();
        rabbit.send(EXCHANGE, routingKey, mensagem);
    }

    private JsonNode esperarConfirmacao() throws Exception {
        Message[] recebida = new Message[1];
        await().atMost(ESPERA).until(() -> (recebida[0] = rabbit.receive(FILA_CONFIRMACOES)) != null);
        return mapper.readTree(recebida[0].getBody());
    }

    private long contar(String tabela, String coluna, UUID valor) {
        return jdbc.sql("SELECT count(*) FROM notification." + tabela + " WHERE " + coluna + " = :v")
                .param("v", valor).query(Long.class).single();
    }

    private UUID semear() {
        UUID notificacao = UUID.randomUUID();
        jdbc.sql("INSERT INTO notification.dispositivo_push (usuario_id, identificador_aparelho, token, plataforma) "
                + "VALUES (:sub, 'pixel', 'ExponentPushToken[x]', 'ANDROID')").param("sub", sub).update();
        jdbc.sql("INSERT INTO notification.notificacao (id, evento_id, tipo, idoso_id, titulo, mensagem, ocorrido_em) "
                + "VALUES (:id, :evento, 'SOS', :idoso, 't', 'm', now())")
                .param("id", notificacao).param("evento", UUID.randomUUID()).param("idoso", idoso).update();
        jdbc.sql("INSERT INTO notification.entrega (notificacao_id, destinatario_id, canal) VALUES (:n, :d, 'PUSH')")
                .param("n", notificacao).param("d", sub).update();
        jdbc.sql("INSERT INTO notification.entrega (notificacao_id, destinatario_id, canal) VALUES (:n, :d, 'PUSH')")
                .param("n", notificacao).param("d", MEMBRO).update();
        jdbc.sql("INSERT INTO notification.alerta (id, origem_id, idoso_id, tipo, titulo, gerado_em) "
                + "VALUES (:id, :origem, :idoso, 'SOS', 't', now())")
                .param("id", UUID.randomUUID()).param("origem", UUID.randomUUID()).param("idoso", idoso).update();
        return notificacao;
    }

    @Test
    void idosoAvisaARedeDepoisDoCareProcessarApagaTudoEConfirmaUmaVez() throws Exception {
        semear();
        String pedido = envelopePedido(protocolo, "IDOSO", "/report-service");

        publicar("lgpd.erasure.requested", pedido);
        publicar("lgpd.erasure.requested", pedido); // reentrega do mesmo protocolo

        JsonNode confirmacao = esperarConfirmacao();
        assertEquals("lgpd.erasure.completed", confirmacao.get("type").asText());
        assertEquals("/notification-service", confirmacao.get("source").asText());
        assertEquals(protocolo.toString(), confirmacao.get("subject").asText());
        assertEquals("notification-service", confirmacao.get("data").get("servico").asText());

        String texto = "Sebastiana pediu a exclusão da conta no SeniorInsight. Você não vai mais receber avisos sobre ela.";
        verify(emailSender, timeout(5000)).enviar("ana@exemplo.com", "Exclusão de conta", texto);
        assertEquals(2, CHAMADAS_REDE_EXCLUSAO.get(protocolo).get(), "404 e depois 200");
        assertEquals(0, contar("dispositivo_push", "usuario_id", sub));
        assertEquals(0, contar("entrega", "destinatario_id", sub));
        assertEquals(0, contar("notificacao", "idoso_id", idoso));
        assertEquals(0, contar("alerta", "idoso_id", idoso));
        assertThrows(TitularExcluidoException.class,
                () -> registrarToken.registrar(sub, "pixel", "ExponentPushToken[y]", Plataforma.ANDROID));

        Thread.sleep(1500); // dá tempo de uma segunda confirmação indevida aparecer
        assertNull(rabbit.receive(FILA_CONFIRMACOES), "a reentrega não pode confirmar de novo");
        verify(emailSender, org.mockito.Mockito.times(1)).enviar(eq("ana@exemplo.com"), anyString(), anyString());
    }

    @Test
    void familiarApagaSoOsDadosDeleSemAvisoEConfirma() throws Exception {
        UUID notificacao = semear();

        publicar("lgpd.erasure.requested", envelopePedido(protocolo, "FAMILIAR", "/report-service"));

        assertEquals(protocolo.toString(), esperarConfirmacao().get("subject").asText());
        assertEquals(0, contar("dispositivo_push", "usuario_id", sub));
        assertEquals(0, contar("entrega", "destinatario_id", sub));
        assertEquals(1, contar("entrega", "notificacao_id", notificacao), "sobra só a entrega do outro destinatário");
        assertEquals(1, contar("notificacao", "id", notificacao));
        assertEquals(1, contar("alerta", "idoso_id", idoso));
        verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
        assertNull(CHAMADAS_REDE_EXCLUSAO.get(protocolo));
    }

    @Test
    void envelopeInvalidoVaiParaANotificationDlq() {
        publicar("lgpd.erasure.requested", envelopePedido(protocolo, "IDOSO", "/care-service"));

        Message[] morta = new Message[1];
        await().atMost(ESPERA).until(() -> (morta[0] = rabbit.receive("notification.dlq")) != null);
        assertNotNull(morta[0]);
        assertEquals(0, contar("exclusao_protocolo", "protocolo", protocolo));
    }

    @Test
    void sosUpdatedContinuaGerandoNotificacao() {
        UUID alarme = UUID.randomUUID();
        String sos = """
                {"specversion":"1.0","id":"%s","source":"/care-service","type":"sos.updated","subject":"%s",
                 "data":{"alarmeId":"%s","idosoId":"%s","nomeIdoso":"Maria","status":"DISPARADO",
                 "acionadoEm":"2026-11-12T15:00:00Z","atualizadoEm":"2026-11-12T15:00:00Z"}}
                """.formatted(UUID.randomUUID(), alarme, alarme, idoso);

        publicar("sos.updated", sos);

        await().atMost(ESPERA).until(() -> contar("notificacao", "evento_id", alarme) == 1
                && contar("alerta", "origem_id", alarme) == 1);
        assertEquals(Instant.parse("2026-11-12T15:00:00Z").getEpochSecond(),
                jdbc.sql("SELECT extract(epoch FROM ocorrido_em)::bigint FROM notification.notificacao WHERE evento_id = :e")
                        .param("e", alarme).query(Long.class).single());
    }
}
