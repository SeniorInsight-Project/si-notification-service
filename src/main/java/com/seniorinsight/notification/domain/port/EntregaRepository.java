package com.seniorinsight.notification.domain.port;

import java.time.Instant;
import java.util.List;

import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.StatusEntrega;

public interface EntregaRepository {

    /** Cria a entrega pendente; devolve {@code false} se já existe (notificação + destinatário + canal). */
    boolean registrarSeAusente(Entrega entrega);

    void atualizarStatus(Entrega entrega, StatusEntrega status, Instant enviadoEm);

    /**
     * Reivindica, de forma atômica e sem competir com outras instâncias, as entregas de push enviadas antes de
     * {@code enviadasAte} e ainda sem leitura, marcando-as como reenviadas.
     */
    List<Entrega> reivindicarSemLeitura(Instant enviadasAte, int limite);
}
