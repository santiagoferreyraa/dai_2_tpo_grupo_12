package com.ecopedia.async.notification.messaging;

import com.ecopedia.async.notification.domain.NotificationService;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

/**
 * La entrada de Notificaciones: consume la cola {@code notifications.dispatch}.
 *
 * <p>Es la capa de presentación del componente, el equivalente a un controlador: recibe, traduce
 * al lenguaje del dominio y delega. No decide nada.
 *
 * <p><b>Qué pasa si algo falla adentro.</b> El contenedor del listener recibe dentro de una
 * sesión transaccional de JMS: si leer el mensaje o {@link NotificationService#dispatch} lanza —la
 * base no responde, un JSON roto—, el mensaje no se da por consumido y el broker lo vuelve a
 * entregar, con espera creciente. Al quinto fallo Artemis lo aparta en
 * {@code DLQ.notifications.dispatch}, de donde se lo reenvía a la cola desde la consola (QA-28; la
 * regla está en {@code docker/artemis/broker.properties}). Un mensaje duplicado no es un fallo: lo
 * descarta el servicio, y no vuelve a la cola.
 *
 * <p><b>Por qué recibe el {@link Message} crudo y no el record ya convertido.</b> Para que un
 * mensaje ilegible también pase por acá y deje su registro, y para leer en qué intento va
 * ({@code JMSXDeliveryCount}): con el record, la conversión fallaría antes de llegar al método y
 * solo quedaría el WARN genérico del contenedor, sin decir qué mensaje era ni que va a la DLQ.
 */
@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    /** Lo pone Artemis en cada entrega: 1 la primera vez, 2 en el primer reintento, y así. */
    private static final String DELIVERY_COUNT = "JMSXDeliveryCount";

    private final NotificationService notificationService;
    private final MessageConverter converter;
    private final String queue;
    private final int maxDeliveryAttempts;

    public BookingEventListener(
            NotificationService notificationService,
            MessageConverter converter,
            @Value("${ecopedia.notifications.queue}") String queue,
            @Value("${ecopedia.notifications.max-delivery-attempts}") int maxDeliveryAttempts) {
        this.notificationService = notificationService;
        this.converter = converter;
        this.queue = queue;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    @JmsListener(destination = "${ecopedia.notifications.queue}")
    public void onBookingEvent(Message message) throws JMSException {
        int attempt = attemptOf(message);

        BookingEventMessage event;
        try {
            event = read(message);
        } catch (JMSException | RuntimeException unreadable) {
            // Un mensaje mandado a mano desde la consola de Artemis no trae id de JMS.
            String jmsId = message.getJMSMessageID();
            reportFailure("ilegible " + (jmsId != null ? jmsId : "(sin id de JMS)"), attempt, unreadable);
            throw unreadable;
        }

        log.info("Recibido de la cola: {} de la reserva {}", event.type(), event.bookingId());
        try {
            notificationService.dispatch(event.toDispatch());
        } catch (RuntimeException failure) {
            reportFailure(event.eventId(), attempt, failure);
            throw failure;
        }
    }

    private BookingEventMessage read(Message message) throws JMSException {
        Object body = converter.fromMessage(message);
        if (body instanceof BookingEventMessage event) {
            return event;
        }
        throw new MessageConversionException(
                "Se esperaba un booking-event y llegó " + body.getClass().getName());
    }

    /**
     * Un WARN por cada intento que falla y un ERROR en el último, que es el que Artemis manda a la
     * DLQ. Con ids y la causa, nunca el cuerpo: el cuerpo trae datos del conductor.
     */
    private void reportFailure(String messageId, int attempt, Exception failure) {
        if (attempt >= maxDeliveryAttempts) {
            log.error(
                    "El mensaje {} falló en el intento {} de {} y pasa a la cola de mensajes muertos DLQ.{};"
                            + " se reenvía desde la consola de Artemis. Causa: {}",
                    messageId,
                    attempt,
                    maxDeliveryAttempts,
                    queue,
                    failure.toString());
        } else {
            log.warn(
                    "El mensaje {} falló en el intento {} de {}; Artemis lo vuelve a entregar. Causa: {}",
                    messageId,
                    attempt,
                    maxDeliveryAttempts,
                    failure.toString());
        }
    }

    private static int attemptOf(Message message) throws JMSException {
        return message.propertyExists(DELIVERY_COUNT) ? message.getIntProperty(DELIVERY_COUNT) : 1;
    }
}
