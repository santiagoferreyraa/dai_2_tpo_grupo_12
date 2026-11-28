package com.ecopedia.async.notification.domain;

public class NotificationNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotificationNotFoundException(Long notificationId) {
        super("El aviso " + notificationId + " no existe");
    }
}
