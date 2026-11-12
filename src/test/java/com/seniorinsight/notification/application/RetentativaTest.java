package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RetentativaTest {

    private static final Duration INTERVALO = Duration.ofSeconds(2);

    private final List<Duration> pausas = new ArrayList<>();
    private final Retentativa retentativa = new Retentativa(3, INTERVALO, pausas::add);

    @Test
    void executarRepeteAteDarCerto() {
        AtomicInteger chamadas = new AtomicInteger();

        retentativa.executar(() -> {
            if (chamadas.incrementAndGet() < 3) {
                throw new IllegalStateException("banco fora");
            }
        });

        assertEquals(3, chamadas.get());
        assertEquals(List.of(INTERVALO, INTERVALO), pausas);
    }

    @Test
    void executarPropagaAUltimaFalhaQuandoOLimiteEstoura() {
        AtomicInteger chamadas = new AtomicInteger();

        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> retentativa.executar(() -> {
            throw new IllegalStateException("falha " + chamadas.incrementAndGet());
        }));

        assertEquals("falha 3", erro.getMessage());
        assertEquals(2, pausas.size());
    }

    @Test
    void executarNaoPausaQuandoDaCertoDePrimeira() {
        retentativa.executar(() -> {
        });

        assertEquals(List.of(), pausas);
    }

    @Test
    void buscarRepeteEnquantoVazioEDevolveOPrimeiroValor() {
        AtomicInteger chamadas = new AtomicInteger();

        Optional<String> resultado = retentativa.buscar(
                () -> chamadas.incrementAndGet() < 3 ? Optional.empty() : Optional.of("pronto"));

        assertEquals(Optional.of("pronto"), resultado);
        assertEquals(2, pausas.size());
    }

    @Test
    void buscarDevolveVazioQuandoOLimiteEstoura() {
        AtomicInteger chamadas = new AtomicInteger();

        Optional<String> resultado = retentativa.buscar(() -> {
            chamadas.incrementAndGet();
            return Optional.empty();
        });

        assertEquals(Optional.empty(), resultado);
        assertEquals(3, chamadas.get());
        assertEquals(2, pausas.size());
    }

    @Test
    void buscarRepeteDepoisDeFalhaEPropagaSeAUltimaTentativaFalhar() {
        AtomicInteger chamadas = new AtomicInteger();

        Optional<String> recuperado = retentativa.buscar(() -> {
            if (chamadas.incrementAndGet() == 1) {
                throw new IllegalStateException("care fora");
            }
            return Optional.of("ok");
        });
        assertEquals(Optional.of("ok"), recuperado);

        assertThrows(IllegalStateException.class, () -> retentativa.buscar(() -> {
            throw new IllegalStateException("care fora");
        }));
    }

    @Test
    void exigeAoMenosUmaTentativa() {
        assertThrows(IllegalArgumentException.class, () -> new Retentativa(0, INTERVALO, pausas::add));
    }
}
