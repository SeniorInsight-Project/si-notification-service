package com.seniorinsight.notification.adapter.out.persistence;

import java.util.UUID;

import com.seniorinsight.notification.domain.port.TitularExcluidoRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Guarda o SHA-256 do sub, não o sub: a tabela só serve para perguntar "esse usuário já foi excluído?". */
@Repository
public class TitularExcluidoRepositoryAdapter implements TitularExcluidoRepository {

    private static final String HASH = "encode(sha256(convert_to(CAST(:usuarioId AS text), 'UTF8')), 'hex')";

    private final JdbcClient jdbc;

    public TitularExcluidoRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void travar(UUID usuarioId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:usuarioId AS text), 0))")
                .param("usuarioId", usuarioId)
                .query()
                .listOfRows();
    }

    @Override
    @Transactional
    public void registrar(UUID usuarioId) {
        jdbc.sql("INSERT INTO notification.titular_excluido (usuario_hash) VALUES (" + HASH + ") ON CONFLICT DO NOTHING")
                .param("usuarioId", usuarioId)
                .update();
    }

    @Override
    public boolean existe(UUID usuarioId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM notification.titular_excluido WHERE usuario_hash = " + HASH + ")")
                .param("usuarioId", usuarioId)
                .query(Boolean.class)
                .single();
    }
}
