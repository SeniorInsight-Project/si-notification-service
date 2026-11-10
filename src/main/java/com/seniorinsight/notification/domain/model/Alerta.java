package com.seniorinsight.notification.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Alerta enviado à rede de proteção de um idoso. {@code origemId} identifica o evento que o gerou (ex.: o
 * {@code alarmeId} do SOS) e torna a gravação idempotente.
 */
public record Alerta(
        UUID id,
        UUID origemId,
        UUID idosoId,
        TipoAlerta tipo,
        String titulo,
        String detalhe,
        Instant geradoEm,
        AvaliacaoAlerta avaliacao,
        Instant avaliadoEm,
        String motivoAvaliacao
) {

    public static final Duration PRAZO_AVALIACAO = Duration.ofHours(24);
    public static final int TAMANHO_MAXIMO_MOTIVO = 500;

    public static Alerta novo(UUID origemId, UUID idosoId, TipoAlerta tipo, String titulo, String detalhe, Instant geradoEm) {
        return new Alerta(UUID.randomUUID(), origemId, idosoId, tipo, titulo, detalhe, geradoEm, null, null, null);
    }

    /**
     * Grava ou troca a avaliação. O prazo de 24 h conta a partir de {@code geradoEm}.
     *
     * @throws PrazoAvaliacaoVencidoException se já passaram 24 h do alerta.
     */
    public Alerta avaliar(AvaliacaoAlerta nova, String motivo, Instant agora) {
        if (nova == null) {
            throw new IllegalArgumentException("avaliacao deve ser UTIL ou FALSO.");
        }
        String motivoLimpo = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (motivoLimpo != null && motivoLimpo.length() > TAMANHO_MAXIMO_MOTIVO) {
            throw new IllegalArgumentException("motivo deve ter no máximo 500 caracteres.");
        }
        if (agora.isAfter(geradoEm.plus(PRAZO_AVALIACAO))) {
            throw new PrazoAvaliacaoVencidoException("O prazo de 24 h para avaliar este alerta já venceu.");
        }
        return new Alerta(id, origemId, idosoId, tipo, titulo, detalhe, geradoEm, nova, agora, motivoLimpo);
    }
}
