package com.ecopedia.async.notification.domain;

/**
 * Alguien intentó tocar un aviso que no es suyo.
 *
 * <p>Responde 403, igual que una reserva ajena en Reservas: el rol no alcanza, porque la regla no
 * es "quién sos" sino "de quién es este aviso", y eso lo sabe el servicio, no la anotación.
 */
public class NotificationAccessDeniedException extends RuntimeException {

    public NotificationAccessDeniedException(Long notificationId) {
        super("El aviso " + notificationId + " no es tuyo");
    }
}
