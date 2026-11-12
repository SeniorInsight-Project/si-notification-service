package com.seniorinsight.notification.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DispositivoPushRepositoryAdapter implements DispositivoPushRepository {

    private final JdbcClient jdbc;

    public DispositivoPushRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public DispositivoPush salvar(DispositivoPush dispositivo) {
        // O mesmo token não pode ficar em dois aparelhos (ex.: outro usuário entrou no mesmo celular).
        jdbc.sql("""
                DELETE FROM notification.dispositivo_push
                WHERE token = :token AND NOT (usuario_id = :usuarioId AND identificador_aparelho = :aparelho)
                """)
                .param("token", dispositivo.token())
                .param("usuarioId", dispositivo.usuarioId())
                .param("aparelho", dispositivo.identificadorAparelho())
                .update();

        OffsetDateTime atualizadoEm = jdbc.sql("""
                INSERT INTO notification.dispositivo_push (usuario_id, identificador_aparelho, token, plataforma)
                VALUES (:usuarioId, :aparelho, :token, :plataforma)
                ON CONFLICT (usuario_id, identificador_aparelho) DO UPDATE
                    SET token = EXCLUDED.token,
                        plataforma = EXCLUDED.plataforma,
                        ativo = TRUE,
                        atualizado_em = CURRENT_TIMESTAMP
                RETURNING atualizado_em
                """)
                .param("usuarioId", dispositivo.usuarioId())
                .param("aparelho", dispositivo.identificadorAparelho())
                .param("token", dispositivo.token())
                .param("plataforma", dispositivo.plataforma().name())
                .query(OffsetDateTime.class)
                .single();
        return new DispositivoPush(dispositivo.usuarioId(), dispositivo.identificadorAparelho(), dispositivo.token(),
                dispositivo.plataforma(), atualizadoEm.toInstant());
    }

    @Override
    @Transactional
    public boolean remover(UUID usuarioId, String identificadorAparelho) {
        return jdbc.sql("""
                DELETE FROM notification.dispositivo_push
                WHERE usuario_id = :usuarioId AND identificador_aparelho = :aparelho
                """)
                .param("usuarioId", usuarioId)
                .param("aparelho", identificadorAparelho)
                .update() > 0;
    }

    @Override
    public List<DispositivoPush> listarAtivos(UUID usuarioId) {
        return jdbc.sql("""
                SELECT usuario_id, identificador_aparelho, token, plataforma, atualizado_em
                FROM notification.dispositivo_push
                WHERE usuario_id = :usuarioId AND ativo
                """)
                .param("usuarioId", usuarioId)
                .query((rs, linha) -> new DispositivoPush(
                        rs.getObject("usuario_id", UUID.class),
                        rs.getString("identificador_aparelho"),
                        rs.getString("token"),
                        Plataforma.valueOf(rs.getString("plataforma")),
                        rs.getObject("atualizado_em", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    @Transactional
    public void desativarPorToken(String token) {
        jdbc.sql("UPDATE notification.dispositivo_push SET ativo = FALSE, atualizado_em = CURRENT_TIMESTAMP WHERE token = :token")
                .param("token", token)
                .update();
    }

    @Override
    @Transactional
    public void removerTodos(UUID usuarioId) {
        jdbc.sql("DELETE FROM notification.dispositivo_push WHERE usuario_id = :usuarioId")
                .param("usuarioId", usuarioId)
                .update();
    }
}
