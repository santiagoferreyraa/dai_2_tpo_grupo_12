package com.ecopedia.integration.payment.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.integration.payment.domain.DebtEvent;
import com.ecopedia.integration.payment.domain.DebtEventPublisher;
import com.ecopedia.integration.payment.domain.DebtEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Lo que de verdad sale por la cola, contra el broker embebido (ECO-62).
 *
 * <p>Es el contrato con Notificaciones, que vive en otro artefacto con su propia clase: el tipo
 * {@code debt-event} en la propiedad {@code _type} y los campos con estos nombres, con las fechas en
 * texto ISO-8601. Si alguien renombra un campo del record, este test lo dice antes que el aviso deje
 * de llegar en silencio.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DebtEventQueueTest {

    @Autowired
    private DebtEventPublisher publisher;

    @Autowired
    private JmsTemplate jms;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("El aviso de una deuda sale por notifications.dispatch como debt-event, en JSON")
    void publishesTheDebtEventOnTheQueue() throws Exception {
        Instant incurred = Instant.parse("2026-11-22T12:00:00Z");
        publisher.publish(new DebtEvent(
                "debt-9001-retry-1-failed",
                DebtEventType.DEBT_RETRY_FAILED,
                9001L,
                7L,
                incurred,
                1,
                3,
                incurred.plusSeconds(20 * 60),
                incurred.plusSeconds(40 * 60)));

        jms.setReceiveTimeout(5_000);
        // Con selector: otros tests del mismo contexto también publican en esta cola.
        Message message = jms.receiveSelected("notifications.dispatch", "debtId = 9001");

        assertThat(message).isInstanceOf(TextMessage.class);
        assertThat(message.getStringProperty("_type")).isEqualTo("debt-event");
        assertThat(message.getStringProperty("eventType")).isEqualTo("DEBT_RETRY_FAILED");

        JsonNode body = json.readTree(((TextMessage) message).getText());
        assertThat(body.get("eventId").asText()).isEqualTo("debt-9001-retry-1-failed");
        assertThat(body.get("type").asText()).isEqualTo("DEBT_RETRY_FAILED");
        assertThat(body.get("driverId").asLong()).isEqualTo(7L);
        assertThat(body.get("incurredAt").asText()).isEqualTo("2026-11-22T12:00:00Z");
        assertThat(body.get("attempt").asInt()).isEqualTo(1);
        assertThat(body.get("maxAttempts").asInt()).isEqualTo(3);
        assertThat(body.get("occurredAt").asText()).isEqualTo("2026-11-22T12:20:00Z");
        assertThat(body.get("nextAttemptAt").asText()).isEqualTo("2026-11-22T12:40:00Z");
    }
}
