package com.ecopedia.async.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import com.ecopedia.async.notification.domain.StationDirectory;
import com.ecopedia.async.notification.domain.StationInfo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * El recorrido de Notificaciones de punta a punta, con el artefacto entero y un Artemis de
 * verdad —el embebido de los tests—: un mensaje entra a la cola y termina guardado en el historial.
 *
 * <p><b>El mensaje se escribe a mano, en el mismo formato con que lo publica Reservas</b>: JSON en
 * un {@code TextMessage} con {@code _type=booking-event}. Los dos artefactos corren en JVM distintas
 * y no pueden compartir el broker embebido de un test, así que el contrato se prueba de los dos
 * lados: {@code BookingEventMessagingTest}, en {@code ecopedia-charging}, afirma que el mensaje sale
 * así, y esta clase, que así se consume.
 *
 * <p>Core se reemplaza con un mock de {@link StationDirectory}: la prueba es de este artefacto.
 */
@SpringBootTest
@ActiveProfiles("dev")
class BookingNotificationFlowTest {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private NotificationRepository repository;

    @MockitoBean
    private StationDirectory stationDirectory;

    @Value("${ecopedia.notifications.queue}")
    private String queue;

    @BeforeEach
    void coreKnowsTheStation() {
        when(stationDirectory.findByConnector(anyLong()))
                .thenReturn(Optional.of(new StationInfo(3L, "Palermo Soho", "Honduras 4800", "CCS2")));
    }

    @Test
    @DisplayName("Un aviso publicado en la cola termina guardado en el historial del conductor")
    void messageEndsUpInTheHistory() throws InterruptedException {
        long driver = 801L;
        send("booking-900-BOOKING_CONFIRMED", "BOOKING_CONFIRMED", 900L, driver);

        List<Notification> history = awaitHistory(driver, 1);

        assertThat(history).singleElement().satisfies(notification -> {
            assertThat(notification.getTitle()).isEqualTo("Reserva confirmada");
            assertThat(notification.getBody()).contains("Palermo Soho");
            assertThat(notification.getBookingId()).isEqualTo(900L);
        });
    }

    @Test
    @DisplayName("El mismo mensaje entregado dos veces deja un solo aviso")
    void duplicateMessageLeavesOneNotification() throws InterruptedException {
        long driver = 802L;
        send("booking-901-BOOKING_CONFIRMED", "BOOKING_CONFIRMED", 901L, driver);
        send("booking-901-BOOKING_CONFIRMED", "BOOKING_CONFIRMED", 901L, driver);
        // Un tercero distinto, para saber cuándo terminó de procesar los dos anteriores.
        send("booking-901-BOOKING_CANCELLED", "BOOKING_CANCELLED", 901L, driver);

        List<Notification> history = awaitHistory(driver, 2);

        assertThat(history)
                .extracting(Notification::getEventId)
                .containsExactlyInAnyOrder("booking-901-BOOKING_CONFIRMED", "booking-901-BOOKING_CANCELLED");
    }

    /** Publica un mensaje igual al que publica {@code JmsBookingEventPublisher} en Reservas. */
    private void send(String eventId, String type, long bookingId, long driverId) {
        String json =
                """
                {"eventId":"%s","type":"%s","bookingId":%d,"driverId":%d,"connectorId":12,\
                "windowStart":"2026-09-30T13:00:00Z","windowEnd":"2026-09-30T14:00:00Z",\
                "occurredAt":"2026-09-28T15:00:00Z"}"""
                        .formatted(eventId, type, bookingId, driverId);

        jmsTemplate.send(queue, session -> {
            var message = session.createTextMessage(json);
            message.setStringProperty("_type", "booking-event");
            return message;
        });
    }

    /** El listener consume en otro hilo: se espera a que aparezcan los avisos, con un tope. */
    private List<Notification> awaitHistory(long driver, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        List<Notification> history = repository.findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(driver);
        while (history.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            history = repository.findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(driver);
        }
        return history;
    }
}
