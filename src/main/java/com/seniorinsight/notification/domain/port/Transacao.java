package com.seniorinsight.notification.domain.port;

import java.util.function.Supplier;

public interface Transacao {

    /** Roda a ação numa transação e devolve o resultado: se lançar exceção, tudo o que gravou volta atrás. */
    <T> T obter(Supplier<T> acao);

    default void executar(Runnable acao) {
        obter(() -> {
            acao.run();
            return null;
        });
    }
}
