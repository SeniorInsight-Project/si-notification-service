package com.seniorinsight.notification.adapter.out.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import com.seniorinsight.notification.domain.port.RedeProtecaoIndisponivelException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Busca a rede no care-service por {@code GET /internal/idosos/{id}/rede}, com cache em memória. */
public class CareRedeProtecaoClient implements RedeProtecaoClient {

    private record Entrada(List<Destinatario> rede, Instant expiraEm) {
    }

    record MembroRede(UUID usuarioId, String nomeCompleto, String email) {
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
                    .map(membro -> new Destinatario(membro.usuarioId(), membro.nomeCompleto(), membro.email()))
                    .toList();
            cache.put(idosoId, new Entrada(rede, clock.instant().plus(ttlCache)));
            return rede;
        } catch (RestClientException exception) {
            throw new RedeProtecaoIndisponivelException("Não foi possível obter a rede do idoso " + idosoId, exception);
        }
    }
}
