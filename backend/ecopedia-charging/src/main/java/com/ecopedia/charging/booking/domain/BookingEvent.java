package com.ecopedia.charging.booking.domain;

import java.time.Instant;

/**
 * Un hecho de una reserva, tal como sale hacia Notificaciones por la cola
 * {@code notifications.dispatch}.
 *
 * <p><b>Lleva ids, no nombres.</b> Reservas no sabe cómo se llama la estación ni el mail del
 * conductor, y no tiene por qué preguntarlo para publicar: quien arma el aviso es Notificaciones,
 * y es ella la que le pide a core lo que necesita para escribirlo. Así una reserva no espera a
 * nadie más que a la base.
 *
 * @param eventId la clave de deduplicación. Es fija por reserva y tipo —no un UUID nuevo en cada
 *     envío— justamente para que dos envíos del mismo hecho se reconozcan como el mismo: si el
 *     broker reentrega o alguien publica dos veces, Notificaciones guarda un solo aviso.
 */
public record BookingEvent(
        String eventId,
        BookingEventType type,
        Long bookingId,
        Long driverId,
        Long connectorId,
        Instant windowStart,
        Instant windowEnd,
        Instant occurredAt) {

    public static BookingEvent confirmed(Booking booking, Instant occurredAt) {
        return of(BookingEventType.BOOKING_CONFIRMED, booking, occurredAt);
    }

    public static BookingEvent cancelled(Booking booking, Instant occurredAt) {
        return of(BookingEventType.BOOKING_CANCELLED, booking, occurredAt);
    }

    private static BookingEvent of(BookingEventType type, Booking booking, Instant occurredAt) {
        return new BookingEvent(
                "booking-" + booking.getId() + "-" + type.name(),
                type,
                booking.getId(),
                booking.getDriverId(),
                booking.getConnectorId(),
                booking.getWindow().start(),
                booking.getWindow().end(),
                occurredAt);
    }
}
