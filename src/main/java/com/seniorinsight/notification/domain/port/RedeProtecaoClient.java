package com.seniorinsight.notification.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.RedeExclusao;

public interface RedeProtecaoClient {

    /** @throws RedeProtecaoIndisponivelException se o care-service não responder. */
    List<Destinatario> listarRede(UUID idosoId);

    /**
     * Quem avisar da exclusão do idoso do protocolo. Vazio enquanto o care-service ainda não processou o pedido.
     *
     * @throws RedeProtecaoIndisponivelException se o care-service responder com erro ou não responder.
     */
    Optional<RedeExclusao> buscarRedeDaExclusao(UUID protocolo);
}
