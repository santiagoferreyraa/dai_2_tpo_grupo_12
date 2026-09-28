package com.ecopedia.charging.booking.messaging;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.booking.domain.BookingEventPublisher;
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
 */
@Component
public class JmsBookingEventPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(JmsBookingEventPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final String queue;

    public JmsBookingEventPublisher(JmsTemplate jmsTemplate, @Value("${ecopedia.notifications.queue}") String queue) {
        this.jmsTemplate = jmsTemplate;
        this.queue = queue;
    }

    @Override
    public void publish(BookingEvent event) {
        try {
            jmsTemplate.convertAndSend(queue, event, message -> {
                message.setStringProperty("eventType", event.type().name());
                message.setLongProperty("bookingId", event.bookingId());
                return message;
            });
            log.info("Aviso {} publicado en la cola {}", event.eventId(), queue);
        } catch (RuntimeException failure) {
            /*
             * El contrato del puerto: no lanzar. La reserva ya está guardada, así que el conductor
             * tiene que recibir su respuesta normal aunque el broker esté caído (RNF06).
             *
             * El evento completo queda en el log, en WARN, para que el aviso que no salió se pueda
             * reconstruir y reenviar a mano. Una tabla de pendientes con reintento automático es la
             * versión robusta de esto y queda para la Entrega 4, junto con las transacciones.
             */
            log.warn(
                    "No se pudo publicar el aviso en la cola {}; la reserva sigue en pie. Aviso: {}",
                    queue,
                    event,
                    failure);
        }
    }
}
