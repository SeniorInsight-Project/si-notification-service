package com.seniorinsight.notification.domain.port;

import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.ResultadoPush;

public interface PushSender {

    /** Envia com prioridade alta, no canal de emergência do aparelho. */
    ResultadoPush enviarSos(String token, Notificacao notificacao);

    /** Envia um aviso comum, sem a prioridade nem o canal de emergência do SOS. */
    ResultadoPush enviarAviso(String token, String titulo, String mensagem);

    /** Envia alerta técnico em horário e canal padrão do aparelho. */
    ResultadoPush enviarAlerta(String token, Notificacao notificacao);
}
