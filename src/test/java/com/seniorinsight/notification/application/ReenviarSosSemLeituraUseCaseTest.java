package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.CanalEntrega;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.model.StatusEntrega;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import org.junit.jupiter.api.Test;

class ReenviarSosSemLeituraUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T12:01:00Z");

    private final EntregaRepository entregas = mock(EntregaRepository.class);
    private final NotificacaoRepository notificacoes = mock(NotificacaoRepository.class);
    private final DispositivoPushRepository dispositivos = mock(DispositivoPushRepository.class);
    private final RedeProtecaoClient rede = mock(RedeProtecaoClient.class);
    private final PushSender pushSender = mock(PushSender.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final ReenviarSosSemLeituraUseCase useCase = new ReenviarSosSemLeituraUseCase(
            entregas, notificacoes, dispositivos, rede, pushSender, emailSender,
            Duration.ofSeconds(60), Clock.fixed(AGORA, ZoneOffset.UTC));

    private final UUID familiar = UUID.randomUUID();
    private final UUID idosoId = UUID.randomUUID();
    private final Notificacao notificacao = new Notificacao(
            UUID.randomUUID(), UUID.randomUUID(), "SOS", idosoId, "Alerta de emergência", "SOS de Maria.", AGORA);
    private final Entrega semLeitura = new Entrega(
            UUID.randomUUID(), notificacao.id(), familiar, CanalEntrega.PUSH, StatusEntrega.ENVIADA,
            AGORA.minusSeconds(61));

    @Test
    void buscaSoEntregasEnviadasHaMaisDoPrazo() {
        when(entregas.reivindicarSemLeitura(AGORA.minusSeconds(60), 100)).thenReturn(List.of());

        assertEquals(0, useCase.executar());

        verify(entregas).reivindicarSemLeitura(AGORA.minusSeconds(60), 100);
        verify(emailSender, never()).enviar(any(), any(), any());
    }

    @Test
    void reenviaOPushEMandaEmailQuemNaoLeu() {
        when(entregas.reivindicarSemLeitura(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(semLeitura));
        when(notificacoes.buscarPorId(notificacao.id())).thenReturn(Optional.of(notificacao));
        when(dispositivos.listarAtivos(familiar)).thenReturn(
                List.of(new DispositivoPush(familiar, "a", "ExponentPushToken[a]", Plataforma.IOS, AGORA)));
        when(entregas.registrarSeAusente(any())).thenReturn(true);
        when(rede.listarRede(idosoId)).thenReturn(List.of(new Destinatario(familiar, "Ana", "ana@email.com")));
        when(emailSender.enviar(eq("ana@email.com"), any(), any())).thenReturn(true);

        assertEquals(1, useCase.executar());

        verify(pushSender).enviarSos("ExponentPushToken[a]", notificacao);
        verify(emailSender).enviar(eq("ana@email.com"), eq("Alerta de emergência"), any());
        verify(entregas).atualizarStatus(any(Entrega.class), eq(StatusEntrega.ENVIADA), eq(AGORA));
    }

    @Test
    void registraFalhaDoEmailQuandoFamiliarNaoTemEmail() {
        when(entregas.reivindicarSemLeitura(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(semLeitura));
        when(notificacoes.buscarPorId(notificacao.id())).thenReturn(Optional.of(notificacao));
        when(dispositivos.listarAtivos(familiar)).thenReturn(List.of());
        when(entregas.registrarSeAusente(any())).thenReturn(true);
        when(rede.listarRede(idosoId)).thenReturn(List.of(new Destinatario(familiar, "Ana", null)));

        useCase.executar();

        verify(emailSender, never()).enviar(any(), any(), any());
        verify(entregas).atualizarStatus(any(Entrega.class), eq(StatusEntrega.FALHA), eq(null));
    }

    @Test
    void umaEntregaComErroNaoImpedeAsDemais() {
        Entrega outra = new Entrega(UUID.randomUUID(), notificacao.id(), UUID.randomUUID(), CanalEntrega.PUSH,
                StatusEntrega.ENVIADA, AGORA.minusSeconds(70));
        when(entregas.reivindicarSemLeitura(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(semLeitura, outra));
        when(notificacoes.buscarPorId(notificacao.id())).thenReturn(Optional.of(notificacao));
        when(dispositivos.listarAtivos(familiar)).thenThrow(new IllegalStateException("banco fora"));
        when(dispositivos.listarAtivos(outra.destinatarioId())).thenReturn(
                List.of(new DispositivoPush(outra.destinatarioId(), "b", "ExponentPushToken[b]", Plataforma.ANDROID, AGORA)));
        when(pushSender.enviarSos(any(), any())).thenReturn(ResultadoPush.ENVIADO);

        assertEquals(2, useCase.executar());

        verify(pushSender).enviarSos("ExponentPushToken[b]", notificacao);
    }
}
