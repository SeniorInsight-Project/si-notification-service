package com.seniorinsight.notification.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ModeloDeDominioTest {

    @Test
    void eventoSosExigeIdentificadores() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventoSos(null, UUID.randomUUID(), "Maria", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EventoSos(UUID.randomUUID(), null, "Maria", null, null, null, null, null));
    }

    @Test
    void localizacaoValidaLimites() {
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(0, -181));
        assertDoesNotThrow(() -> new Localizacao(-90, 180));
        assertTrue(Localizacao.seValida(null, 10.0).isEmpty());
        assertTrue(Localizacao.seValida(-23.5, -46.6).isPresent());
    }

    @Test
    void notificacaoRejeitaTextosForaDosLimites() {
        assertThrows(IllegalArgumentException.class, () -> notificacao("SOS", "x".repeat(161), "msg", null, null));
        assertThrows(IllegalArgumentException.class, () -> notificacao("SOS", "titulo", "x".repeat(1001), null, null));
        assertThrows(IllegalArgumentException.class, () -> notificacao("SOS", "titulo", " ", null, null));
        assertThrows(IllegalArgumentException.class, () -> notificacao("SOS", "titulo", "msg", "x".repeat(501), null));
        assertThrows(IllegalArgumentException.class, () -> notificacao("SOS", "titulo", "msg", null, 101));
        assertDoesNotThrow(() -> notificacao("SOS", "titulo", "msg", "obs", 100));
    }

    @Test
    void notificacaoExigeCamposObrigatorios() {
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now();
        assertThrows(NullPointerException.class,
                () -> new Notificacao(null, id, "SOS", id, "t", "m", agora, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new Notificacao(id, null, "SOS", id, "t", "m", agora, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new Notificacao(id, id, "SOS", null, "t", "m", agora, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new Notificacao(id, id, "SOS", id, "t", "m", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Notificacao(id, id, null, id, "t", "m", agora, null, null, null));
    }

    @Test
    void notificacaoAceitaTextosExatamenteNoLimite() {
        assertDoesNotThrow(() -> notificacao("T".repeat(40), "x".repeat(160), "x".repeat(1000), "x".repeat(500), 0));
        assertThrows(IllegalArgumentException.class, () -> notificacao("T".repeat(41), "titulo", "msg", null, null));
        assertThrows(IllegalArgumentException.class, () -> notificacao("SOS", "titulo", "msg", null, -1));
    }

    @Test
    void localizacaoDescartaValoresNaoNumericos() {
        assertTrue(Localizacao.seValida(Double.NaN, 0.0).isEmpty());
        assertTrue(Localizacao.seValida(0.0, Double.POSITIVE_INFINITY).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(Double.NaN, 0));
    }

    private static Notificacao notificacao(String tipo, String titulo, String mensagem, String observacao,
                                           Integer nivelBateria) {
        return new Notificacao(UUID.randomUUID(), UUID.randomUUID(), tipo, UUID.randomUUID(), titulo, mensagem,
                Instant.now(), null, nivelBateria, observacao);
    }
}
