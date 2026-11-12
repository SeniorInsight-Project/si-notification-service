package com.seniorinsight.notification.adapter.out.persistence;

import java.util.UUID;

import com.seniorinsight.notification.domain.model.ProtocoloExclusao;
import com.seniorinsight.notification.domain.port.ProtocoloExclusaoRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProtocoloExclusaoRepositoryAdapter implements ProtocoloExclusaoRepository {

    private final JdbcClient jdbc;

    public ProtocoloExclusaoRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public ProtocoloExclusao registrar(UUID protocolo) {
        jdbc.sql("INSERT INTO notification.exclusao_protocolo (protocolo) VALUES (:protocolo) ON CONFLICT DO NOTHING")
                .param("protocolo", protocolo)
                .update();
        return jdbc.sql("SELECT aviso_enviado_em IS NOT NULL AS aviso, concluido_em IS NOT NULL AS concluido "
                        + "FROM notification.exclusao_protocolo WHERE protocolo = :protocolo")
                .param("protocolo", protocolo)
                .query((rs, linha) -> new ProtocoloExclusao(protocolo, rs.getBoolean("aviso"), rs.getBoolean("concluido")))
                .single();
    }

    @Override
    @Transactional
    public void marcarAvisoEnviado(UUID protocolo) {
        jdbc.sql("UPDATE notification.exclusao_protocolo SET aviso_enviado_em = CURRENT_TIMESTAMP "
                        + "WHERE protocolo = :protocolo AND aviso_enviado_em IS NULL")
                .param("protocolo", protocolo)
                .update();
    }

    @Override
    @Transactional
    public boolean concluir(UUID protocolo) {
        return jdbc.sql("UPDATE notification.exclusao_protocolo SET concluido_em = CURRENT_TIMESTAMP "
                        + "WHERE protocolo = :protocolo AND concluido_em IS NULL")
                .param("protocolo", protocolo)
                .update() == 1;
    }
}
