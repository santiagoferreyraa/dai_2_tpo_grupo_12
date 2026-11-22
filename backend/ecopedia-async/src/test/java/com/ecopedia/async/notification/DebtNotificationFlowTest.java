package com.ecopedia.async.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import com.ecopedia.async.notification.domain.NotificationType;
import com.ecopedia.async.notification.domain.StationDirectory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Un aviso de deuda de punta a punta (ECO-62): el mensaje que publica Pagos entra a la cola y termina en
 * el historial del conductor, sin reserva ni conector.
 *
 * <p>El mensaje se escribe a mano, en el formato con que lo publica Pagos: JSON con
 * {@code _type=debt-event}. Que Pagos lo publique así lo afirma {@code DebtEventQueueTest}, en
 * {@code ecopedia-integration}; acá, que así se consume.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DebtNotificationFlowTest {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private NotificationRepository repository;

    @MockitoBean
    private StationDirectory stationDirectory;

    @Value("${ecopedia.notifications.queue}")
    private String queue;

    @Test
    @DisplayName("Un debt-event termina en el historial, sin reserva y sin preguntarle a core por la estación")
    void debtEventEndsUpInTheHistory() throws InterruptedException {
        long driver = 901L;
        String json =
                """
                {"eventId":"debt-77-retry-1-failed","type":"DEBT_RETRY_FAILED","debtId":77,"driverId":%d,\
                "incurredAt":"2026-11-22T15:00:00Z","attempt":1,"maxAttempts":3,\
                "occurredAt":"2026-11-22T15:20:00Z","nextAttemptAt":"2026-11-22T15:40:00Z"}"""
                        .formatted(driver);
        jmsTemplate.send(queue, session -> {
            var message = session.createTextMessage(json);
            message.setStringProperty("_type", "debt-event");
            return message;
        });

        List<Notification> history = awaitHistory(driver);

        assertThat(history).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.DEBT_RETRY_FAILED);
            assertThat(notification.getTitle()).isEqualTo("No pudimos cobrar tu carga (reintento 1 de 3)");
            assertThat(notification.getBody()).startsWith("Volvimos a intentar cobrar la carga del 22/11");
            assertThat(notification.getBookingId()).isNull();
            assertThat(notification.getConnectorId()).isNull();
        });
        verify(stationDirectory, never()).findByConnector(any());
    }

    private List<Notification> awaitHistory(long driver) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        List<Notification> history = repository.findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(driver);
        while (history.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            history = repository.findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(driver);
        }
        return history;
    }
}
