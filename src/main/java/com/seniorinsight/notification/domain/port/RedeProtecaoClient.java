package com.seniorinsight.notification.domain.port;

import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Destinatario;

public interface RedeProtecaoClient {

    /** @throws RedeProtecaoIndisponivelException se o care-service não responder. */
    List<Destinatario> listarRede(UUID idosoId);
}
