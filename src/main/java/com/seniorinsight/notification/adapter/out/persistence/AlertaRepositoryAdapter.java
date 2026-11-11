package com.seniorinsight.notification.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.AvaliacaoAlerta;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AlertaRepositoryAdapter implements AlertaRepository {

    private static final String COLUNAS =
            "id, origem_id, idoso_id, tipo, titulo, detalhe, gerado_em, avaliacao, avaliado_em, motivo_avaliacao";

    private final JdbcClient jdbc;

    public AlertaRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean registrarSeAusente(Alerta alerta) {
        return jdbc.sql("""
                INSERT INTO notification.alerta (id, origem_id, idoso_id, tipo, titulo, detalhe, gerado_em)
                VALUES (:id, :origemId, :idosoId, :tipo, :titulo, :detalhe, :geradoEm)
                ON CONFLICT (origem_id) DO NOTHING
                """)
                .param("id", alerta.id())
                .param("origemId", alerta.origemId())
                .param("idosoId", alerta.idosoId())
                .param("tipo", alerta.tipo().name())
                .param("titulo", alerta.titulo())
                .param("detalhe", alerta.detalhe())
                .param("geradoEm", utc(alerta.geradoEm()))
                .update() == 1;
    }

    @Override
    public List<Alerta> listarPorIdoso(UUID idosoId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM notification.alerta WHERE idoso_id = :idosoId ORDER BY gerado_em DESC, id")
                .param("idosoId", idosoId)
                .query(this::mapear)
                .list();
    }

    @Override
    public Optional<Alerta> buscarPorId(UUID idosoId, UUID alertaId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM notification.alerta WHERE id = :id AND idoso_id = :idosoId")
                .param("id", alertaId)
                .param("idosoId", idosoId)
                .query(this::mapear)
                .optional();
    }

    @Override
    @Transactional
    public void salvarAvaliacao(Alerta alerta) {
        jdbc.sql("""
                UPDATE notification.alerta
                SET avaliacao = :avaliacao, avaliado_em = :avaliadoEm, motivo_avaliacao = :motivo
                WHERE id = :id
                """)
                .param("avaliacao", alerta.avaliacao() == null ? null : alerta.avaliacao().name())
                .param("avaliadoEm", alerta.avaliadoEm() == null ? null : utc(alerta.avaliadoEm()))
                .param("motivo", alerta.motivoAvaliacao())
                .param("id", alerta.id())
                .update();
    }

    private Alerta mapear(ResultSet rs, int linha) throws SQLException {
        String avaliacao = rs.getString("avaliacao");
        OffsetDateTime avaliadoEm = rs.getObject("avaliado_em", OffsetDateTime.class);
        return new Alerta(
                rs.getObject("id", UUID.class),
                rs.getObject("origem_id", UUID.class),
                rs.getObject("idoso_id", UUID.class),
                TipoAlerta.valueOf(rs.getString("tipo")),
                rs.getString("titulo"),
                rs.getString("detalhe"),
                rs.getObject("gerado_em", OffsetDateTime.class).toInstant(),
                avaliacao == null ? null : AvaliacaoAlerta.valueOf(avaliacao),
                avaliadoEm == null ? null : avaliadoEm.toInstant(),
                rs.getString("motivo_avaliacao"));
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
