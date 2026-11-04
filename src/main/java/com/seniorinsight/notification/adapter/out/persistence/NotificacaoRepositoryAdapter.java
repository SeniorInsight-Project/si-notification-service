package com.seniorinsight.notification.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.seniorinsight.notification.domain.model.Localizacao;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificacaoRepositoryAdapter implements NotificacaoRepository {

    private static final String INSERIR_SE_AUSENTE = """
            INSERT INTO notification.notificacao
                (id, evento_id, tipo, idoso_id, titulo, mensagem, ocorrido_em,
                 latitude, longitude, nivel_bateria, observacao)
            VALUES
                (:id, :eventoId, :tipo, :idosoId, :titulo, :mensagem, :ocorridoEm,
                 :latitude, :longitude, :nivelBateria, :observacao)
            ON CONFLICT (evento_id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    public NotificacaoRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean registrarSeAusente(Notificacao notificacao) {
        Localizacao localizacao = notificacao.localizacao();
        return jdbcClient.sql(INSERIR_SE_AUSENTE)
                .param("id", notificacao.id())
                .param("eventoId", notificacao.idEvento())
                .param("tipo", notificacao.tipo())
                .param("idosoId", notificacao.idosoId())
                .param("titulo", notificacao.titulo())
                .param("mensagem", notificacao.mensagem())
                .param("ocorridoEm", OffsetDateTime.ofInstant(notificacao.ocorridoEm(), ZoneOffset.UTC))
                .param("latitude", localizacao == null ? null : localizacao.latitude())
                .param("longitude", localizacao == null ? null : localizacao.longitude())
                .param("nivelBateria", notificacao.nivelBateria())
                .param("observacao", notificacao.observacao())
                .update() == 1;
    }
}
