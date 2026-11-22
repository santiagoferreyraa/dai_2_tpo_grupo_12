package com.ecopedia.charging.booking.messaging;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.booking.domain.BookingEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link BookingEventPublisher} contra ActiveMQ Artemis: deja cada hecho de una reserva en la cola
 * {@code notifications.dispatch}.
 *
 * <p><b>Cola y no tópico</b>, como fija ARQUITECTURA §3.3: cada aviso es una unidad de trabajo que
 * tiene que procesarse exactamente una vez. Con un tópico, dos instancias de Notificaciones
 * mandarían dos veces el mismo mail.
 *
 * <p>El cuerpo va en JSON (ver {@code JmsConfig}) y además se copian dos datos a propiedades del
 * mensaje, {@code eventType} y {@code bookingId}. No los necesita el consumidor, que lee el
 * cuerpo: sirven para filtrar con un selector y para leer de un vistazo, en la consola de
 * Artemis, qué mensaje es cuál.
 *
 * <p><b>Sin broker, el aviso no se pierde</b> (ECO-177, QA-27). Si la publicación falla, el aviso
 * se guarda en {@code pending_notifications} y {@link PendingNotificationRelay} lo reenvía cuando el
 * broker vuelve. Es un reenvío y no un outbox con la misma transacción que la reserva: la reserva
 * se guarda sin {@code @Transactional} por el candado de las retenciones (ver
 * {@code BookingServiceImpl}). El hueco que queda es que {@code charging} se caiga justo entre
 * guardar la reserva y guardar el pendiente.
 */
@Component
public class JmsBookingEventPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(JmsBookingEventPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final String queue;
    private final PendingNotificationRepository pendingNotifications;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JmsBookingEventPublisher(
            JmsTemplate jmsTemplate,
            @Value("${ecopedia.notifications.queue}") String queue,
            PendingNotificationRepository pendingNotifications,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jmsTemplate = jmsTemplate;
        this.queue = queue;
        this.pendingNotifications = pendingNotifications;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publish(BookingEvent event) {
        /*
         * El contrato del puerto: no lanzar. La reserva ya está guardada, así que el conductor
         * tiene que recibir su respuesta normal aunque el broker esté caído (RNF06).
         */
        if (hasPending()) {
            // Si saliera ahora, llegaría antes que hechos anteriores que todavía esperan.
            keepForLater(event, "hay avisos anteriores esperando al broker");
            return;
        }
        try {
            send(event);
        } catch (RuntimeException failure) {
            keepForLater(event, failure.getMessage());
        }
    }

    /** Publica en la cola o lanza. Lo usan este publicador y el reenvío de pendientes. */
    void send(BookingEvent event) {
        jmsTemplate.convertAndSend(queue, event, message -> {
            message.setStringProperty("eventType", event.type().name());
            message.setLongProperty("bookingId", event.bookingId());
            return message;
        });
        log.info("Aviso {} publicado en la cola {}", event.eventId(), queue);
    }

    private boolean hasPending() {
        try {
            return pendingNotifications.count() > 0;
        } catch (RuntimeException databaseDown) {
            // Sin base no se puede saber; se intenta publicar, que es lo que se hacía antes.
            return false;
        }
    }

    private void keepForLater(BookingEvent event, String reason) {
        try {
            if (pendingNotifications.existsByEventId(event.eventId())) return;
            pendingNotifications.save(new PendingNotification(
                    event.eventId(),
                    event.type().name(),
                    event.bookingId(),
                    objectMapper.writeValueAsString(event),
                    clock.instant()));
            log.warn(
                    "El aviso {} no salió ({}); queda pendiente y se reenvía cuando vuelva el broker",
                    event.eventId(),
                    reason);
        } catch (JsonProcessingException | RuntimeException failure) {
            // Ni cola ni base: el evento completo queda en el log para reconstruirlo a mano.
            log.warn("No se pudo publicar ni guardar el aviso; la reserva sigue en pie. Aviso: {}", event, failure);
        }
    }
}
