package com.seniorinsight.notification.domain.port;

import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.ResultadoPush;

public interface PushSender {

    /** Envia com prioridade alta, no canal de emergência do aparelho. */
    ResultadoPush enviarSos(String token, Notificacao notificacao);
}
