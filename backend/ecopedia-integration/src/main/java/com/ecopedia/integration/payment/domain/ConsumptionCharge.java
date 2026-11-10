package com.ecopedia.integration.payment.domain;

import java.math.BigDecimal;

/**
 * Cómo terminó el cobro de un consumo (RF14, RNF06, ECO-44).
 *
 * @param pendingPaymentId la deuda que quedó anotada, si no se pudo cobrar; {@code null} si se cobró
 */
public record ConsumptionCharge(Status status, BigDecimal amount, Long pendingPaymentId) {

    public enum Status {
        /** La pasarela cobró. */
        CHARGED,
        /**
         * No se pudo cobrar —la pasarela lo rechazó, no contestó, o el conductor no tiene tarjeta
         * usable— y quedó como deuda pendiente: bloquea reservar y cargar hasta saldarla (RF02).
         */
        DEBT_REGISTERED
    }
}
