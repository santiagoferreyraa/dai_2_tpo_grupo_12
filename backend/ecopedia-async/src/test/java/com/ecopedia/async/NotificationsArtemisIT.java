package com.ecopedia.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import jakarta.jms.TextMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;

/**
 * El consumidor de la cola `notifications.dispatch` contra un Artemis de verdad (QA-24).
 *
 * <p>El broker del contenedor arranca con el `broker.properties` del repo, el mismo que monta el
 * compose, así que también se prueban sus reglas tal como corren en el servidor: cinco intentos con
 * espera creciente y, después, la cola de mensajes muertos propia. Por eso la segunda prueba tarda
 * unos 30 segundos: son las esperas reales entre intentos (2, 4, 8 y 16 s).
 */
class NotificationsArtemisIT extends ContainersSupport {

    private static final SimpleString DEAD_LETTERS = SimpleString.of("DLQ.notifications.dispatch");

    private static final SimpleString GENERIC_DLQ = SimpleString.of("DLQ");

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private NotificationRepository notifications;

    @Value("${ecopedia.notifications.queue}")
    private String queue;

    @Test
    @DisplayName("Un aviso publicado en la cola del broker real termina guardado en PostgreSQL")
    void bookingEventEndsUpInTheHistory() {
        long driver = ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000);
        long booking = ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000);
        send(
                """
                {"eventId":"booking-%d-BOOKING_CONFIRMED","type":"BOOKING_CONFIRMED","bookingId":%d,\
                "driverId":%d,"connectorId":12,"windowStart":"2026-12-30T13:00:00Z",\
                "windowEnd":"2026-12-30T14:00:00Z","occurredAt":"2026-11-27T15:00:00Z"}"""
                        .formatted(booking, booking, driver),
                "booking-event");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Notification> history = notifications.findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(driver);
            assertThat(history).singleElement().satisfies(notification -> {
                assertThat(notification.getTitle()).isEqualTo("Reserva confirmada");
                assertThat(notification.getBody()).contains("Palermo Soho");
                assertThat(notification.getBookingId()).isEqualTo(booking);
            });
        });
    }

    @Test
    @DisplayName("Un mensaje ilegible, después de cinco intentos, queda en la cola de mensajes muertos propia")
    void unreadableMessageEndsInTheDeadLetterQueue() throws Exception {
        long before = messageCount(DEAD_LETTERS);
        long genericBefore = messageCount(GENERIC_DLQ);

        send("esto no es JSON", "booking-event");

        await().atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> messageCount(DEAD_LETTERS) == before + 1);
        assertThat(messageCount(GENERIC_DLQ))
                .as("sin una segunda copia en la DLQ común del broker")
                .isEqualTo(genericBefore);
    }

    private void send(String body, String type) {
        jmsTemplate.send(queue, session -> {
            TextMessage message = session.createTextMessage(body);
            message.setStringProperty("_type", type);
            return message;
        });
    }

    /**
     * Cuántos mensajes tiene una cola, preguntándole al broker sin consumir ni crear nada: leer con
     * JMS una cola que todavía no existe la crearía, y Artemis dejaría de usarla como DLQ.
     */
    private static long messageCount(SimpleString queueName) throws Exception {
        try (ServerLocator locator = ActiveMQClient.createServerLocator(ARTEMIS.getBrokerUrl());
                ClientSessionFactory factory = locator.createSessionFactory();
                ClientSession session =
                        factory.createSession(ARTEMIS.getUser(), ARTEMIS.getPassword(), false, true, true, false, 0)) {
            ClientSession.QueueQuery query = session.queueQuery(queueName);
            return query.isExists() ? query.getMessageCount() : 0;
        }
    }
}
