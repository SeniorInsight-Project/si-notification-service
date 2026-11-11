package com.seniorinsight.notification.application;

import java.time.Clock;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.LeituraNotificacao;
import com.seniorinsight.notification.domain.port.EntregaRepository;

/**
 * Confirma a leitura de uma notificação pelo seu destinatário. Idempotente: a primeira confirmação define
 * {@code lidaEm} e as seguintes devolvem o mesmo instante.
 */
public class ConfirmarLeituraNotificacaoUseCase {

    private final EntregaRepository entregas;
    private final Clock clock;

    public ConfirmarLeituraNotificacaoUseCase(EntregaRepository entregas, Clock clock) {
        this.entregas = entregas;
        this.clock = clock;
    }

    /** @throws NotificacaoNaoEncontradaException se a notificação não existe ou não é do usuário. */
    public LeituraNotificacao confirmar(UUID usuarioId, UUID notificacaoId) {
        return entregas.confirmarLeitura(notificacaoId, usuarioId, clock.instant())
                .map(lidaEm -> new LeituraNotificacao(notificacaoId, lidaEm))
                .orElseThrow(NotificacaoNaoEncontradaException::new);
    }
}
