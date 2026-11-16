package com.seniorinsight.notification.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.CanalEntrega;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.StatusEntrega;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EntregaRepositoryAdapter implements EntregaRepository {

    private final JdbcClient jdbc;

    public EntregaRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean registrarSeAusente(Entrega entrega) {
        return jdbc.sql("""
                INSERT INTO notification.entrega (id, notificacao_id, destinatario_id, canal, status)
                VALUES (:id, :notificacaoId, :destinatarioId, :canal, :status)
                ON CONFLICT (notificacao_id, destinatario_id, canal) DO NOTHING
                """)
                .param("id", entrega.id())
                .param("notificacaoId", entrega.notificacaoId())
                .param("destinatarioId", entrega.destinatarioId())
                .param("canal", entrega.canal().name())
                .param("status", entrega.status().name())
                .update() == 1;
    }

    @Override
    @Transactional
    public Optional<Instant> confirmarLeitura(UUID notificacaoId, UUID destinatarioId, Instant lidaEm) {
        jdbc.sql("""
                UPDATE notification.entrega SET status = 'LIDA', lida_em = :lidaEm
                WHERE notificacao_id = :notificacaoId AND destinatario_id = :destinatarioId AND lida_em IS NULL
                """)
                .param("lidaEm", OffsetDateTime.ofInstant(lidaEm, ZoneOffset.UTC))
                .param("notificacaoId", notificacaoId)
                .param("destinatarioId", destinatarioId)
                .update();
        return jdbc.sql("""
                SELECT MIN(lida_em) FROM notification.entrega
                WHERE notificacao_id = :notificacaoId AND destinatario_id = :destinatarioId
                """)
                .param("notificacaoId", notificacaoId)
                .param("destinatarioId", destinatarioId)
                .query(OffsetDateTime.class)
                .optional()
                .map(OffsetDateTime::toInstant);
    }

    @Override
    @Transactional
    public void atualizarStatus(Entrega entrega, StatusEntrega status, Instant enviadoEm) {
        jdbc.sql("UPDATE notification.entrega SET status = :status, enviado_em = :enviadoEm WHERE id = :id AND status <> 'LIDA'")
                .param("status", status.name())
                .param("enviadoEm", enviadoEm == null ? null : OffsetDateTime.ofInstant(enviadoEm, ZoneOffset.UTC))
                .param("id", entrega.id())
                .update();
    }

    @Override
    @Transactional
    public List<Entrega> reivindicarSemLeitura(Instant enviadasAte, int limite) {
        return jdbc.sql("""
                UPDATE notification.entrega SET reenviado_em = CURRENT_TIMESTAMP
                WHERE id IN (
                    SELECT id FROM notification.entrega
                    WHERE canal = 'PUSH' AND status = 'ENVIADA' AND lida_em IS NULL
                      AND reenviado_em IS NULL AND enviado_em <= :limite
                    ORDER BY enviado_em
                    LIMIT :quantidade
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, notificacao_id, destinatario_id, canal, status, enviado_em
                """)
                .param("limite", OffsetDateTime.ofInstant(enviadasAte, ZoneOffset.UTC))
                .param("quantidade", limite)
                .query((rs, linha) -> new Entrega(
                        rs.getObject("id", UUID.class),
                        rs.getObject("notificacao_id", UUID.class),
                        rs.getObject("destinatario_id", UUID.class),
                        CanalEntrega.valueOf(rs.getString("canal")),
                        StatusEntrega.valueOf(rs.getString("status")),
                        rs.getObject("enviado_em", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    @Transactional
    public void removerDoDestinatario(UUID destinatarioId) {
        jdbc.sql("DELETE FROM notification.entrega WHERE destinatario_id = :destinatarioId")
                .param("destinatarioId", destinatarioId)
                .update();
    }
}
