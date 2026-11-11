package com.seniorinsight.notification.application;

import java.util.UUID;

import com.seniorinsight.notification.domain.port.DispositivoPushRepository;

public class RemoverTokenPushUseCase {

    private final DispositivoPushRepository repository;

    public RemoverTokenPushUseCase(DispositivoPushRepository repository) {
        this.repository = repository;
    }

    /** @throws TokenPushNaoEncontradoException se o usuário não tem token para o aparelho. */
    public void remover(UUID usuarioId, String identificadorAparelho) {
        if (!repository.remover(usuarioId, identificadorAparelho)) {
            throw new TokenPushNaoEncontradoException(identificadorAparelho);
        }
    }
}
