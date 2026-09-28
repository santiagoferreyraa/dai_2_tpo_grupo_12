package com.ecopedia.async.notification.domain;

/**
 * Puerto de salida hacia el canal de mail.
 *
 * <p>Hoy lo implementa {@code SimulatedEmailSender}, que escribe el mail en el log: el canal es
 * ficticio a propósito, como la pasarela y la distribuidora. Lo real de este componente es el
 * broker, no el servidor de correo. Cambiar a un envío de verdad es otra implementación de este
 * puerto, sin tocar el servicio.
 */
public interface EmailSender {

    void send(Long recipientId, String subject, String body);
}
