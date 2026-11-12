package com.seniorinsight.notification.domain.model;

import java.util.UUID;

/**
 * Pedido de exclusão dos dados de um titular (lgpd.erasure.requested). {@code titularId} é o {@code idoso_id} do
 * care-service quando o titular é IDOSO; {@code titularSub} é o {@code sub} do Keycloak.
 */
public record PedidoExclusao(UUID protocolo, TipoTitular tipo, UUID titularId, UUID titularSub) {
}
