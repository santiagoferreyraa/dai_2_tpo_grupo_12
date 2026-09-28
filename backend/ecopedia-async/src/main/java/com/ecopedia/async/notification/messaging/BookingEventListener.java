package com.ecopedia.async.notification.messaging;

import com.ecopedia.async.notification.domain.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * La entrada de Notificaciones: consume la cola {@code notifications.dispatch}.
 *
 * <p>Es la capa de presentación del componente, el equivalente a un controlador: recibe, traduce
 * al lenguaje del dominio y delega. No decide nada.
 *
 * <p><b>Qué pasa si algo falla adentro.</b> El contenedor del listener recibe dentro de una
 * sesión transaccional de JMS: si {@link NotificationService#dispatch} lanza —la base no
 * responde, por ejemplo—, el mensaje no se da por consumido y el broker lo vuelve a entregar.
 * Tras varios intentos fallidos Artemis lo aparta en su cola de mensajes muertos ({@code DLQ}),
 * donde se lo puede ver desde la consola. Un mensaje duplicado no es un fallo: lo descarta el
 * servicio, y no vuelve a la cola.
 */
@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final NotificationService notificationService;

    public BookingEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @JmsListener(destination = "${ecopedia.notifications.queue}")
    public void onBookingEvent(BookingEventMessage message) {
        log.info("Recibido de la cola: {} de la reserva {}", message.type(), message.bookingId());
        notificationService.dispatch(message.toDispatch());
    }
}
