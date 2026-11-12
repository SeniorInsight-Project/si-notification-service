package com.seniorinsight.notification.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.StatusEntrega;

public interface EntregaRepository {

    /** Cria a entrega pendente; devolve {@code false} se já existe (notificação + destinatário + canal). */
    boolean registrarSeAusente(Entrega entrega);

    /**
     * Marca como lidas, em {@code lidaEm}, as entregas do destinatário para a notificação que ainda não foram lidas.
     * Idempotente: se já houve leitura, mantém o instante original.
     *
     * @return o instante da leitura, ou vazio se a notificação não tem entrega para o destinatário.
     */
    Optional<Instant> confirmarLeitura(UUID notificacaoId, UUID destinatarioId, Instant lidaEm);

    void atualizarStatus(Entrega entrega, StatusEntrega status, Instant enviadoEm);

    /**
     * Reivindica, de forma atômica e sem competir com outras instâncias, as entregas de push enviadas antes de
     * {@code enviadasAte} e ainda sem leitura, marcando-as como reenviadas.
     */
    List<Entrega> reivindicarSemLeitura(Instant enviadasAte, int limite);

    /** Apaga as entregas em que o usuário é o destinatário. */
    void removerDoDestinatario(UUID destinatarioId);
}
