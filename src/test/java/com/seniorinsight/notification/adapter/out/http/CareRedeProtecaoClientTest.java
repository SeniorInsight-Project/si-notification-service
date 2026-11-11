package com.seniorinsight.notification.adapter.out.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.seniorinsight.notification.domain.port.RedeProtecaoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CareRedeProtecaoClientTest {

    private final UUID idoso = UUID.randomUUID();
    private final UUID familiar = UUID.randomUUID();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://care");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

    private static final class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-11-10T12:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioMutavel relogio = new RelogioMutavel();
    private final CareRedeProtecaoClient client =
            new CareRedeProtecaoClient(builder.build(), Duration.ofMinutes(5), relogio);

    private String resposta() {
        return "[{\"familiarId\":\"%s\",\"usuarioId\":\"%s\",\"nomeCompleto\":\"Ana\",\"email\":\"ana@email.com\"}]"
                .formatted(UUID.randomUUID(), familiar);
    }

    @Test
    void usaOCacheDentroDoPrazoEConsultaDeNovoDepois() {
        servidor.expect(once(), requestTo("http://care/internal/idosos/" + idoso + "/rede"))
                .andRespond(withSuccess(resposta(), MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://care/internal/idosos/" + idoso + "/rede"))
                .andRespond(withSuccess(resposta(), MediaType.APPLICATION_JSON));

        assertEquals(familiar, client.listarRede(idoso).get(0).usuarioId());
        relogio.agora = relogio.agora.plusSeconds(299);
        client.listarRede(idoso);
        relogio.agora = relogio.agora.plusSeconds(2);
        client.listarRede(idoso);

        servidor.verify();
    }

    @Test
    void erroDoCareViraRedeIndisponivel() {
        servidor.expect(requestTo("http://care/internal/idosos/" + idoso + "/rede")).andRespond(withServerError());

        assertThrows(RedeProtecaoIndisponivelException.class, () -> client.listarRede(idoso));
    }
}
