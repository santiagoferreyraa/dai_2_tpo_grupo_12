package com.ecopedia.charging.booking.messaging;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reenvía los avisos que no salieron porque el broker no estaba (ECO-177, QA-27).
 *
 * <p>Cada {@code ecopedia.notifications.retry-interval} (15 s) toma los pendientes del más viejo al
 * más nuevo y los publica. Al primero que falla corta: si el broker sigue caído, seguir sería
 * fallar cien veces seguidas, y publicar los siguientes rompería el orden. No hay límite de
 * intentos: un aviso pendiente no se descarta nunca.
 *
 * <p>Si el aviso sale y después no se puede borrar de la tabla, el próximo barrido lo vuelve a
 * publicar. No pasa nada: Notificaciones descarta lo repetido por {@code eventId}.
 */
@Component
public class PendingNotificationRelay {

    private static final Logger log = LoggerFactory.getLogger(PendingNotificationRelay.class);

    private final PendingNotificationRepository pendingNotifications;
    private final JmsBookingEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public PendingNotificationRelay(
            PendingNotificationRepository pendingNotifications,
            JmsBookingEventPublisher publisher,
            ObjectMapper objectMapper) {
        this.pendingNotifications = pendingNotifications;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
            fixedDelayString = "${ecopedia.notifications.retry-interval}",
            initialDelayString = "${ecopedia.notifications.retry-interval}")
    void relayOnSchedule() {
        relay();
    }

    /**
     * Un barrido. Visible para los tests, que no esperan al reloj.
     *
     * @return cuántos avisos salieron
     */
    public int relay() {
        List<PendingNotification> batch;
        try {
            batch = pendingNotifications.findTop100ByOrderByCreatedAtAscIdAsc();
        } catch (RuntimeException databaseDown) {
            // La base caída no tiene que matar el reloj: el próximo barrido lo vuelve a intentar.
            log.warn("No se pudo leer los avisos pendientes: {}", databaseDown.getMessage());
            return 0;
        }

        int sent = 0;
        for (PendingNotification pending : batch) {
            BookingEvent event;
            try {
                event = objectMapper.readValue(pending.getPayload(), BookingEvent.class);
            } catch (JsonProcessingException unreadable) {
                // No debería pasar: lo escribió este mismo artefacto. Se descarta con el contenido en el log.
                log.error(
                        "El aviso pendiente {} no se puede leer y se descarta: {}",
                        pending.getEventId(),
                        pending.getPayload());
                pendingNotifications.delete(pending);
                continue;
            }
            try {
                publisher.send(event);
            } catch (RuntimeException brokerDown) {
                pending.failedAgain(brokerDown.getMessage());
                pendingNotifications.save(pending);
                log.warn(
                        "El broker sigue sin responder: quedan {} avisos pendientes; el más viejo, {}, lleva {} intentos",
                        batch.size() - sent,
                        pending.getEventId(),
                        pending.getAttempts());
                break;
            }
            pendingNotifications.delete(pending);
            sent++;
        }
        if (sent > 0) log.info("Se reenviaron {} avisos pendientes", sent);
        return sent;
    }
}
