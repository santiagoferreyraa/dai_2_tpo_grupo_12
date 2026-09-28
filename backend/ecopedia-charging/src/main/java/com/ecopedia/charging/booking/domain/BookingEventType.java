package com.ecopedia.charging.booking.domain;

/**
 * Lo que le pasó a una reserva y merece un aviso al conductor.
 *
 * <p>Viaja como texto en el mensaje, así que el nombre es parte del contrato con Notificaciones:
 * renombrar una constante acá deja al consumidor sin saber qué aviso armar.
 */
public enum BookingEventType {
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED
}
