package com.seniorinsight.notification.application;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.TitularExcluidoRepository;
import com.seniorinsight.notification.domain.port.Transacao;

public class RegistrarTokenPushUseCase {

    private final DispositivoPushRepository repository;
    private final TitularExcluidoRepository titulares;
    private final Transacao transacao;

    public RegistrarTokenPushUseCase(
            DispositivoPushRepository repository, TitularExcluidoRepository titulares, Transacao transacao) {
        this.repository = repository;
        this.titulares = titulares;
        this.transacao = transacao;
    }

    /**
     * Idempotente por aparelho: repetir a chamada com outro token substitui o anterior.
     *
     * @throws TitularExcluidoException se o usuário já pediu a exclusão da conta.
     */
    public DispositivoPush registrar(UUID usuarioId, String identificadorAparelho, String token, Plataforma plataforma) {
        DispositivoPush dispositivo = new DispositivoPush(usuarioId, identificadorAparelho, token, plataforma, Instant.now());
        return transacao.obter(() -> {
            titulares.travar(usuarioId);
            if (titulares.existe(usuarioId)) {
                throw new TitularExcluidoException();
            }
            return repository.salvar(dispositivo);
        });
    }
}
