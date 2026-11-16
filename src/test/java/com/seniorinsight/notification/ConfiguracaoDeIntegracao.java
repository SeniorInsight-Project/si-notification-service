package com.seniorinsight.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Beans que o {@code @DataJpaTest} não traz e os adaptadores de persistência pedem. */
@TestConfiguration
public class ConfiguracaoDeIntegracao {

    public static final Instant AGORA = Instant.parse("2026-11-12T15:30:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(AGORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
