package com.seniorinsight.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.CanalEntrega;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.EventoSinal;
import com.seniorinsight.notification.domain.model.Notificacao;
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

/** Processa alertas técnicos de sinal; a entrega é adiada durante o período de silêncio. */
public class ProcessarEventoSinalUseCase {

    private static final LocalTime INICIO_SILENCIO = LocalTime.of(22, 0);
    private static final LocalTime FIM_SILENCIO = LocalTime.of(7, 0);
    private static final int LIMITE_AGENDADAS = 100;

    private final NotificacaoRepository notificacoes;
    private final RedeProtecaoClient rede;
    private final DispositivoPushRepository dispositivos;
    private final EntregaRepository entregas;
    private final PushSender pushSender;
    private final EmailSender emailSender;
    private final AlertaRepository alertas;
    private final Clock clock;
    private final ZoneId fusoHorario;

    public ProcessarEventoSinalUseCase(
            NotificacaoRepository notificacoes,
            RedeProtecaoClient rede,
            DispositivoPushRepository dispositivos,
            EntregaRepository entregas,
            PushSender pushSender,
            EmailSender emailSender,
            AlertaRepository alertas,
            Clock clock,
            ZoneId fusoHorario) {
        this.notificacoes = notificacoes;
        this.rede = rede;
        this.dispositivos = dispositivos;
        this.entregas = entregas;
        this.pushSender = pushSender;
        this.emailSender = emailSender;
        this.alertas = alertas;
        this.clock = clock;
        this.fusoHorario = fusoHorario;
    }

    /**
     * Registra o evento pelo id e entrega o aviso aos administradores. Evento já registrado não gera outro aviso: se
     * a entrega do primeiro falhou, o agendamento pendente dele é retomado por {@link #entregarAgendadas()}.
     *
     * @return {@code false} quando o evento já tinha sido registrado
     */
    public boolean processar(EventoSinal evento) {
        boolean perda = evento.tipo() == EventoSinal.Tipo.PERDIDO;
        UUID eventoId = evento.idEvento();
        UUID idosoId = evento.idosoId();
        Instant agora = clock.instant();
        Instant ocorrido = evento.ocorridoEm();
        Notificacao notificacao = new Notificacao(
                UUID.randomUUID(), eventoId, "TECNICO", idosoId,
                perda ? "Sinal do dispositivo perdido" : "Sinal do dispositivo restabelecido",
                perda ? "O relógio do idoso está sem sinal." : "O sinal do relógio do idoso foi restabelecido.",
                ocorrido, dentroDoSilencio(agora) ? proximoHorarioDeEntrega(agora) : agora);

        if (!notificacoes.registrarSeAusente(notificacao)) {
            return false;
        }
        Notificacao persistida = notificacoes.buscarPorEventoId(eventoId).orElseThrow();
        if (!persistida.programadaPara().isAfter(agora)) {
            entregar(persistida);
            notificacoes.marcarAgendamentoProcessado(persistida.id(), clock.instant());
        }
        return true;
    }

    public int entregarAgendadas() {
        List<Notificacao> pendentes = notificacoes.buscarAgendadasAte(clock.instant(), LIMITE_AGENDADAS);
        for (Notificacao notificacao : pendentes) {
            entregar(notificacao);
            notificacoes.marcarAgendamentoProcessado(notificacao.id(), clock.instant());
        }
        return pendentes.size();
    }

    private void entregar(Notificacao notificacao) {
        alertas.registrarSeAusente(Alerta.novo(notificacao.idEvento(), notificacao.idosoId(), TipoAlerta.TECNICO,
                notificacao.titulo(), notificacao.mensagem(), notificacao.ocorridoEm()));

        for (Destinatario destinatario : rede.listarRede(notificacao.idosoId()).stream()
                .filter(Destinatario::administrador)
                .toList()) {
            if (destinatario.usuarioId() == null) {
                enviarEmailReserva(notificacao, destinatario);
                continue;
            }
            Entrega entregaPush = Entrega.pendente(notificacao.id(), destinatario.usuarioId(), CanalEntrega.PUSH);
            if (!entregas.registrarSeAusente(entregaPush)) {
                continue;
            }
            boolean enviado = false;
            for (DispositivoPush dispositivo : dispositivos.listarAtivos(destinatario.usuarioId())) {
                ResultadoPush resultado = pushSender.enviarAlerta(dispositivo.token(), notificacao);
                if (resultado == ResultadoPush.ENVIADO) {
                    enviado = true;
                } else if (resultado == ResultadoPush.TOKEN_INVALIDO) {
                    dispositivos.desativarPorToken(dispositivo.token());
                }
            }
            entregas.atualizarStatus(entregaPush, enviado ? StatusEntrega.ENVIADA : StatusEntrega.FALHA,
                    enviado ? clock.instant() : null);
            if (!enviado) {
                enviarEmailReserva(notificacao, destinatario);
            }
        }
    }

    private void enviarEmailReserva(Notificacao notificacao, Destinatario destinatario) {
        if (destinatario.email() == null || destinatario.email().isBlank()) {
            return;
        }
        if (destinatario.usuarioId() == null) {
            emailSender.enviar(destinatario.email(), notificacao.titulo(), notificacao.mensagem());
            return;
        }
        Entrega entregaEmail = Entrega.pendente(notificacao.id(), destinatario.usuarioId(), CanalEntrega.EMAIL);
        if (!entregas.registrarSeAusente(entregaEmail)) {
            return;
        }
        boolean enviado = emailSender.enviar(destinatario.email(), notificacao.titulo(), notificacao.mensagem());
        entregas.atualizarStatus(entregaEmail, enviado ? StatusEntrega.ENVIADA : StatusEntrega.FALHA,
                enviado ? clock.instant() : null);
    }

    private boolean dentroDoSilencio(Instant instante) {
        LocalTime hora = instante.atZone(fusoHorario).toLocalTime();
        return !hora.isBefore(INICIO_SILENCIO) || hora.isBefore(FIM_SILENCIO);
    }

    private Instant proximoHorarioDeEntrega(Instant instante) {
        var dataHora = instante.atZone(fusoHorario);
        var dataEntrega = dataHora.toLocalTime().isBefore(FIM_SILENCIO)
                ? dataHora.toLocalDate()
                : dataHora.toLocalDate().plusDays(1);
        return dataEntrega.atTime(FIM_SILENCIO).atZone(fusoHorario).toInstant();
    }
}
