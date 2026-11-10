package com.seniorinsight.notification.adapter.out.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExpoPushSenderTest {

    private static final String URL = "https://exp.host/--/api/v2/push/send";
    private static final String TOKEN = "ExponentPushToken[abc]";

    private final Notificacao notificacao = new Notificacao(UUID.randomUUID(), UUID.randomUUID(), "SOS",
            UUID.randomUUID(), "Alerta de emergência", "SOS de Maria.", Instant.now());
    private final RestClient.Builder builder = RestClient.builder().baseUrl(URL);
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ExpoPushSender sender = new ExpoPushSender(builder.build());

    @Test
    void enviaComPrioridadeAltaECanalSos() {
        servidor.expect(requestTo(URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.to").value(TOKEN))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.channelId").value("sos"))
                .andExpect(jsonPath("$.data.notificacaoId").value(notificacao.id().toString()))
                .andRespond(withSuccess("{\"data\":[{\"status\":\"ok\",\"id\":\"1\"}]}", MediaType.APPLICATION_JSON));

        assertEquals(ResultadoPush.ENVIADO, sender.enviarSos(TOKEN, notificacao));
        servidor.verify();
    }

    @Test
    void tokenNaoRegistradoViraTokenInvalido() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"data\":[{\"status\":\"error\",\"message\":\"x\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}",
                MediaType.APPLICATION_JSON));

        assertEquals(ResultadoPush.TOKEN_INVALIDO, sender.enviarSos(TOKEN, notificacao));
    }

    @Test
    void erroDoProvedorEFalha() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertEquals(ResultadoPush.FALHA, sender.enviarSos(TOKEN, notificacao));
    }

    @Test
    void tokenForaDoFormatoExpoNaoChamaOProvedor() {
        assertEquals(ResultadoPush.TOKEN_INVALIDO, sender.enviarSos("fcm-token-cru", notificacao));
        servidor.verify();
    }
}
