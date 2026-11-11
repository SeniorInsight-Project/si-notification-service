package com.seniorinsight.notification.application;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;

public class RegistrarTokenPushUseCase {

    private final DispositivoPushRepository repository;

    public RegistrarTokenPushUseCase(DispositivoPushRepository repository) {
        this.repository = repository;
    }

    /** Idempotente por aparelho: repetir a chamada com outro token substitui o anterior. */
    public DispositivoPush registrar(UUID usuarioId, String identificadorAparelho, String token, Plataforma plataforma) {
        return repository.salvar(new DispositivoPush(usuarioId, identificadorAparelho, token, plataforma, Instant.now()));
    }
}
