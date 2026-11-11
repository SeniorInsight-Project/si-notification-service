package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Confirmação de leitura de uma notificação por um destinatário. */
public record LeituraNotificacao(UUID notificacaoId, Instant lidaEm) {
}
