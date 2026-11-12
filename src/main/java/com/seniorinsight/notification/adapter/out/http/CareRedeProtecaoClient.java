package com.seniorinsight.notification.adapter.out.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.RedeExclusao;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import com.seniorinsight.notification.domain.port.RedeProtecaoIndisponivelException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Busca a rede no care-service por {@code GET /internal/idosos/{id}/rede}, com cache em memória, e a rede de uma
 * exclusão por {@code GET /internal/exclusoes/{protocolo}/rede}, sem cache.
 */
public class CareRedeProtecaoClient implements RedeProtecaoClient {

    private record Entrada(List<Destinatario> rede, Instant expiraEm) {
    }

    record MembroRede(UUID usuarioId, String nomeCompleto, String email) {

        Destinatario paraDestinatario() {
            return new Destinatario(usuarioId, nomeCompleto, email);
        }
    }

    record RespostaExclusaoRede(String primeiroNomeIdoso, List<MembroRede> membros) {
    }

    private final RestClient restClient;
    private final Duration ttlCache;
    private final Clock clock;
    private final Map<UUID, Entrada> cache = new ConcurrentHashMap<>();

    public CareRedeProtecaoClient(RestClient restClient, Duration ttlCache, Clock clock) {
        this.restClient = restClient;
        this.ttlCache = ttlCache;
        this.clock = clock;
    }

    @Override
    public List<Destinatario> listarRede(UUID idosoId) {
        Entrada entrada = cache.get(idosoId);
        if (entrada != null && entrada.expiraEm().isAfter(clock.instant())) {
            return entrada.rede();
        }
        try {
            List<MembroRede> membros = restClient.get()
                    .uri("/internal/idosos/{id}/rede", idosoId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            List<Destinatario> rede = (membros == null ? List.<MembroRede>of() : membros).stream()
                    .map(MembroRede::paraDestinatario)
                    .toList();
            cache.put(idosoId, new Entrada(rede, clock.instant().plus(ttlCache)));
            return rede;
        } catch (RestClientException exception) {
            throw new RedeProtecaoIndisponivelException("Não foi possível obter a rede do idoso " + idosoId, exception);
        }
    }

    @Override
    public Optional<RedeExclusao> buscarRedeDaExclusao(UUID protocolo) {
        try {
            RespostaExclusaoRede resposta = restClient.get()
                    .uri("/internal/exclusoes/{protocolo}/rede", protocolo)
                    .retrieve()
                    .body(RespostaExclusaoRede.class);
            return Optional.ofNullable(resposta).map(CareRedeProtecaoClient::paraRedeExclusao);
        } catch (HttpClientErrorException.NotFound naoProcessado) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new RedeProtecaoIndisponivelException(
                    "Não foi possível obter a rede da exclusão " + protocolo, exception);
        }
    }

    private static RedeExclusao paraRedeExclusao(RespostaExclusaoRede resposta) {
        List<MembroRede> membros = resposta.membros() == null ? List.of() : resposta.membros();
        return new RedeExclusao(resposta.primeiroNomeIdoso(), membros.stream().map(MembroRede::paraDestinatario).toList());
    }
}
