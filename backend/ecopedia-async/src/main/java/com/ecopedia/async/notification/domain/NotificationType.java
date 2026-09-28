package com.ecopedia.async.notification.domain;

/**
 * De qué avisa un aviso.
 *
 * <p>Los nombres coinciden con los de {@code BookingEventType} en {@code ecopedia-charging}, porque
 * llegan como texto en el mensaje. Son dos enums, uno por artefacto, y no uno compartido: ver
 * {@code JmsConfig}, que explica por qué los dos lados no comparten clases.
 */
public enum NotificationType {
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED
}
