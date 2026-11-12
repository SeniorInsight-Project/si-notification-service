package com.seniorinsight.notification.domain.model;

import java.util.List;

/** Quem deve ser avisado da exclusão de um idoso, como o care-service guardou antes de apagar a rede. */
public record RedeExclusao(String primeiroNomeIdoso, List<Destinatario> membros) {
}
