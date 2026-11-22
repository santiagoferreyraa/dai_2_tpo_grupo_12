package com.ecopedia.async.notification.messaging;

import com.ecopedia.async.notification.domain.NotificationToDispatch;

/**
 * Lo que llega por la cola {@code notifications.dispatch}: un evento de reserva (de Reservas) o, desde
 * ECO-62, uno de deuda (de Pagos). Cada uno se traduce a lo mismo, un {@link NotificationToDispatch}.
 */
sealed interface QueuedEvent permits BookingEventMessage, DebtEventMessage {

    String eventId();

    NotificationToDispatch toDispatch();

    /** Para el log: "BOOKING_CONFIRMED de la reserva 12". Con ids, nunca datos del conductor. */
    String describe();
}
