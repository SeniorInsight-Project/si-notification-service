package com.seniorinsight.notification.adapter.in.scheduler;

import com.seniorinsight.notification.application.ReenviarSosSemLeituraUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReenvioSosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReenvioSosScheduler.class);

    private final ReenviarSosSemLeituraUseCase reenviar;

    public ReenvioSosScheduler(ReenviarSosSemLeituraUseCase reenviar) {
        this.reenviar = reenviar;
    }

    @Scheduled(fixedDelayString = "${notification.sos.intervalo-verificacao}")
    public void verificar() {
        try {
            int tratadas = reenviar.executar();
            if (tratadas > 0) {
                log.info("{} entrega(s) de SOS sem leitura reenviadas.", tratadas);
            }
        } catch (RuntimeException exception) {
            log.error("Falha na verificacao de SOS sem leitura.", exception);
        }
    }
}
