package com.ecopedia.integration.payment.service;

import com.ecopedia.integration.payment.domain.DebtCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El reloj de la deuda (ECO-62): cada minuto mira qué reintentos tocan —cada deuda lleva su propia
 * hora, a 20 minutos del intento anterior— y vuelve a pedir la anulación de reservas que Reservas no
 * contestó.
 *
 * <p>Un minuto de barrido contra 20 de intervalo: un reintento sale como mucho un minuto tarde.
 */
@Component
public class DebtRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DebtRetryScheduler.class);

    private final DebtCollectionService debtCollection;

    public DebtRetryScheduler(DebtCollectionService debtCollection) {
        this.debtCollection = debtCollection;
    }

    @Scheduled(
            fixedDelayString = "${ecopedia.payments.debt.sweep-interval}",
            initialDelayString = "${ecopedia.payments.debt.sweep-interval}")
    void sweep() {
        try {
            int settled = debtCollection.retryDueDebts();
            if (settled > 0) {
                log.info("Barrido de deudas: {} saldadas en este reintento", settled);
            }
            debtCollection.cancelBookingsOfDefaultedDebts();
        } catch (RuntimeException failure) {
            // La base caída no tiene que matar el reloj: el próximo barrido lo vuelve a intentar.
            log.warn("No se pudo barrer las deudas: {}", failure.getMessage());
        }
    }
}
