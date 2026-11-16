package com.seniorinsight.notification.application;

import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.RedeExclusao;
import com.seniorinsight.notification.domain.model.ResultadoPush;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Avisa a rede do idoso, por push e e-mail, que ele pediu a exclusão da conta. O aviso não pode travar a exclusão:
 * se o care-service não entregar a rede dentro do limite de tentativas, registra o erro e devolve sem lançar.
 */
public class AvisarRedeExclusaoUseCase {

    static final String TITULO = "Exclusão de conta";
    static final String MENSAGEM = "%s pediu a exclusão da conta no SeniorInsight. Você não vai mais receber avisos sobre ela.";
    static final String NOME_PADRAO = "Um idoso da sua rede";

    private static final Logger log = LoggerFactory.getLogger(AvisarRedeExclusaoUseCase.class);

    private final RedeProtecaoClient rede;
    private final DispositivoPushRepository dispositivos;
    private final PushSender pushSender;
    private final EmailSender emailSender;
    private final Retentativa retentativa;

    public AvisarRedeExclusaoUseCase(
            RedeProtecaoClient rede,
            DispositivoPushRepository dispositivos,
            PushSender pushSender,
            EmailSender emailSender,
            Retentativa retentativa) {
        this.rede = rede;
        this.dispositivos = dispositivos;
        this.pushSender = pushSender;
        this.emailSender = emailSender;
        this.retentativa = retentativa;
    }

    public void avisar(UUID protocolo) {
        Optional<RedeExclusao> redeDaExclusao = buscarRede(protocolo);
        if (redeDaExclusao.isEmpty()) {
            return;
        }
        String mensagem = MENSAGEM.formatted(nomeDoIdoso(redeDaExclusao.get()));
        for (Destinatario membro : redeDaExclusao.get().membros()) {
            avisarMembro(protocolo, membro, mensagem);
        }
    }

    private Optional<RedeExclusao> buscarRede(UUID protocolo) {
        try {
            Optional<RedeExclusao> resultado = retentativa.buscar(() -> rede.buscarRedeDaExclusao(protocolo));
            if (resultado.isEmpty()) {
                log.error("O care-service não entregou a rede da exclusão {} no limite de tentativas; "
                        + "a exclusão segue sem avisar a rede.", protocolo);
            }
            return resultado;
        } catch (RuntimeException exception) {
            log.error("Falha ao buscar a rede da exclusão {} no care-service; a exclusão segue sem avisar a rede.",
                    protocolo, exception);
            return Optional.empty();
        }
    }

    private void avisarMembro(UUID protocolo, Destinatario membro, String mensagem) {
        try {
            enviarPush(membro, mensagem);
            enviarEmail(membro, mensagem);
        } catch (RuntimeException exception) {
            log.error("Falha ao avisar um membro da rede na exclusão {}; seguindo com os demais.", protocolo, exception);
        }
    }

    private void enviarPush(Destinatario membro, String mensagem) {
        if (membro.usuarioId() == null) {
            return;
        }
        for (DispositivoPush dispositivo : dispositivos.listarAtivos(membro.usuarioId())) {
            if (pushSender.enviarAviso(dispositivo.token(), TITULO, mensagem) == ResultadoPush.TOKEN_INVALIDO) {
                dispositivos.desativarPorToken(dispositivo.token());
            }
        }
    }

    private void enviarEmail(Destinatario membro, String mensagem) {
        if (membro.email() != null && !membro.email().isBlank()) {
            emailSender.enviar(membro.email(), TITULO, mensagem);
        }
    }

    private static String nomeDoIdoso(RedeExclusao redeDaExclusao) {
        String nome = redeDaExclusao.primeiroNomeIdoso();
        return nome == null || nome.isBlank() ? NOME_PADRAO : nome.trim();
    }
}
