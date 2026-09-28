package com.ecopedia.async.notification.domain;

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
        Instant windowEnd) {}
