package com.seniorinsight.notification.domain.port;

import java.util.UUID;

public interface PublicadorExclusaoConcluida {

    /** Grava {@code lgpd.erasure.completed} para publicação, na transação em andamento. */
    void publicar(UUID protocolo);
}
