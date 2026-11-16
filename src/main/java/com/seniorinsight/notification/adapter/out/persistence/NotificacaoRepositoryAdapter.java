package com.seniorinsight.notification.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificacaoRepositoryAdapter implements NotificacaoRepository {

    private static final String COLUNAS = "id, evento_id, tipo, idoso_id, titulo, mensagem, ocorrido_em, programada_para";

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
                notificacao.ocorridoEm(),
                notificacao.programadaPara()
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

    @Override
    public List<Notificacao> buscarAgendadasAte(Instant instante, int limite) {
        return jdbc.sql("""
                SELECT %s FROM notification.notificacao
                WHERE tipo = 'TECNICO' AND processada_em IS NULL AND programada_para <= :instante
                ORDER BY programada_para LIMIT :limite
                """.formatted(COLUNAS))
                .param("instante", OffsetDateTime.ofInstant(instante, ZoneOffset.UTC))
                .param("limite", limite)
                .query(this::mapear)
                .list();
    }

    @Override
    @Transactional
    public void marcarAgendamentoProcessado(UUID notificacaoId, Instant processadoEm) {
        jdbc.sql("UPDATE notification.notificacao SET processada_em = :processadoEm WHERE id = :id")
                .param("processadoEm", OffsetDateTime.ofInstant(processadoEm, ZoneOffset.UTC))
                .param("id", notificacaoId)
                .update();
    }

    private Notificacao mapear(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        return new Notificacao(
                rs.getObject("id", UUID.class),
                rs.getObject("evento_id", UUID.class),
                rs.getString("tipo"),
                rs.getObject("idoso_id", UUID.class),
                rs.getString("titulo"),
                rs.getString("mensagem"),
                rs.getObject("ocorrido_em", OffsetDateTime.class).toInstant(),
                rs.getObject("programada_para", OffsetDateTime.class).toInstant()
        );
    }

    @Override
    @Transactional
    public void removerDoIdoso(UUID idosoId) {
        jdbc.sql("DELETE FROM notification.notificacao WHERE idoso_id = :idosoId")
                .param("idosoId", idosoId)
                .update();
    }
}
