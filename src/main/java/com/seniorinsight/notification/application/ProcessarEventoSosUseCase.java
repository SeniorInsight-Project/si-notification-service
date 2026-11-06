package com.seniorinsight.notification.application;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.EventoSos;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;

public class ProcessarEventoSosUseCase {

    private final NotificacaoRepository notificacaoRepository;

    public ProcessarEventoSosUseCase(NotificacaoRepository notificacaoRepository) {
        this.notificacaoRepository = notificacaoRepository;
    }

    public boolean processar(EventoSos evento) {
        if (evento.idEvento() == null || evento.idosoId() == null) {
            throw new IllegalArgumentException("O evento SOS deve informar alarmeId e idosoId.");
        }

        String nomeIdoso = evento.nomeIdoso() == null || evento.nomeIdoso().isBlank()
                ? "Idoso"
                : evento.nomeIdoso().trim();
        Instant ocorridoEm = evento.ocorridoEm() == null ? Instant.now() : evento.ocorridoEm();
        Notificacao notificacao = new Notificacao(
                UUID.randomUUID(),
                evento.idEvento(),
                "SOS",
                evento.idosoId(),
                "Alerta de emergência",
                "O botão SOS foi acionado por " + nomeIdoso + ".",
                ocorridoEm
        );
        return notificacaoRepository.registrarSeAusente(notificacao);
    }
}
