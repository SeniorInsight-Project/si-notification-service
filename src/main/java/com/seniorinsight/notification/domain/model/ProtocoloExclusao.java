package com.seniorinsight.notification.domain.model;

import java.util.UUID;

/** Andamento de um pedido de exclusão: se a rede já foi avisada e se a exclusão já foi concluída e confirmada. */
public record ProtocoloExclusao(UUID protocolo, boolean avisoEnviado, boolean concluido) {
}
