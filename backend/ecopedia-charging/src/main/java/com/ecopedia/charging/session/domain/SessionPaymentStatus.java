package com.ecopedia.charging.session.domain;

/**
 * Cómo quedó el cobro del consumo al cerrar la sesión (RF14, RNF06).
 *
 * <p>El cierre de la sesión y la liberación del conector no dependen de esto: van primero, en la
 * transacción local. El cobro es una operación externa y se resuelve después, por cualquiera de
 * estos caminos.
 */
public enum SessionPaymentStatus {
    /** La sesión sigue activa: todavía no hay nada que cobrar. */
    NOT_SETTLED,
    /** El consumo no superó la seña (o fue cero): no hay nada más que cobrar. */
    NOT_REQUIRED,
    /** La pasarela cobró la diferencia. */
    CHARGED,
    /**
     * La pasarela rechazó el cobro o no contestó, y Pagos lo anotó como deuda pendiente (RNF06):
     * el conductor no puede reservar ni cargar hasta saldarla.
     */
    DEBT_REGISTERED,
    /**
     * No se pudo ni calcular el importe ni dejarlo anotado: Tarificación o Pagos no contestaron. La
     * sesión queda cerrada igual y el importe, para reintentar.
     */
    PENDING
}
