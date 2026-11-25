package com.ecopedia.async.notification.domain;

/**
 * De qué avisa un aviso.
 *
 * <p>Los nombres coinciden con los de {@code BookingEventType} en {@code ecopedia-charging}, porque
 * llegan como texto en el mensaje. Son dos enums, uno por artefacto, y no uno compartido: ver
 * {@code JmsConfig}, que explica por qué los dos lados no comparten clases.
 */
public enum NotificationType {
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    /** El conductor no se presentó y la reserva se cerró sola (ECO-57). */
    BOOKING_NO_SHOW,
    /** La cuenta quedó marcada por una deuda vencida y la reserva se anuló, con la seña devuelta (ECO-62). */
    BOOKING_CANCELLED_FOR_DEBT,
    /**
     * La carga de la reserva no se pudo iniciar por una falla ajena al conductor (la red, el cargador) y
     * la reserva se liberó con la seña devuelta (RF13). No es una cancelación del conductor.
     */
    BOOKING_ACTIVATION_FAILED,
    /**
     * El cobro al cerrar una carga falló y quedó una deuda (ECO-62). Estos cuatro coinciden con
     * {@code DebtEventType} en {@code ecopedia-integration}, que es quien los publica.
     */
    DEBT_RECORDED,
    /** Falló un reintento del cobro y quedan otros. */
    DEBT_RETRY_FAILED,
    /** Falló el último reintento: la cuenta quedó marcada. Un solo aviso para las dos cosas (Manuel, 22/11). */
    DEBT_DEFAULTED,
    /** La deuda se cobró. */
    DEBT_SETTLED
}
