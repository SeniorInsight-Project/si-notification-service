package com.seniorinsight.notification.domain.port;

import java.util.UUID;

/** Guarda quem já foi excluído, só para recusar a recriação de dados (ex.: um novo push token). */
public interface TitularExcluidoRepository {

    /** Serializa, até o fim da transação atual, o registro de token e a exclusão do mesmo usuário. */
    void travar(UUID usuarioId);

    void registrar(UUID usuarioId);

    boolean existe(UUID usuarioId);
}
