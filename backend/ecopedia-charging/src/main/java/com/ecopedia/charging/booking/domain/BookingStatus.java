package com.ecopedia.charging.booking.domain;

/**
 * Estado de una reserva guardada.
 *
 * <p>La reserva consumida al iniciar la carga (RF11) se suma con SesionesDeCarga (ECO-44), y por
 * eso la columna es texto y no un número: agregar un estado no reinterpreta los que ya están
 * guardados.
 */
public enum BookingStatus {
    CONFIRMED,
    CANCELLED,
    /**
     * El conductor no se presentó: pasó la tolerancia desde el inicio y la reserva seguía
     * confirmada (RF09, ECO-57). Libera el conector por el resto de la ventana y al conductor.
     */
    NO_SHOW
}
