package com.ecopedia.async.notification.domain;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long notificationId) {
        super("El aviso " + notificationId + " no existe");
    }
}
