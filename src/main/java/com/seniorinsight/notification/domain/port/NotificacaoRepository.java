package com.seniorinsight.notification.domain.port;

import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Notificacao;

public interface NotificacaoRepository {

    boolean registrarSeAusente(Notificacao notificacao);

    Optional<Notificacao> buscarPorEventoId(UUID eventoId);

    Optional<Notificacao> buscarPorId(UUID id);

    /** Apaga as notificações do idoso; as entregas delas saem em cascata. */
    void removerDoIdoso(UUID idosoId);
}
