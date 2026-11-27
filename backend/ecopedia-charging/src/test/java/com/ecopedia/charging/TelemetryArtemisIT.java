package com.ecopedia.charging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.SessionStatus;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.TextMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;

/**
 * El consumidor del tópico `telemetry.charging` contra un Artemis de verdad (QA-24): lo que publica
 * el cargador viaja por el broker del contenedor, lo toma la suscripción durable y compartida de
 * SesionesDeCarga y termina guardado en la sesión, en PostgreSQL.
 */
class TelemetryArtemisIT extends ContainersSupport {

    private static final Instant STARTED = Instant.parse("2026-11-27T15:00:00Z");

    @Autowired
    private ChargingSessionRepository sessions;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${ecopedia.telemetry.topic}")
    private String topic;

    @Test
    @DisplayName("Una lectura publicada en el tópico del broker real queda guardada en la sesión")
    void readingTravelsThroughTheBroker() {
        String sessionId = UUID.randomUUID().toString();
        sessions.save(ChargingSession.start(sessionId, null, 7L, 42L, 80, new BigDecimal("50.00"), STARTED));
        // El mismo JSON que publica el simulador de cargadores (`telemetry-event`).
        String body =
                """
                {"eventId":"evt-%s","type":"READING","sessionId":"%s","connectorId":7,
                 "timestamp":"2026-11-27T15:00:10Z","powerKw":48.50,"energyKwh":3.250,
                 "batteryPercent":41.5,"endReason":null}
                """
                        .formatted(sessionId, sessionId);

        JmsTemplate publisher = new JmsTemplate(connectionFactory);
        publisher.setPubSubDomain(true);

        // Un tópico no guarda lo publicado antes de que exista la suscripción, y el listener se suscribe
        // al arrancar sin avisar cuándo terminó: se publica hasta que llega. La misma lectura dos veces
        // deja la sesión igual.
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    publisher.send(topic, session -> {
                        TextMessage message = session.createTextMessage(body);
                        message.setStringProperty("_type", "telemetry-event");
                        return message;
                    });
                    ChargingSession stored = sessions.findBySessionId(sessionId).orElseThrow();
                    assertThat(stored.getKwhDelivered()).isEqualByComparingTo("3.250");
                    assertThat(stored.getCurrentPowerKw()).isEqualByComparingTo("48.50");
                    assertThat(stored.getLastReadingAt()).isEqualTo(Instant.parse("2026-11-27T15:00:10Z"));
                    assertThat(stored.getStatus()).isEqualTo(SessionStatus.ACTIVE);
                });
    }
}
