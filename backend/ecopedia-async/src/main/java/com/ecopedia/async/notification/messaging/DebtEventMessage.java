package com.ecopedia.async.notification.messaging;

import com.ecopedia.async.notification.domain.DebtFacts;
import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
import java.time.Instant;

/**
 * El cuerpo JSON que publica Pagos ({@code DebtEvent} en {@code ecopedia-integration}), visto desde este
 * lado (ECO-62). Una copia del contrato, como {@link BookingEventMessage}: si Pagos renombra un campo,
 * este record deja de recibirlo.
 */
public record DebtEventMessage(
        String eventId,
        NotificationType type,
        Long debtId,
        Long driverId,
        Instant incurredAt,
        Integer attempt,
        Integer maxAttempts,
        Instant occurredAt,
        Instant nextAttemptAt)
        implements QueuedEvent {

    @Override
    public NotificationToDispatch toDispatch() {
        return NotificationToDispatch.ofDebt(
                eventId, type, driverId, new DebtFacts(incurredAt, attempt, maxAttempts, occurredAt, nextAttemptAt));
    }

    @Override
    public String describe() {
        return type + " de la deuda " + debtId;
    }
}
