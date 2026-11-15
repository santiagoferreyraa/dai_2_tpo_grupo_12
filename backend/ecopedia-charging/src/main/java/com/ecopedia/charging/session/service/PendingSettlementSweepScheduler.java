package com.ecopedia.charging.session.service;

import com.ecopedia.charging.session.domain.ChargingSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido periódico que reintenta la liquidación y cobro de consumo para sesiones completadas
 * cuyo estado de pago quedó {@code PENDING_SETTLEMENT} por fallas temporales en Tarificación o Pagos (RF14, RNF06, ECO-55).
 */
@Component
public class PendingSettlementSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingSettlementSweepScheduler.class);

    private final ChargingSessionService sessionService;

    public PendingSettlementSweepScheduler(ChargingSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(
            fixedDelayString = "${ecopedia.charging.settlement-sweep-ms:30000}",
            initialDelayString = "${ecopedia.charging.settlement-sweep-ms:30000}")
    public void sweep() {
        try {
            int settled = sessionService.retryPendingSettlements();
            if (settled > 0) {
                log.info("Barrido de liquidaciones pendientes completado: {} sesiones regularizadas", settled);
            }
        } catch (RuntimeException failure) {
            log.warn("Error durante el barrido de liquidaciones pendientes: {}", failure.getMessage());
        }
    }
}
