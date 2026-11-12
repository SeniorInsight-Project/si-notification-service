package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.EventoSos;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import org.junit.jupiter.api.Test;

class ProcessarEventoSosUseCaseTest {

    @Test
    void registraEventoUmaUnicaVez() {
        Set<UUID> eventosRegistrados = new HashSet<>();
        Set<Notificacao> notificacoes = new HashSet<>();
        NotificacaoRepository repository = new NotificacaoRepository() {
            @Override
            public boolean registrarSeAusente(Notificacao notificacao) {
                if (!eventosRegistrados.add(notificacao.idEvento())) {
                    return false;
                }
                notificacoes.add(notificacao);
                return true;
            }

            @Override
            public Optional<Notificacao> buscarPorEventoId(UUID eventoId) {
                return Optional.empty();
            }

            @Override
            public Optional<Notificacao> buscarPorId(UUID id) {
                return Optional.empty();
            }

            @Override
            public void removerDoIdoso(UUID idosoId) {
            }
        };
        ProcessarEventoSosUseCase useCase = new ProcessarEventoSosUseCase(repository);
        EventoSos evento = eventoValido();

        assertTrue(useCase.processar(evento));
        assertFalse(useCase.processar(evento));
        assertEquals(1, notificacoes.size());
        assertEquals(evento.idEvento(), notificacoes.iterator().next().idEvento());
    }

    @Test
    void rejeitaEventoSemIdentificador() {
        ProcessarEventoSosUseCase useCase = new ProcessarEventoSosUseCase(mock(NotificacaoRepository.class));
        EventoSos evento = new EventoSos(null, UUID.randomUUID(), "Idoso", null, null, null, null, Instant.now());

        assertThrows(IllegalArgumentException.class, () -> useCase.processar(evento));
    }

    private EventoSos eventoValido() {
        return new EventoSos(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pessoa",
                null,
                null,
                null,
                null,
                Instant.parse("2026-10-26T12:00:00Z")
        );
    }
}
