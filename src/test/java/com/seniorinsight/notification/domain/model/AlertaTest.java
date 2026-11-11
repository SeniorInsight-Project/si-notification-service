package com.seniorinsight.notification.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AlertaTest {

    private static final Instant GERADO = Instant.parse("2026-11-10T12:00:00Z");

    private final Alerta alerta = Alerta.novo(UUID.randomUUID(), UUID.randomUUID(), TipoAlerta.SOS, "SOS", "detalhe", GERADO);

    @Test
    void alertaNovoNaoTemAvaliacao() {
        assertNull(alerta.avaliacao());
        assertNull(alerta.avaliadoEm());
    }

    @Test
    void avaliaDentroDoPrazoEPermiteTrocar() {
        Instant primeira = GERADO.plusSeconds(60);
        Alerta util = alerta.avaliar(AvaliacaoAlerta.UTIL, "  chegou a tempo ", primeira);
        assertEquals(AvaliacaoAlerta.UTIL, util.avaliacao());
        assertEquals("chegou a tempo", util.motivoAvaliacao());
        assertEquals(primeira, util.avaliadoEm());

        Alerta falso = util.avaliar(AvaliacaoAlerta.FALSO, null, GERADO.plusSeconds(3600));
        assertEquals(AvaliacaoAlerta.FALSO, falso.avaliacao());
        assertNull(falso.motivoAvaliacao());
    }

    @Test
    void aceitaAvaliarExatamenteNoLimiteDe24Horas() {
        Alerta avaliado = alerta.avaliar(AvaliacaoAlerta.UTIL, null, GERADO.plus(Alerta.PRAZO_AVALIACAO));
        assertEquals(AvaliacaoAlerta.UTIL, avaliado.avaliacao());
    }

    @Test
    void rejeitaDepoisDe24Horas() {
        Instant depois = GERADO.plus(Alerta.PRAZO_AVALIACAO).plusSeconds(1);
        assertThrows(PrazoAvaliacaoVencidoException.class, () -> alerta.avaliar(AvaliacaoAlerta.UTIL, null, depois));
    }

    @Test
    void rejeitaAvaliacaoNulaEMotivoLongo() {
        assertThrows(IllegalArgumentException.class, () -> alerta.avaliar(null, null, GERADO));
        assertThrows(IllegalArgumentException.class,
                () -> alerta.avaliar(AvaliacaoAlerta.UTIL, "x".repeat(501), GERADO));
    }
}
