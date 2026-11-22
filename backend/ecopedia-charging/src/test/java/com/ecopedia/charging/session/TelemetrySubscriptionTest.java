package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.ecopedia.charging.session.domain.ChargerEndReason;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ChargingSessionService;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.SessionStopReason;
import com.ecopedia.charging.session.domain.TelemetryReading;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * La telemetría de los cargadores, de punta a punta de este artefacto (RF16, ECO-40): lo que se
 * publica en el tópico {@code telemetry.charging} termina guardado en la sesión.
 *
 * <p>Con el artefacto entero, la base y un Artemis de verdad —el embebido de los tests—. El primer
 * test recorre el camino completo, con el mensaje tal como lo publica el cargador: JSON en un
 * {@code TextMessage} con el tipo lógico en {@code _type}. Es el contrato con el simulador, y lo
 * que se rompería en silencio si alguien cambiara el conversor o el nombre del tópico. Los demás
 * llaman al servicio directo: lo que prueban son las reglas de qué lectura se guarda, que viven en
 * la sentencia que corre contra la base.
 */
@SpringBootTest
@ActiveProfiles("dev")
class TelemetrySubscriptionTest {

    private static final Instant T0 = Instant.parse("2026-11-13T15:00:00Z");

    @Autowired
    private ChargingSessionService sessions;

    @Autowired
    private ChargingSessionRepository repository;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${ecopedia.telemetry.topic}")
    private String topic;

    @Test
    @DisplayName("Una lectura publicada en el tópico queda guardada en la sesión activa")
    void aReadingPublishedInTheTopicIsStoredInTheSession() {
        String sessionId = activeSession();
        String body =
                """
                {"eventId":"evt-%s","type":"READING","sessionId":"%s","connectorId":7,
                 "timestamp":"2026-11-13T15:00:10Z","powerKw":48.50,"energyKwh":3.250,
                 "batteryPercent":41.5,"endReason":null,"campoQueNoConocemos":"x"}
                """
                        .formatted(sessionId, sessionId);

        JmsTemplate publisher = new JmsTemplate(connectionFactory);
        publisher.setPubSubDomain(true);

        // Un tópico no guarda lo que se publica antes de que exista la suscripción, y el listener se
        // suscribe al arrancar, sin que el test pueda saber cuándo terminó. Por eso se publica hasta
        // que llega: guardar dos veces la misma lectura da lo mismo.
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    publisher.send(topic, jms -> {
                        TextMessage message = jms.createTextMessage(body);
                        message.setStringProperty("_type", "telemetry-event");
                        return message;
                    });
                    ChargingSession stored =
                            repository.findBySessionId(sessionId).orElseThrow();
                    assertThat(stored.getKwhDelivered()).isEqualByComparingTo("3.250");
                    assertThat(stored.getCurrentPowerKw()).isEqualByComparingTo("48.50");
                    assertThat(stored.getBatteryPercent()).isEqualByComparingTo("41.5");
                    assertThat(stored.getLastReadingAt()).isEqualTo(Instant.parse("2026-11-13T15:00:10Z"));
                    assertThat(stored.getStatus()).isEqualTo(SessionStatus.ACTIVE);
                    assertThat(stored.getChargerEndReason()).isNull();
                });
    }

    @Test
    @DisplayName("Una lectura atrasada no pisa a una más nueva")
    void aLateReadingDoesNotOverwriteANewerOne() {
        String sessionId = activeSession();

        assertThat(sessions.processTelemetry(reading(sessionId, 20, "5.000", "50.00", "60.0")))
                .isTrue();
        assertThat(sessions.processTelemetry(reading(sessionId, 10, "2.000", "49.00", "45.0")))
                .isFalse();

        ChargingSession stored = repository.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("5.000");
        assertThat(stored.getBatteryPercent()).isEqualByComparingTo("60.0");
        assertThat(stored.getLastReadingAt()).isEqualTo(T0.plusSeconds(20));
    }

    @Test
    @DisplayName("La misma lectura entregada dos veces deja la sesión igual")
    void theSameReadingTwiceLeavesTheSessionUnchanged() {
        String sessionId = activeSession();
        TelemetryReading reading = reading(sessionId, 10, "2.000", "49.00", "45.0");

        sessions.processTelemetry(reading);
        sessions.processTelemetry(reading);

        ChargingSession stored = repository.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("2.000");
        assertThat(stored.getLastReadingAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    @DisplayName("Los kWh son acumulados: una lectura posterior con menos kWh no los baja")
    void energyNeverGoesDown() {
        String sessionId = activeSession();

        sessions.processTelemetry(reading(sessionId, 10, "6.000", "50.00", "55.0"));
        sessions.processTelemetry(reading(sessionId, 20, "4.000", "30.00", "56.0"));

        ChargingSession stored = repository.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("6.000");
        // Lo demás sí es de la última lectura.
        assertThat(stored.getCurrentPowerKw()).isEqualByComparingTo("30.00");
        assertThat(stored.getBatteryPercent()).isEqualByComparingTo("56.0");
    }

    @Test
    @DisplayName("Una lectura de una sesión cerrada, o que no existe, se descarta sin error")
    void readingsForClosedOrUnknownSessionsAreDiscarded() {
        String sessionId = activeSession();
        ChargingSession session = repository.findBySessionId(sessionId).orElseThrow();
        session.stop(T0.plusSeconds(30), new BigDecimal("7.000"));
        repository.save(session);

        assertThat(sessions.processTelemetry(reading(sessionId, 40, "9.000", "50.00", "70.0")))
                .isFalse();
        assertThat(sessions.processTelemetry(reading("no-existe-" + UUID.randomUUID(), 40, "9.000", "50.00", "70.0")))
                .isFalse();

        ChargingSession stored = repository.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("7.000");
        assertThat(stored.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Si el cargador corta por batería llena, queda registrado y la sesión se cierra (ECO-45)")
    void aChargerThatStopsOnItsOwnIsRecordedAndTheSessionCloses() {
        // Objetivo 100%: la lectura del 97% no corta, y la que corta es el aviso del cargador.
        String sessionId = activeSession(100);
        sessions.processTelemetry(reading(sessionId, 10, "8.000", "50.00", "97.0"));
        assertThat(repository.findBySessionId(sessionId).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.ACTIVE);

        TelemetryReading last = new TelemetryReading(
                "evt-final",
                TelemetryReading.Type.FINISHED,
                sessionId,
                7L,
                T0.plusSeconds(20),
                BigDecimal.ZERO,
                new BigDecimal("8.400"),
                new BigDecimal("100.0"),
                ChargerEndReason.BATTERY_FULL);
        assertThat(sessions.processTelemetry(last)).isTrue();

        ChargingSession stored = repository.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getChargerEndReason()).isEqualTo(ChargerEndReason.BATTERY_FULL);
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("8.400");
        assertThat(stored.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(stored.getStopReason()).isEqualTo(SessionStopReason.TARGET_REACHED);
    }

    @Test
    @DisplayName("Una falla del cargador sin motivo explícito queda registrada como FAULT")
    void aFaultWithoutAnExplicitReasonIsRecordedAsFault() {
        String sessionId = activeSession();

        sessions.processTelemetry(new TelemetryReading(
                "evt-fault",
                TelemetryReading.Type.FAULT,
                sessionId,
                7L,
                T0.plusSeconds(15),
                BigDecimal.ZERO,
                new BigDecimal("1.500"),
                new BigDecimal("33.0"),
                null));

        ChargingSession stored = repository.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getChargerEndReason()).isEqualTo(ChargerEndReason.FAULT);
        assertThat(stored.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Una lectura sin sesión, sin instante o sin kWh se rechaza")
    void anIncompleteReadingIsRejected() {
        TelemetryReading withoutEnergy = new TelemetryReading(
                "evt-roto", TelemetryReading.Type.READING, activeSession(), 7L, T0, BigDecimal.TEN, null, null, null);

        assertThatThrownBy(() -> sessions.processTelemetry(withoutEnergy)).isInstanceOf(IllegalArgumentException.class);
    }

    private String activeSession() {
        return activeSession(80);
    }

    private String activeSession(int targetBatteryPercent) {
        String sessionId = UUID.randomUUID().toString();
        repository.save(
                ChargingSession.start(sessionId, null, 7L, 42L, targetBatteryPercent, new BigDecimal("50.00"), T0));
        return sessionId;
    }

    private static TelemetryReading reading(
            String sessionId, long secondsAfterStart, String energyKwh, String powerKw, String batteryPercent) {
        return new TelemetryReading(
                "evt-" + sessionId + "-" + secondsAfterStart,
                TelemetryReading.Type.READING,
                sessionId,
                7L,
                T0.plusSeconds(secondsAfterStart),
                new BigDecimal(powerKw),
                new BigDecimal(energyKwh),
                new BigDecimal(batteryPercent),
                null);
    }
}
