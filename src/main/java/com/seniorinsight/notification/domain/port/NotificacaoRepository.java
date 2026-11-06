package com.seniorinsight.notification.domain.port;

import com.seniorinsight.notification.domain.model.Notificacao;

public interface NotificacaoRepository {

    boolean registrarSeAusente(Notificacao notificacao);
}
