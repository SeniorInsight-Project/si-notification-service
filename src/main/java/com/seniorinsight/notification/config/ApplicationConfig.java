package com.seniorinsight.notification.config;

import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    ProcessarEventoSosUseCase processarEventoSosUseCase(NotificacaoRepository notificacaoRepository) {
        return new ProcessarEventoSosUseCase(notificacaoRepository);
    }
}
