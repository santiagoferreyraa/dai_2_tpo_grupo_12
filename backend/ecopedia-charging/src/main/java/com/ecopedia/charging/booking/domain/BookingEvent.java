package com.ecopedia.charging.booking.domain;

import java.math.BigDecimal;
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
 * <p><b>La seña sí viaja</b> (ECO-53): es un dato de la reserva, no de otro componente, y el aviso
 * tiene que decir qué pasó con la plata del conductor.
 *
 * @param eventId la clave de deduplicación. Es fija por reserva y tipo —no un UUID nuevo en cada
 *     envío— justamente para que dos envíos del mismo hecho se reconozcan como el mismo: si el
 *     broker reentrega o alguien publica dos veces, Notificaciones guarda un solo aviso.
 * @param depositAmount la seña cobrada; 0 si no hubo.
 * @param depositRefunded en una cancelación, si la seña se devuelve; {@code null} en los demás.
 * @param cardLastFour la tarjeta a la que se devuelve; {@code null} si no hubo seña.
 * @param depositRefundableUntil hasta cuándo cancelar devolvía la seña; {@code null} si no hubo.
 */
public record BookingEvent(
        String eventId,
        BookingEventType type,
        Long bookingId,
        Long driverId,
        Long connectorId,
        Instant windowStart,
        Instant windowEnd,
        Instant occurredAt,
        BigDecimal depositAmount,
        Boolean depositRefunded,
        String cardLastFour,
        Instant depositRefundableUntil) {

    public static BookingEvent confirmed(Booking booking, Instant occurredAt) {
        return of(BookingEventType.BOOKING_CONFIRMED, booking, occurredAt, null);
    }

    public static BookingEvent cancelled(Booking booking, Instant occurredAt) {
        Boolean refunded = booking.getDepositStatus() == DepositStatus.NONE ? null : booking.isDepositRefunded();
        return of(BookingEventType.BOOKING_CANCELLED, booking, occurredAt, refunded);
    }

    public static BookingEvent noShow(Booking booking, Instant occurredAt) {
        return of(BookingEventType.BOOKING_NO_SHOW, booking, occurredAt, null);
    }

    private static BookingEvent of(BookingEventType type, Booking booking, Instant occurredAt, Boolean refunded) {
        return new BookingEvent(
                "booking-" + booking.getId() + "-" + type.name(),
                type,
                booking.getId(),
                booking.getDriverId(),
                booking.getConnectorId(),
                booking.getWindow().start(),
                booking.getWindow().end(),
                occurredAt,
                booking.getDepositAmount(),
                refunded,
                booking.getDepositCardLastFour(),
                booking.getDepositRefundableUntil());
    }
}
