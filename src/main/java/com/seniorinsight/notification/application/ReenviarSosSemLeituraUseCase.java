package com.seniorinsight.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.CanalEntrega;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.model.StatusEntrega;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RN-002: sem leitura dentro do prazo, reenvia o push e manda e-mail ao familiar que não leu. */
public class ReenviarSosSemLeituraUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReenviarSosSemLeituraUseCase.class);
    private static final int LIMITE_POR_EXECUCAO = 100;

    private final EntregaRepository entregas;
    private final NotificacaoRepository notificacoes;
    private final DispositivoPushRepository dispositivos;
    private final RedeProtecaoClient rede;
    private final PushSender pushSender;
    private final EmailSender emailSender;
    private final Duration prazoLeitura;
    private final Clock clock;

    public ReenviarSosSemLeituraUseCase(
            EntregaRepository entregas,
            NotificacaoRepository notificacoes,
            DispositivoPushRepository dispositivos,
            RedeProtecaoClient rede,
            PushSender pushSender,
            EmailSender emailSender,
            Duration prazoLeitura,
            Clock clock) {
        this.entregas = entregas;
        this.notificacoes = notificacoes;
        this.dispositivos = dispositivos;
        this.rede = rede;
        this.pushSender = pushSender;
        this.emailSender = emailSender;
        this.prazoLeitura = prazoLeitura;
        this.clock = clock;
    }

    /** @return quantas entregas sem leitura foram tratadas. */
    public int executar() {
        var pendentes = entregas.reivindicarSemLeitura(clock.instant().minus(prazoLeitura), LIMITE_POR_EXECUCAO);
        for (Entrega entrega : pendentes) {
            try {
                notificacoes.buscarPorId(entrega.notificacaoId()).ifPresent(notificacao -> {
                    reenviarPush(entrega.destinatarioId(), notificacao);
                    enviarEmail(entrega.destinatarioId(), notificacao);
                });
            } catch (RuntimeException exception) {
                log.error("Falha ao reenviar a entrega {}; seguindo com as demais.", entrega.id(), exception);
            }
        }
        return pendentes.size();
    }

    private void reenviarPush(UUID usuarioId, Notificacao notificacao) {
        for (DispositivoPush dispositivo : dispositivos.listarAtivos(usuarioId)) {
            if (pushSender.enviarSos(dispositivo.token(), notificacao) == ResultadoPush.TOKEN_INVALIDO) {
                dispositivos.desativarPorToken(dispositivo.token());
            }
        }
    }

    private void enviarEmail(UUID usuarioId, Notificacao notificacao) {
        Entrega entrega = Entrega.pendente(notificacao.id(), usuarioId, CanalEntrega.EMAIL);
        if (!entregas.registrarSeAusente(entrega)) {
            return;
        }
        boolean enviado = false;
        try {
            enviado = rede.listarRede(notificacao.idosoId()).stream()
                    .filter(destinatario -> usuarioId.equals(destinatario.usuarioId()))
                    .map(Destinatario::email)
                    .filter(email -> email != null && !email.isBlank())
                    .findFirst()
                    .map(email -> emailSender.enviar(email, notificacao.titulo(), notificacao.mensagem()
                            + " Ninguém confirmou a leitura do alerta; abra o aplicativo SeniorInsight."))
                    .orElse(false);
        } catch (RuntimeException exception) {
            log.error("Falha ao enviar o e-mail da notificação {}.", notificacao.id(), exception);
        }
        if (enviado) {
            entregas.atualizarStatus(entrega, StatusEntrega.ENVIADA, clock.instant());
        } else {
            entregas.atualizarStatus(entrega, StatusEntrega.FALHA, null);
        }
    }
}
