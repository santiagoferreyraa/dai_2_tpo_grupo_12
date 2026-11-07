package com.ecopedia.integration.payment.domain;

/**
 * Estado de una deuda o cobro pendiente del conductor (RF02, RF14, ECO-54).
 */
public enum PendingPaymentStatus {
    /** Deuda pendiente de cobro: bloquea reservas y habilitación de cargadores. */
    PENDING,
    /** Deuda saldada exitosamente. */
    SETTLED,
    /** Deuda cancelada o condonada administrativamente. */
    CANCELLED
}
