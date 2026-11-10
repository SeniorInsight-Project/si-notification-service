package com.seniorinsight.notification.domain.model;

import java.util.UUID;

/** Membro da rede de proteção do idoso. {@code usuarioId} é nulo enquanto o familiar não tem conta. */
public record Destinatario(UUID usuarioId, String nome, String email) {
}
