package com.seniorinsight.notification.adapter.out.persistence;

import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificacaoRepositoryAdapter implements NotificacaoRepository {

    private final SpringDataNotificacaoRepository repository;

    public NotificacaoRepositoryAdapter(SpringDataNotificacaoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean registrarSeAusente(Notificacao notificacao) {
        return repository.inserirSeAusente(
                notificacao.id(),
                notificacao.idEvento(),
                notificacao.tipo(),
                notificacao.idosoId(),
                notificacao.titulo(),
                notificacao.mensagem(),
                notificacao.ocorridoEm()
        ) == 1;
    }
}
