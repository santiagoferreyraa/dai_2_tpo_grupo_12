package com.ecopedia.charging.session.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido periódico que corta automáticamente las sesiones de carga activas cuya ventana reservada
 * o ventana walk-in (15 minutos antes de la próxima reserva) ha vencido (RF10, RF12, ECO-58).
 */
@Component
public class SessionWindowCutoffScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionWindowCutoffScheduler.class);

    private final ChargingActivationService activationService;

    public SessionWindowCutoffScheduler(ChargingActivationService activationService) {
        this.activationService = activationService;
    }

    @Scheduled(
            fixedDelayString = "${ecopedia.session.cutoff-sweep-ms:30000}",
            initialDelayString = "${ecopedia.session.cutoff-sweep-ms:30000}")
    void sweep() {
        try {
            activationService.stopExpiredSessions();
        } catch (RuntimeException failure) {
            log.warn("Error durante el barrido de corte de sesiones vencidas: {}", failure.getMessage());
        }
    }
}
