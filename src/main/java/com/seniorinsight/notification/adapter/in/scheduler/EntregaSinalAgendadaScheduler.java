package com.seniorinsight.notification.adapter.in.scheduler;

import com.seniorinsight.notification.application.ProcessarEventoSinalUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EntregaSinalAgendadaScheduler {

    private static final Logger log = LoggerFactory.getLogger(EntregaSinalAgendadaScheduler.class);
    private final ProcessarEventoSinalUseCase processar;

    public EntregaSinalAgendadaScheduler(ProcessarEventoSinalUseCase processar) {
        this.processar = processar;
    }

    @Scheduled(fixedDelayString = "${notification.sinal.intervalo-verificacao}")
    public void entregar() {
        try {
            int tratadas = processar.entregarAgendadas();
            if (tratadas > 0) {
                log.info("{} notificação(ões) de sinal agendada(s) entregues.", tratadas);
            }
        } catch (RuntimeException exception) {
            log.error("Falha ao entregar notificações de sinal agendadas.", exception);
        }
    }
}
