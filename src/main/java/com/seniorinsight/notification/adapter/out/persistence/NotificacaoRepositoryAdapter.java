package com.seniorinsight.notification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificacaoRepositoryAdapter implements NotificacaoRepository {

    private static final String COLUNAS = "id, evento_id, tipo, idoso_id, titulo, mensagem, ocorrido_em";

    private final SpringDataNotificacaoRepository repository;
    private final JdbcClient jdbc;

    public NotificacaoRepositoryAdapter(SpringDataNotificacaoRepository repository, JdbcClient jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
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

    @Override
    public Optional<Notificacao> buscarPorEventoId(UUID eventoId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM notification.notificacao WHERE evento_id = :id")
                .param("id", eventoId)
                .query(this::mapear)
                .optional();
    }

    @Override
    public Optional<Notificacao> buscarPorId(UUID id) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM notification.notificacao WHERE id = :id")
                .param("id", id)
                .query(this::mapear)
                .optional();
    }

    private Notificacao mapear(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        return new Notificacao(
                rs.getObject("id", UUID.class),
                rs.getObject("evento_id", UUID.class),
                rs.getString("tipo"),
                rs.getObject("idoso_id", UUID.class),
                rs.getString("titulo"),
                rs.getString("mensagem"),
                rs.getObject("ocorrido_em", java.time.OffsetDateTime.class).toInstant()
        );
    }
}
