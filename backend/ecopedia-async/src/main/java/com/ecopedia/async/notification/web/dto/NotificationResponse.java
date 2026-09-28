package com.ecopedia.async.notification.web.dto;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationType;
import java.time.Instant;

/**
 * Un aviso tal como lo recibe la pantalla.
 *
 * <p>Lleva el texto ya armado y además los datos sueltos —estación, ventana, reserva—: el texto es
 * para mostrar tal cual, y los datos para que la pantalla pueda enlazar el aviso con su reserva o
 * dibujarlo a su manera sin tener que desarmar una frase.
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Long bookingId,
        Long connectorId,
        Long stationId,
        String stationName,
        Instant windowStart,
        Instant windowEnd,
        Instant createdAt,
        boolean read) {

    public static NotificationResponse fromDomain(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getBookingId(),
                notification.getConnectorId(),
                notification.getStationId(),
                notification.getStationName(),
                notification.getWindowStart(),
                notification.getWindowEnd(),
                notification.getCreatedAt(),
                notification.isRead());
    }
}
