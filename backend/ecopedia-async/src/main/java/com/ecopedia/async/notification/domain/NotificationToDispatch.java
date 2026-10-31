package com.ecopedia.async.notification.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que hay que avisar, tal como lo pide otro componente: destinatario, qué pasó, los datos del
 * hecho y la clave para no avisarlo dos veces. Es la entrada de {@link NotificationService} que
 * describe ARQUITECTURA §2.3.
 *
 * <p>Trae ids y no texto: el texto lo arma Notificaciones, que es la que sabe cómo se le habla al
 * conductor.
 */
public record NotificationToDispatch(
        String eventId,
        NotificationType type,
        Long recipientId,
        Long bookingId,
        Long connectorId,
        Instant windowStart,
        Instant windowEnd,
        BigDecimal depositAmount,
        Boolean depositRefunded,
        String cardLastFour,
        Instant depositRefundableUntil) {

    /** Un aviso de una reserva sin seña. */
    public NotificationToDispatch(
            String eventId,
            NotificationType type,
            Long recipientId,
            Long bookingId,
            Long connectorId,
            Instant windowStart,
            Instant windowEnd) {
        this(eventId, type, recipientId, bookingId, connectorId, windowStart, windowEnd, null, null, null, null);
    }

    /** Si la reserva tuvo seña. Un mensaje de antes de ECO-53 no la trae, y vale como sin seña. */
    public boolean hasDeposit() {
        return depositAmount != null && depositAmount.signum() > 0;
    }
}
