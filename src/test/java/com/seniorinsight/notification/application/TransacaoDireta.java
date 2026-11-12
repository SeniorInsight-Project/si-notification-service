package com.seniorinsight.notification.application;

import java.util.function.Supplier;

import com.seniorinsight.notification.domain.port.Transacao;

/** Transação de mentira para os testes de caso de uso: só roda a ação. */
final class TransacaoDireta implements Transacao {

    @Override
    public <T> T obter(Supplier<T> acao) {
        return acao.get();
    }
}
