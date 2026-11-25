package com.ecopedia.charging.booking.domain;

/**
 * Lo que le pasó a una reserva y merece un aviso al conductor.
 *
 * <p>Viaja como texto en el mensaje, así que el nombre es parte del contrato con Notificaciones:
 * renombrar una constante acá deja al consumidor sin saber qué aviso armar.
 */
public enum BookingEventType {
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    BOOKING_NO_SHOW,
    /** La cuenta quedó marcada por una deuda vencida y la reserva se anuló, con la seña devuelta (ECO-62). */
    BOOKING_CANCELLED_FOR_DEBT,
    /**
     * La carga de la reserva no se pudo iniciar por una falla ajena al conductor (la red, el cargador)
     * y la reserva se liberó con la seña devuelta (RF13). No es una cancelación: el conductor no canceló
     * nada, y el aviso no puede decirle que sí.
     */
    BOOKING_ACTIVATION_FAILED
}
