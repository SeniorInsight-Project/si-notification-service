package com.seniorinsight.notification.domain.port;

import java.util.UUID;

import com.seniorinsight.notification.domain.model.ProtocoloExclusao;

public interface ProtocoloExclusaoRepository {

    /** Reserva o protocolo se ainda não existe e devolve o andamento atual. */
    ProtocoloExclusao registrar(UUID protocolo);

    void marcarAvisoEnviado(UUID protocolo);

    /** @return {@code true} só para quem concluiu o protocolo; {@code false} se já estava concluído. */
    boolean concluir(UUID protocolo);
}
