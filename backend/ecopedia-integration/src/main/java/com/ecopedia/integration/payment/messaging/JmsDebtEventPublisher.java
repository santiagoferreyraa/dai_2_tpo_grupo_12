package com.ecopedia.integration.payment.messaging;

import com.ecopedia.integration.payment.domain.DebtEvent;
import com.ecopedia.integration.payment.domain.DebtEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link DebtEventPublisher} contra ActiveMQ Artemis: deja cada hecho de una deuda en la cola
 * {@code notifications.dispatch} (ECO-62), la misma en la que Reservas publica los de las reservas.
 *
 * <p><b>Cola y no tópico</b>, como fija ARQUITECTURA §3.3: cada aviso es una unidad de trabajo que
 * tiene que procesarse exactamente una vez.
 *
 * <p>El cuerpo va en JSON con el tipo {@value com.ecopedia.integration.config.JmsConfig#DEBT_EVENT_TYPE}
 * (ver {@code JmsConfig}), y además se copian dos datos a propiedades del mensaje, {@code eventType} y
 * {@code debtId}, para filtrar con un selector y leer de un vistazo en la consola de Artemis.
 *
 * <p><b>Sin broker, el aviso no se pierde</b>, igual que en Reservas (ECO-177): si la publicación
 * falla, se guarda en {@code pending_notifications} y {@link PendingNotificationRelay} lo reenvía
 * cuando el broker vuelve. Es un reenvío y no un outbox en la misma transacción que la deuda: Pagos
 * guarda sin transacción propia para no tener la base tomada mientras espera a la pasarela (ver
 * {@code DebtCollectionServiceImpl}). El hueco que queda es que {@code integration} se caiga justo
 * entre guardar la deuda y guardar el pendiente.
 */
@Component
public class JmsDebtEventPublisher implements DebtEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(JmsDebtEventPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final String queue;
    private final PendingNotificationRepository pendingNotifications;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JmsDebtEventPublisher(
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
    public void publish(DebtEvent event) {
        /*
         * El contrato del puerto: no lanzar. La deuda ya está guardada, así que el conductor
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
    void send(DebtEvent event) {
        jmsTemplate.convertAndSend(queue, event, message -> {
            message.setStringProperty("eventType", event.type().name());
            message.setLongProperty("debtId", event.debtId());
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

    private void keepForLater(DebtEvent event, String reason) {
        try {
            if (pendingNotifications.existsByEventId(event.eventId())) return;
            pendingNotifications.save(new PendingNotification(
                    event.eventId(),
                    event.type().name(),
                    event.debtId(),
                    objectMapper.writeValueAsString(event),
                    clock.instant()));
            log.warn(
                    "El aviso {} no salió ({}); queda pendiente y se reenvía cuando vuelva el broker",
                    event.eventId(),
                    reason);
        } catch (JsonProcessingException | RuntimeException failure) {
            // Ni cola ni base: el evento completo queda en el log para reconstruirlo a mano.
            log.warn("No se pudo publicar ni guardar el aviso; la deuda sigue en pie. Aviso: {}", event, failure);
        }
    }
}
