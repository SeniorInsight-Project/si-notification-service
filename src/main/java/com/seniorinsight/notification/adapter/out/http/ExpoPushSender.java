package com.seniorinsight.notification.adapter.out.http;

import java.util.List;
import java.util.Map;

import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.port.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Envio pelo Expo Push: prioridade alta e canal {@code sos} no Android. */
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    static final String CANAL_SOS = "sos";

    record RespostaExpo(List<Ticket> data) {
    }

    record Ticket(String status, String message, Map<String, Object> details) {
    }

    private final RestClient restClient;

    public ExpoPushSender(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ResultadoPush enviarSos(String token, Notificacao notificacao) {
        if (!tokenExpo(token)) {
            log.warn("Token de push fora do formato do Expo; envio ignorado.");
            return ResultadoPush.TOKEN_INVALIDO;
        }
        Map<String, Object> corpo = Map.of(
                "to", token,
                "title", notificacao.titulo(),
                "body", notificacao.mensagem(),
                "priority", "high",
                "channelId", CANAL_SOS,
                "sound", "default",
                "data", Map.of(
                        "tipo", notificacao.tipo(),
                        "notificacaoId", notificacao.id().toString(),
                        "idosoId", notificacao.idosoId().toString()));
        try {
            RespostaExpo resposta = restClient.post().body(corpo).retrieve().body(RespostaExpo.class);
            return interpretar(resposta);
        } catch (RestClientException exception) {
            log.error("Falha ao chamar o Expo Push.", exception);
            return ResultadoPush.FALHA;
        }
    }

    static ResultadoPush interpretar(RespostaExpo resposta) {
        if (resposta == null || resposta.data() == null || resposta.data().isEmpty()) {
            return ResultadoPush.FALHA;
        }
        Ticket ticket = resposta.data().get(0);
        if ("ok".equals(ticket.status())) {
            return ResultadoPush.ENVIADO;
        }
        boolean naoRegistrado = ticket.details() != null && "DeviceNotRegistered".equals(ticket.details().get("error"));
        log.warn("Expo recusou o push: {}", ticket.message());
        return naoRegistrado ? ResultadoPush.TOKEN_INVALIDO : ResultadoPush.FALHA;
    }

    static boolean tokenExpo(String token) {
        return token != null && (token.startsWith("ExponentPushToken[") || token.startsWith("ExpoPushToken["));
    }
}
