package com.ecopedia.async.notification.messaging;

import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
import java.time.Instant;

/**
 * El cuerpo JSON que publica Reservas ({@code BookingEvent} en {@code ecopedia-charging}), visto
 * desde este lado.
 *
 * <p>Es una copia del contrato y no la misma clase: cada artefacto tiene la suya y lo único
 * compartido es el formato. Ver {@code JmsConfig}. Si Reservas agrega un campo, acá se ignora
 * hasta que alguien lo necesite; si renombra uno, este record deja de recibirlo.
 */
public record BookingEventMessage(
        String eventId,
        NotificationType type,
        Long bookingId,
        Long driverId,
        Long connectorId,
        Instant windowStart,
        Instant windowEnd,
        Instant occurredAt) {

    NotificationToDispatch toDispatch() {
        return new NotificationToDispatch(eventId, type, driverId, bookingId, connectorId, windowStart, windowEnd);
    }
}
