package com.seniorinsight.notification.adapter.out.rabbit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.seniorinsight.notification.domain.port.Transacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publica na exchange {@code si.events} os eventos pendentes da {@code outbox_evento}, com o tipo do evento como
 * routing key. Cada execução trava um lote ({@code FOR UPDATE SKIP LOCKED}, então réplicas não enviam o mesmo evento
 * juntas), envia, espera os publisher confirms e só então marca {@code publicado_em}. Sem confirmação, o evento fica
 * pendente e {@code tentativas} aumenta. A entrega é "pelo menos uma vez": o consumidor deduplica pelo
 * {@code message_id}, que é o id da linha.
 */
@Component
public class OutboxRelay {

    static final String APP_ID = "notification-service";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private record Pendente(UUID id, UUID agregadoId, String tipoEvento, String payload) {
    }

    private record Envio(Pendente evento, CorrelationData confirmacao) {
    }

    private final JdbcClient jdbc;
    private final Transacao transacao;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final int tamanhoLote;
    private final Duration timeoutConfirmacao;

    public OutboxRelay(
            JdbcClient jdbc,
            Transacao transacao,
            RabbitTemplate rabbitTemplate,
            @Value("${notification.rabbitmq.exchange}") String exchange,
            @Value("${notification.outbox.relay.tamanho-lote}") int tamanhoLote,
            @Value("${notification.outbox.relay.timeout-confirmacao}") Duration timeoutConfirmacao) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.tamanhoLote = tamanhoLote;
        this.timeoutConfirmacao = timeoutConfirmacao;
    }

    @Scheduled(fixedDelayString = "${notification.outbox.relay.intervalo}")
    public void publicarPendentes() {
        try {
            transacao.executar(this::publicarLote);
        } catch (RuntimeException exception) {
            log.error("Falha ao publicar os eventos pendentes da outbox.", exception);
        }
    }

    private void publicarLote() {
        List<Pendente> pendentes = travarPendentes();
        long prazo = System.nanoTime() + timeoutConfirmacao.toNanos();
        for (Envio envio : enviar(pendentes)) {
            aguardarConfirmacao(envio, prazo);
        }
    }

    private List<Pendente> travarPendentes() {
        return jdbc.sql("""
                SELECT id, agregado_id, tipo_evento, payload::text AS payload
                FROM notification.outbox_evento
                WHERE publicado_em IS NULL
                ORDER BY criado_em
                LIMIT :limite
                FOR UPDATE SKIP LOCKED
                """)
                .param("limite", tamanhoLote)
                .query((rs, linha) -> new Pendente(
                        rs.getObject("id", UUID.class),
                        rs.getObject("agregado_id", UUID.class),
                        rs.getString("tipo_evento"),
                        rs.getString("payload")))
                .list();
    }

    private List<Envio> enviar(List<Pendente> pendentes) {
        List<Envio> enviados = new ArrayList<>(pendentes.size());
        for (Pendente evento : pendentes) {
            CorrelationData confirmacao = new CorrelationData(evento.id().toString());
            try {
                rabbitTemplate.send(exchange, evento.tipoEvento(), paraMensagem(evento), confirmacao);
                enviados.add(new Envio(evento, confirmacao));
            } catch (AmqpException exception) {
                // Falha de conexão: o resto do lote fica pendente para o próximo ciclo.
                registrarFalha(evento, exception.getMessage());
                break;
            }
        }
        return enviados;
    }

    private void aguardarConfirmacao(Envio envio, long prazo) {
        try {
            long restante = Math.max(0, prazo - System.nanoTime());
            CorrelationData.Confirm confirm = envio.confirmacao().getFuture().get(restante, TimeUnit.NANOSECONDS);
            if (confirm.isAck()) {
                marcarPublicado(envio.evento());
            } else {
                registrarFalha(envio.evento(), "nack do broker: " + confirm.getReason());
            }
        } catch (TimeoutException exception) {
            registrarFalha(envio.evento(), "sem confirmação do broker em " + timeoutConfirmacao);
        } catch (ExecutionException exception) {
            registrarFalha(envio.evento(), exception.getCause().getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            registrarFalha(envio.evento(), "relay interrompido");
        }
    }

    private void marcarPublicado(Pendente evento) {
        jdbc.sql("UPDATE notification.outbox_evento SET publicado_em = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", evento.id())
                .update();
    }

    private void registrarFalha(Pendente evento, String motivo) {
        jdbc.sql("UPDATE notification.outbox_evento SET tentativas = tentativas + 1 WHERE id = :id")
                .param("id", evento.id())
                .update();
        log.warn("Outbox: falha ao publicar o evento {} ({}): {}", evento.id(), evento.tipoEvento(), motivo);
    }

    private static Message paraMensagem(Pendente evento) {
        return MessageBuilder.withBody(evento.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(evento.id().toString())
                .setType(evento.tipoEvento())
                .setAppId(APP_ID)
                .setHeader("agregado_id", evento.agregadoId().toString())
                .build();
    }
}
