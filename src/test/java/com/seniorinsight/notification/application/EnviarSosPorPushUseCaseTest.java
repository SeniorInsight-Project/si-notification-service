package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.model.StatusEntrega;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EnviarSosPorPushUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");

    private final NotificacaoRepository notificacoes = mock(NotificacaoRepository.class);
    private final RedeProtecaoClient rede = mock(RedeProtecaoClient.class);
    private final DispositivoPushRepository dispositivos = mock(DispositivoPushRepository.class);
    private final EntregaRepository entregas = mock(EntregaRepository.class);
    private final PushSender pushSender = mock(PushSender.class);
    private final AlertaRepository alertas = mock(AlertaRepository.class);
    private final EnviarSosPorPushUseCase useCase = new EnviarSosPorPushUseCase(
            notificacoes, rede, dispositivos, entregas, pushSender, alertas, Clock.fixed(AGORA, ZoneOffset.UTC));

    private final UUID eventoId = UUID.randomUUID();
    private final UUID idosoId = UUID.randomUUID();
    private final Notificacao notificacao = new Notificacao(
            UUID.randomUUID(), eventoId, "SOS", idosoId, "Alerta de emergência", "SOS de Maria.", AGORA);

    private DispositivoPush aparelho(UUID usuarioId, String token) {
        return new DispositivoPush(usuarioId, "aparelho-" + token, token, Plataforma.ANDROID, AGORA);
    }

    private void quandoHaNotificacaoEEntregaNova() {
        when(notificacoes.buscarPorEventoId(eventoId)).thenReturn(Optional.of(notificacao));
        when(entregas.registrarSeAusente(any())).thenReturn(true);
    }

    @Test
    void gravaUmAlertaSosParaOEnvio() {
        quandoHaNotificacaoEEntregaNova();
        when(rede.listarRede(idosoId)).thenReturn(List.of());

        useCase.enviar(eventoId);

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertas).registrarSeAusente(captor.capture());
        Alerta alerta = captor.getValue();
        assertEquals(eventoId, alerta.origemId());
        assertEquals(idosoId, alerta.idosoId());
        assertEquals(TipoAlerta.SOS, alerta.tipo());
        assertEquals("Alerta de emergência", alerta.titulo());
        assertEquals("SOS de Maria.", alerta.detalhe());
        assertEquals(AGORA, alerta.geradoEm());
    }

    @Test
    void enviaParaFamiliaresComContaEMarcaEntregaComoEnviada() {
        UUID familiar = UUID.randomUUID();
        quandoHaNotificacaoEEntregaNova();
        when(rede.listarRede(idosoId)).thenReturn(List.of(
                new Destinatario(familiar, "Ana", "ana@email.com"),
                new Destinatario(null, "Sem conta", "sem@email.com")));
        when(dispositivos.listarAtivos(familiar)).thenReturn(List.of(aparelho(familiar, "ExponentPushToken[a]")));
        when(pushSender.enviarSos("ExponentPushToken[a]", notificacao)).thenReturn(ResultadoPush.ENVIADO);

        assertEquals(1, useCase.enviar(eventoId));

        verify(entregas).atualizarStatus(any(Entrega.class), eq(StatusEntrega.ENVIADA), eq(AGORA));
    }

    @Test
    void marcaFalhaQuandoFamiliarNaoTemAparelho() {
        UUID familiar = UUID.randomUUID();
        quandoHaNotificacaoEEntregaNova();
        when(rede.listarRede(idosoId)).thenReturn(List.of(new Destinatario(familiar, "Ana", null)));
        when(dispositivos.listarAtivos(familiar)).thenReturn(List.of());

        assertEquals(0, useCase.enviar(eventoId));

        verify(entregas).atualizarStatus(any(Entrega.class), eq(StatusEntrega.FALHA), eq(null));
        verify(pushSender, never()).enviarSos(any(), any());
    }

    @Test
    void desativaTokenQueOProvedorNaoReconhece() {
        UUID familiar = UUID.randomUUID();
        quandoHaNotificacaoEEntregaNova();
        when(rede.listarRede(idosoId)).thenReturn(List.of(new Destinatario(familiar, "Ana", null)));
        when(dispositivos.listarAtivos(familiar)).thenReturn(List.of(aparelho(familiar, "ExponentPushToken[velho]")));
        when(pushSender.enviarSos(any(), any())).thenReturn(ResultadoPush.TOKEN_INVALIDO);

        assertEquals(0, useCase.enviar(eventoId));

        verify(dispositivos).desativarPorToken("ExponentPushToken[velho]");
    }

    @Test
    void naoReenviaParaQuemJaTemEntrega() {
        UUID familiar = UUID.randomUUID();
        when(notificacoes.buscarPorEventoId(eventoId)).thenReturn(Optional.of(notificacao));
        when(entregas.registrarSeAusente(any())).thenReturn(false);
        when(rede.listarRede(idosoId)).thenReturn(List.of(new Destinatario(familiar, "Ana", null)));

        assertEquals(0, useCase.enviar(eventoId));

        verify(pushSender, never()).enviarSos(any(), any());
    }

    @Test
    void falhaQuandoANotificacaoNaoExiste() {
        when(notificacoes.buscarPorEventoId(eventoId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.enviar(eventoId));
    }
}
