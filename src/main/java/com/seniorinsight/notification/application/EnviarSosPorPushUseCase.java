package com.seniorinsight.notification.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.CanalEntrega;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.model.StatusEntrega;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;

/** Envia o push do SOS a cada familiar da rede com conta. Idempotente: quem já tem entrega não recebe de novo. */
public class EnviarSosPorPushUseCase {

    private final NotificacaoRepository notificacoes;
    private final RedeProtecaoClient rede;
    private final DispositivoPushRepository dispositivos;
    private final EntregaRepository entregas;
    private final PushSender pushSender;
    private final AlertaRepository alertas;
    private final Clock clock;

    public EnviarSosPorPushUseCase(
            NotificacaoRepository notificacoes,
            RedeProtecaoClient rede,
            DispositivoPushRepository dispositivos,
            EntregaRepository entregas,
            PushSender pushSender,
            AlertaRepository alertas,
            Clock clock) {
        this.notificacoes = notificacoes;
        this.rede = rede;
        this.dispositivos = dispositivos;
        this.entregas = entregas;
        this.pushSender = pushSender;
        this.alertas = alertas;
        this.clock = clock;
    }

    /** @return quantos familiares receberam o push. */
    public int enviar(UUID eventoId) {
        Notificacao notificacao = notificacoes.buscarPorEventoId(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Notificação do evento " + eventoId + " não encontrada."));

        // Central de alertas: um alerta por SOS, gravado de forma idempotente (reprocessar não duplica).
        alertas.registrarSeAusente(Alerta.novo(
                notificacao.idEvento(), notificacao.idosoId(), TipoAlerta.SOS,
                notificacao.titulo(), notificacao.mensagem(), notificacao.ocorridoEm()));

        List<Destinatario> destinatarios = rede.listarRede(notificacao.idosoId()).stream()
                .filter(destinatario -> destinatario.usuarioId() != null)
                .toList();

        int enviados = 0;
        for (Destinatario destinatario : destinatarios) {
            Entrega entrega = Entrega.pendente(notificacao.id(), destinatario.usuarioId(), CanalEntrega.PUSH);
            if (!entregas.registrarSeAusente(entrega)) {
                continue;
            }
            if (enviarParaAparelhos(notificacao, destinatario)) {
                entregas.atualizarStatus(entrega, StatusEntrega.ENVIADA, clock.instant());
                enviados++;
            } else {
                entregas.atualizarStatus(entrega, StatusEntrega.FALHA, null);
            }
        }
        return enviados;
    }

    private boolean enviarParaAparelhos(Notificacao notificacao, Destinatario destinatario) {
        boolean algumEnviado = false;
        for (DispositivoPush dispositivo : dispositivos.listarAtivos(destinatario.usuarioId())) {
            ResultadoPush resultado = pushSender.enviarSos(dispositivo.token(), notificacao);
            if (resultado == ResultadoPush.ENVIADO) {
                algumEnviado = true;
            } else if (resultado == ResultadoPush.TOKEN_INVALIDO) {
                dispositivos.desativarPorToken(dispositivo.token());
            }
        }
        return algumEnviado;
    }
}
