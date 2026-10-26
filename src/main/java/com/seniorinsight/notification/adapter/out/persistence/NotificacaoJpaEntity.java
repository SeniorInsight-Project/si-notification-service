package com.seniorinsight.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Notificacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notificacao", schema = "notification")
public class NotificacaoJpaEntity {

    @Id
    private UUID id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "idoso_id", nullable = false)
    private UUID idosoId;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "mensagem", nullable = false, length = 1000)
    private String mensagem;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    protected NotificacaoJpaEntity() {
    }

    public NotificacaoJpaEntity(Notificacao notificacao) {
        this.id = notificacao.id();
        this.eventoId = notificacao.idEvento();
        this.tipo = notificacao.tipo();
        this.idosoId = notificacao.idosoId();
        this.titulo = notificacao.titulo();
        this.mensagem = notificacao.mensagem();
        this.ocorridoEm = notificacao.ocorridoEm();
    }
}
