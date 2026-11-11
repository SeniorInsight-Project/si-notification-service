package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.LeituraNotificacao;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import org.junit.jupiter.api.Test;

class ConfirmarLeituraNotificacaoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T12:00:00Z");

    private final EntregaRepository entregas = mock(EntregaRepository.class);
    private final ConfirmarLeituraNotificacaoUseCase useCase =
            new ConfirmarLeituraNotificacaoUseCase(entregas, Clock.fixed(AGORA, ZoneOffset.UTC));
    private final UUID usuario = UUID.randomUUID();
    private final UUID notificacao = UUID.randomUUID();

    @Test
    void primeiraConfirmacaoGravaOInstanteAtual() {
        when(entregas.confirmarLeitura(notificacao, usuario, AGORA)).thenReturn(Optional.of(AGORA));

        assertEquals(new LeituraNotificacao(notificacao, AGORA), useCase.confirmar(usuario, notificacao));
    }

    @Test
    void confirmarDeNovoDevolveOInstanteOriginal() {
        Instant primeira = Instant.parse("2026-11-10T08:00:00Z");
        when(entregas.confirmarLeitura(notificacao, usuario, AGORA)).thenReturn(Optional.of(primeira));

        assertEquals(primeira, useCase.confirmar(usuario, notificacao).lidaEm());
        assertEquals(primeira, useCase.confirmar(usuario, notificacao).lidaEm());
    }

    @Test
    void notificacaoInexistenteOuDeOutroUsuarioFalhaComNaoEncontrada() {
        when(entregas.confirmarLeitura(notificacao, usuario, AGORA)).thenReturn(Optional.empty());

        assertThrows(NotificacaoNaoEncontradaException.class, () -> useCase.confirmar(usuario, notificacao));
    }
}
