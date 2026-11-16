package com.seniorinsight.notification.application;

import java.time.Duration;

/** Espera entre duas tentativas. Existe para os testes não dormirem de verdade. */
@FunctionalInterface
public interface Pausa {

    void aguardar(Duration intervalo);
}
