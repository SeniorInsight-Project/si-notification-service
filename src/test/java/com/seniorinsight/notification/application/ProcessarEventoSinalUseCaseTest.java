package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.CanalEntrega;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.EventoSinal;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.model.StatusEntrega;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;

class ProcessarEventoSinalUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T12:00:00Z");
    private static final UUID EVENTO_ID = UUID.randomUUID();
    private static final UUID IDOSO_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID APOIO_ID = UUID.randomUUID();

    private final NotificacaoRepository notificacoes = mock(NotificacaoRepository.class);
    private final RedeProtecaoClient rede = mock(RedeProtecaoClient.class);
    private final DispositivoPushRepository dispositivos = mock(DispositivoPushRepository.class);
    private final EntregaRepository entregas = mock(EntregaRepository.class);
    private final PushSender pushSender = mock(PushSender.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final AlertaRepository alertas = mock(AlertaRepository.class);
    private Notificacao persistida;

    @BeforeEach
    void preparar() {
        when(notificacoes.registrarSeAusente(any())).thenAnswer(invocacao -> {
            persistida = invocacao.getArgument(0);
            return true;
        });
        when(notificacoes.buscarPorEventoId(EVENTO_ID)).thenAnswer(invocacao -> Optional.ofNullable(persistida));
        when(entregas.registrarSeAusente(any())).thenReturn(true);
        when(rede.listarRede(IDOSO_ID)).thenReturn(List.of(
                new Destinatario(ADMIN_ID, "Ana", "ana@example.com", "ADMINISTRADOR"),
                new Destinatario(APOIO_ID, "Bia", "bia@example.com", "APOIO")));
    }

    private ProcessarEventoSinalUseCase casoDeUso(Instant instante) {
        return new ProcessarEventoSinalUseCase(notificacoes, rede, dispositivos, entregas, pushSender,
                emailSender, alertas, Clock.fixed(instante, ZoneOffset.UTC), ZoneId.of("America/Sao_Paulo"));
    }

    private static EventoSinal perda(Instant ocorridoEm) {
        return new EventoSinal(EVENTO_ID, EventoSinal.Tipo.PERDIDO, IDOSO_ID, ocorridoEm);
    }

    private static EventoSinal retorno(Instant ocorridoEm) {
        return new EventoSinal(EVENTO_ID, EventoSinal.Tipo.RECUPERADO, IDOSO_ID, ocorridoEm);
    }

    @Test
    void perdaDeSinalEnviaPushSomenteAoAdministrador() {
        DispositivoPush aparelho = new DispositivoPush(
                ADMIN_ID, "celular", "ExponentPushToken[abc]", Plataforma.ANDROID, AGORA);
        when(dispositivos.listarAtivos(ADMIN_ID)).thenReturn(List.of(aparelho));
        when(pushSender.enviarAlerta(eq(aparelho.token()), any())).thenReturn(ResultadoPush.ENVIADO);

        casoDeUso(AGORA).processar(perda(AGORA));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertas).registrarSeAusente(captor.capture());
        assertEquals(TipoAlerta.TECNICO, captor.getValue().tipo());
        verify(pushSender).enviarAlerta(eq(aparelho.token()), any());
        verify(dispositivos, never()).listarAtivos(APOIO_ID);
        verify(emailSender, never()).enviar(any(), any(), any());
        verify(entregas).registrarSeAusente(ArgumentMatchers.argThat(
                entrega -> entrega.canal() == CanalEntrega.PUSH && entrega.destinatarioId().equals(ADMIN_ID)));
    }

    @Test
    void periodoDeSilencioPersisteEntregaParaAsSeteSemEnviarNaMadrugada() {
        Instant umaHoraLocal = Instant.parse("2026-11-11T04:00:00Z");

        casoDeUso(umaHoraLocal).processar(perda(umaHoraLocal));

        assertEquals(Instant.parse("2026-11-11T10:00:00Z"), persistida.programadaPara());
        verify(rede, never()).listarRede(IDOSO_ID);
        verify(notificacoes, never()).marcarAgendamentoProcessado(any(), any());
    }

    @Test
    void entregaAgendadaUsaEmailDeReservaEMarcaProcessamento() {
        Instant seteLocal = Instant.parse("2026-11-11T10:00:00Z");
        Notificacao agendada = new Notificacao(UUID.randomUUID(), EVENTO_ID, "TECNICO", IDOSO_ID,
                "Sinal do dispositivo restabelecido", "O sinal foi restabelecido.", seteLocal, seteLocal);
        when(notificacoes.buscarAgendadasAte(any(), eq(100))).thenReturn(List.of(agendada));
        when(dispositivos.listarAtivos(ADMIN_ID)).thenReturn(List.of());
        when(emailSender.enviar(any(), any(), any())).thenReturn(true);

        assertEquals(1, casoDeUso(seteLocal).entregarAgendadas());

        verify(emailSender).enviar("ana@example.com", agendada.titulo(), agendada.mensagem());
        verify(notificacoes).marcarAgendamentoProcessado(agendada.id(), seteLocal);
        verify(entregas).atualizarStatus(any(Entrega.class), eq(StatusEntrega.ENVIADA), eq(seteLocal));
    }

    @Test
    void administradorSemContaRecebeEmailDeReserva() {
        when(rede.listarRede(IDOSO_ID)).thenReturn(List.of(
                new Destinatario(null, "Ana", "ana@example.com", "ADMINISTRADOR")));
        when(emailSender.enviar(any(), any(), any())).thenReturn(true);

        casoDeUso(AGORA).processar(retorno(AGORA));

        verify(emailSender).enviar("ana@example.com", persistida.titulo(), persistida.mensagem());
        verify(dispositivos, never()).listarAtivos(any());
    }

    @Test
    void retornoGeraUmAvisoDeRestabelecimento() {
        when(emailSender.enviar(any(), any(), any())).thenReturn(true);
        when(dispositivos.listarAtivos(ADMIN_ID)).thenReturn(List.of());

        assertTrue(casoDeUso(AGORA).processar(retorno(AGORA)));

        assertEquals("Sinal do dispositivo restabelecido", persistida.titulo());
        verify(alertas).registrarSeAusente(any(Alerta.class));
        verify(emailSender).enviar(eq("ana@example.com"), eq(persistida.titulo()), any());
    }

    @Test
    void eventoRepetidoNaoGeraAvisoDuplicado() {
        when(notificacoes.registrarSeAusente(any())).thenReturn(false);

        assertFalse(casoDeUso(AGORA).processar(perda(AGORA)));

        verify(alertas, never()).registrarSeAusente(any());
        verify(rede, never()).listarRede(any());
        verify(pushSender, never()).enviarAlerta(any(), any());
        verify(emailSender, never()).enviar(any(), any(), any());
        verify(notificacoes, never()).marcarAgendamentoProcessado(any(), any());
    }
}
