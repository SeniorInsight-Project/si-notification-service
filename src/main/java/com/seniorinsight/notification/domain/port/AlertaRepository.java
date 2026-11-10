package com.seniorinsight.notification.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;

public interface AlertaRepository {

    /** Grava o alerta; devolve {@code false} se já existe um alerta para a mesma origem. */
    boolean registrarSeAusente(Alerta alerta);

    /** Do mais recente para o mais antigo. */
    List<Alerta> listarPorIdoso(UUID idosoId);

    Optional<Alerta> buscarPorId(UUID idosoId, UUID alertaId);

    void salvarAvaliacao(Alerta alerta);
}
