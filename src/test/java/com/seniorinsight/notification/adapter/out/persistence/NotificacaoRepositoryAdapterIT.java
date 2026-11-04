package com.seniorinsight.notification.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.seniorinsight.notification.domain.model.Localizacao;
import com.seniorinsight.notification.domain.model.Notificacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Roda as migrations do Flyway num Postgres real e valida o SQL do repositório,
 * inclusive a idempotência do ON CONFLICT.
 */
@JdbcTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificacaoRepositoryAdapter.class)
class NotificacaoRepositoryAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificacaoRepositoryAdapter repository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void registraNotificacaoComContextoDoSos() {
        Notificacao notificacao = notificacao(UUID.randomUUID(), new Localizacao(-23.55, -46.63), 42, "caí");

        assertTrue(repository.registrarSeAusente(notificacao));

        Map<String, Object> linha = jdbcClient
                .sql("SELECT * FROM notification.notificacao WHERE evento_id = ?")
                .param(notificacao.idEvento())
                .query()
                .singleRow();
        assertEquals(notificacao.id(), linha.get("id"));
        assertEquals("O botão SOS foi acionado por Maria.", linha.get("mensagem"));
        assertEquals(-23.55, linha.get("latitude"));
        assertEquals(-46.63, linha.get("longitude"));
        assertEquals(42, ((Number) linha.get("nivel_bateria")).intValue());
        assertEquals("caí", linha.get("observacao"));
        assertEquals(notificacao.ocorridoEm(),
                jdbcClient.sql("SELECT ocorrido_em FROM notification.notificacao WHERE id = ?")
                        .param(notificacao.id())
                        .query(OffsetDateTime.class)
                        .single()
                        .toInstant());
    }

    @Test
    void ignoraEventoJaRegistrado() {
        UUID idEvento = UUID.randomUUID();

        assertTrue(repository.registrarSeAusente(notificacao(idEvento, null, null, null)));
        assertFalse(repository.registrarSeAusente(notificacao(idEvento, null, null, null)));

        assertEquals(1, jdbcClient.sql("SELECT count(*) FROM notification.notificacao WHERE evento_id = ?")
                .param(idEvento)
                .query(Integer.class)
                .single());
    }

    @Test
    void aceitaContextoAusente() {
        Notificacao notificacao = notificacao(UUID.randomUUID(), null, null, null);

        assertTrue(repository.registrarSeAusente(notificacao));

        Map<String, Object> linha = jdbcClient
                .sql("SELECT latitude, longitude, nivel_bateria, observacao FROM notification.notificacao WHERE id = ?")
                .param(notificacao.id())
                .query()
                .singleRow();
        assertNull(linha.get("latitude"));
        assertNull(linha.get("longitude"));
        assertNull(linha.get("nivel_bateria"));
        assertNull(linha.get("observacao"));
    }

    @Test
    void aplicaTodasAsMigrations() {
        assertEquals(List.of("1", "2"), jdbcClient
                // A linha sem versão é o registro "<< Flyway Schema Creation >>" do próprio Flyway.
                .sql("""
                        SELECT version FROM notification.flyway_schema_history
                        WHERE success AND version IS NOT NULL
                        ORDER BY installed_rank
                        """)
                .query(String.class)
                .list());
    }

    /**
     * Duas entregas simultâneas do mesmo evento (ex.: duas réplicas consumindo uma reentrega) devem
     * gerar uma única notificação. Roda fora da transação do teste para cada thread usar a sua.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registraUmaUnicaVezComEntregasConcorrentes() throws Exception {
        UUID idEvento = UUID.randomUUID();
        int entregas = 8;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(entregas);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < entregas; i++) {
                resultados.add(executor.submit(() -> {
                    largada.await();
                    return repository.registrarSeAusente(notificacao(idEvento, null, null, null));
                }));
            }
            largada.countDown();

            long inseridas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(30, TimeUnit.SECONDS)) {
                    inseridas++;
                }
            }
            assertEquals(1, inseridas);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, jdbcClient.sql("SELECT count(*) FROM notification.notificacao WHERE evento_id = ?")
                .param(idEvento)
                .query(Integer.class)
                .single());
    }

    /** As constraints da V2 são a última barreira caso algum produtor escreva direto no banco. */
    @ParameterizedTest(name = "rejeita {0}")
    @CsvSource(delimiter = '|', textBlock = """
            latitude sem longitude       | -23.5 |       |
            longitude sem latitude       |       | -46.6 |
            latitude fora da faixa       | 90.5  | 0     |
            longitude fora da faixa      | 0     | -181  |
            bateria acima de 100         |       |       | 101
            bateria negativa             |       |       | -1
            """)
    void bancoRejeitaContextoInvalido(String caso, Double latitude, Double longitude, Integer nivelBateria) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcClient.sql("""
                        INSERT INTO notification.notificacao
                            (evento_id, tipo, idoso_id, titulo, mensagem, ocorrido_em, latitude, longitude, nivel_bateria)
                        VALUES (:evento, 'SOS', :idoso, 't', 'm', now(), :lat, :lon, :bat)
                        """)
                .param("evento", UUID.randomUUID())
                .param("idoso", UUID.randomUUID())
                .param("lat", latitude, Types.DOUBLE)
                .param("lon", longitude, Types.DOUBLE)
                .param("bat", nivelBateria, Types.INTEGER)
                .update());
    }

    private static Notificacao notificacao(UUID idEvento, Localizacao localizacao, Integer nivelBateria,
                                           String observacao) {
        return new Notificacao(UUID.randomUUID(), idEvento, "SOS", UUID.randomUUID(), "Alerta de emergência",
                "O botão SOS foi acionado por Maria.", Instant.parse("2026-10-26T12:00:00.123456Z"),
                localizacao, nivelBateria, observacao);
    }
}
