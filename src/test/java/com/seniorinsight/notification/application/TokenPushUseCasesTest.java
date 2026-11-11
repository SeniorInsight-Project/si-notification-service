package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import org.junit.jupiter.api.Test;

class TokenPushUseCasesTest {

    private final DispositivoPushRepository repository = mock(DispositivoPushRepository.class);
    private final UUID usuario = UUID.randomUUID();

    @Test
    void registraAssociandoOTokenAoUsuarioEAoAparelho() {
        when(repository.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        DispositivoPush salvo = new RegistrarTokenPushUseCase(repository)
                .registrar(usuario, "pixel-7", "ExponentPushToken[x]", Plataforma.ANDROID);

        assertEquals(usuario, salvo.usuarioId());
        assertEquals("pixel-7", salvo.identificadorAparelho());
        assertEquals("ExponentPushToken[x]", salvo.token());
    }

    @Test
    void rejeitaDadosInvalidosSemTocarNoBanco() {
        RegistrarTokenPushUseCase useCase = new RegistrarTokenPushUseCase(repository);

        assertThrows(IllegalArgumentException.class, () -> useCase.registrar(usuario, " ", "t", Plataforma.IOS));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrar(usuario, "a", "", Plataforma.IOS));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrar(usuario, "a", "t", null));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.registrar(usuario, "a", "x".repeat(4097), Plataforma.IOS));
        verify(repository, never()).salvar(any());
    }

    @Test
    void removeOTokenDoAparelho() {
        when(repository.remover(usuario, "pixel-7")).thenReturn(true);

        new RemoverTokenPushUseCase(repository).remover(usuario, "pixel-7");

        verify(repository).remover(usuario, "pixel-7");
    }

    @Test
    void removerAparelhoSemTokenFalhaComNaoEncontrado() {
        when(repository.remover(usuario, "pixel-7")).thenReturn(false);

        assertThrows(TokenPushNaoEncontradoException.class,
                () -> new RemoverTokenPushUseCase(repository).remover(usuario, "pixel-7"));
    }
}
