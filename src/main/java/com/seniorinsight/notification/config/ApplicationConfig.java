package com.seniorinsight.notification.config;

import java.time.Clock;

import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ProcessarEventoSosUseCase processarEventoSosUseCase(NotificacaoRepository notificacaoRepository, Clock clock) {
        return new ProcessarEventoSosUseCase(notificacaoRepository, clock);
    }
}
