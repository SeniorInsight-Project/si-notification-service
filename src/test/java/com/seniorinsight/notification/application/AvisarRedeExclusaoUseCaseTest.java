package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.model.RedeExclusao;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import com.seniorinsight.notification.domain.port.RedeProtecaoIndisponivelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AvisarRedeExclusaoUseCaseTest {

    private static final String TEXTO =
            "Sebastiana pediu a exclusão da conta no SeniorInsight. Você não vai mais receber avisos sobre ela.";
    private static final int TENTATIVAS = 3;

    private final UUID protocolo = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final RedeProtecaoClient rede = mock(RedeProtecaoClient.class);
    private final DispositivoPushRepository dispositivos = mock(DispositivoPushRepository.class);
    private final PushSender push = mock(PushSender.class);
    private final EmailSender email = mock(EmailSender.class);
    private final AvisarRedeExclusaoUseCase useCase = new AvisarRedeExclusaoUseCase(
            rede, dispositivos, push, email, new Retentativa(TENTATIVAS, Duration.ZERO, intervalo -> {
    }));
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AvisarRedeExclusaoUseCase.class);

    @BeforeEach
    void capturarLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(logs);
    }

    private RedeExclusao redeComAnaEBruno() {
        return new RedeExclusao("Sebastiana", List.of(
                new Destinatario(ana, "Ana Lima", "ana@exemplo.com"),
                new Destinatario(bruno, "Bruno Dias", "bruno@exemplo.com")));
    }

    private DispositivoPush aparelho(UUID usuario, String token) {
        return new DispositivoPush(usuario, "aparelho-" + token, token, Plataforma.ANDROID, Instant.now());
    }

    private long errosLogados() {
        return logs.list.stream().filter(evento -> evento.getLevel() == Level.ERROR).count();
    }

    @Test
    void avisaCadaMembroPorPushEEmail() {
        when(rede.buscarRedeDaExclusao(protocolo)).thenReturn(Optional.of(redeComAnaEBruno()));
        when(dispositivos.listarAtivos(ana)).thenReturn(List.of(aparelho(ana, "t-ana-1"), aparelho(ana, "t-ana-2")));
        when(dispositivos.listarAtivos(bruno)).thenReturn(List.of(aparelho(bruno, "t-bruno")));
        when(push.enviarAviso(anyString(), anyString(), anyString())).thenReturn(ResultadoPush.ENVIADO);

        useCase.avisar(protocolo);

        verify(push).enviarAviso("t-ana-1", "Exclusão de conta", TEXTO);
        verify(push).enviarAviso("t-ana-2", "Exclusão de conta", TEXTO);
        verify(push).enviarAviso("t-bruno", "Exclusão de conta", TEXTO);
        verify(email).enviar("ana@exemplo.com", "Exclusão de conta", TEXTO);
        verify(email).enviar("bruno@exemplo.com", "Exclusão de conta", TEXTO);
        assertEquals(0, errosLogados());
    }

    @Test
    void tentaDeNovoQuandoOCareAindaNaoProcessou() {
        when(rede.buscarRedeDaExclusao(protocolo))
                .thenReturn(Optional.empty(), Optional.of(redeComAnaEBruno()));
        when(dispositivos.listarAtivos(any())).thenReturn(List.of());

        useCase.avisar(protocolo);

        verify(rede, times(2)).buscarRedeDaExclusao(protocolo);
        verify(email).enviar("ana@exemplo.com", "Exclusão de conta", TEXTO);
        verify(email).enviar("bruno@exemplo.com", "Exclusão de conta", TEXTO);
    }

    @Test
    void tentaDeNovoQuandoOCareEstaForaDoAr() {
        when(rede.buscarRedeDaExclusao(protocolo))
                .thenThrow(new RedeProtecaoIndisponivelException("care fora", null))
                .thenReturn(Optional.of(redeComAnaEBruno()));
        when(dispositivos.listarAtivos(any())).thenReturn(List.of());

        useCase.avisar(protocolo);

        verify(email).enviar("ana@exemplo.com", "Exclusão de conta", TEXTO);
    }

    @Test
    void seOLimiteEstourarNaoAvisaNinguemRegistraErroENaoLancaExcecao() {
        when(rede.buscarRedeDaExclusao(protocolo)).thenReturn(Optional.empty());

        useCase.avisar(protocolo);

        verify(rede, times(TENTATIVAS)).buscarRedeDaExclusao(protocolo);
        verify(email, never()).enviar(anyString(), anyString(), anyString());
        verify(push, never()).enviarAviso(anyString(), anyString(), anyString());
        assertEquals(1, errosLogados());
        assertTrue(logs.list.get(0).getFormattedMessage().contains(protocolo.toString()));
    }

    @Test
    void seOCareContinuarForaDoArNaoLancaExcecaoERegistraErro() {
        when(rede.buscarRedeDaExclusao(protocolo)).thenThrow(new RedeProtecaoIndisponivelException("care fora", null));

        useCase.avisar(protocolo);

        verify(rede, times(TENTATIVAS)).buscarRedeDaExclusao(protocolo);
        assertEquals(1, errosLogados());
    }

    @Test
    void falhaNoAvisoDeUmMembroNaoImpedeOsOutros() {
        when(rede.buscarRedeDaExclusao(protocolo)).thenReturn(Optional.of(redeComAnaEBruno()));
        when(dispositivos.listarAtivos(ana)).thenThrow(new IllegalStateException("banco"));
        when(dispositivos.listarAtivos(bruno)).thenReturn(List.of());

        useCase.avisar(protocolo);

        verify(email).enviar("bruno@exemplo.com", "Exclusão de conta", TEXTO);
        assertEquals(1, errosLogados());
    }

    @Test
    void membroSemContaRecebeSoOEmailESemEmailRecebeSoOPush() {
        UUID semEmail = UUID.randomUUID();
        when(rede.buscarRedeDaExclusao(protocolo)).thenReturn(Optional.of(new RedeExclusao("Sebastiana", List.of(
                new Destinatario(null, "Carla", "carla@exemplo.com"),
                new Destinatario(semEmail, "Davi", " ")))));
        when(dispositivos.listarAtivos(semEmail)).thenReturn(List.of(aparelho(semEmail, "t-davi")));
        when(push.enviarAviso(anyString(), anyString(), anyString())).thenReturn(ResultadoPush.ENVIADO);

        useCase.avisar(protocolo);

        verify(email).enviar("carla@exemplo.com", "Exclusão de conta", TEXTO);
        verify(email, never()).enviar(" ", "Exclusão de conta", TEXTO);
        verify(push).enviarAviso("t-davi", "Exclusão de conta", TEXTO);
    }

    @Test
    void desativaOTokenRecusadoPeloProvedor() {
        when(rede.buscarRedeDaExclusao(protocolo)).thenReturn(Optional.of(redeComAnaEBruno()));
        when(dispositivos.listarAtivos(ana)).thenReturn(List.of(aparelho(ana, "t-velho")));
        when(dispositivos.listarAtivos(bruno)).thenReturn(List.of());
        when(push.enviarAviso("t-velho", "Exclusão de conta", TEXTO)).thenReturn(ResultadoPush.TOKEN_INVALIDO);

        useCase.avisar(protocolo);

        verify(dispositivos).desativarPorToken("t-velho");
    }
}
