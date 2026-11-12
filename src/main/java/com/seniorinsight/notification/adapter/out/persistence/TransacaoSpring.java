package com.seniorinsight.notification.adapter.out.persistence;

import java.util.function.Supplier;

import com.seniorinsight.notification.domain.port.Transacao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransacaoSpring implements Transacao {

    private final TransactionTemplate template;

    public TransacaoSpring(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T obter(Supplier<T> acao) {
        return template.execute(status -> acao.get());
    }
}
