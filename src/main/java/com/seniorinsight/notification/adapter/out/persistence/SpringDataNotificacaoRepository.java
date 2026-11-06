package com.seniorinsight.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificacaoRepository extends JpaRepository<NotificacaoJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO notification.notificacao
                (id, evento_id, tipo, idoso_id, titulo, mensagem, ocorrido_em)
            VALUES
                (:id, :eventoId, :tipo, :idosoId, :titulo, :mensagem, :ocorridoEm)
            ON CONFLICT (evento_id) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(
            @Param("id") UUID id,
            @Param("eventoId") UUID eventoId,
            @Param("tipo") String tipo,
            @Param("idosoId") UUID idosoId,
            @Param("titulo") String titulo,
            @Param("mensagem") String mensagem,
            @Param("ocorridoEm") Instant ocorridoEm
    );
}
