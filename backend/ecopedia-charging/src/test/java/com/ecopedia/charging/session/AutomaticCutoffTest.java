package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ChargingSessionService;
import com.ecopedia.charging.session.domain.ConnectorStateRepository;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.SessionStopReason;
import com.ecopedia.charging.session.domain.TelemetryReading;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.TextMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * El corte automático contra la base de verdad (RF12, ECO-45): una lectura de telemetría que cumple
 * la primera de las dos condiciones cierra la sesión, libera el conector y recorta la reserva.
 *
 * <p>El cargador y Tarificación no corren en los tests: apagar el cargador falla y se sigue con la
 * telemetría, y el cobro queda pendiente. Es lo que tiene que pasar —el corte no puede depender de un
 * tercero (RNF06)— y lo que se prueba acá es el cierre, no la liquidación.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AutomaticCutoffTest {

    /** Un conector por test, para que el estado de uno no le ocupe la terminal al otro. */
    private static final AtomicLong NEXT_CONNECTOR = new AtomicLong(4_500);

    private static final Long DRIVER_ID = 451L;

    @Autowired
    private ChargingSessionService sessions;

    @Autowired
    private ChargingSessionRepository sessionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConnectorStateRepository connectorStates;

    @Autowired
    private ChargingTransactionalCoordinator coordinator;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${ecopedia.telemetry.topic}")
    private String telemetryTopic;

    /** Una sesión habilitada de verdad: reserva en curso, conector ocupado, sesión activa. */
    private ChargingSession activeSession(long connectorId, int target, Instant windowStart, Instant windowEnd) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Booking booking = bookingRepository.save(
                new Booking(connectorId, DRIVER_ID, new TimeWindow(windowStart, windowEnd), now.minusSeconds(3600)));
        return coordinator.persistLocalActivation(
                UUID.randomUUID().toString(),
                booking.getId(),
                connectorId,
                DRIVER_ID,
                target,
                new BigDecimal("50.00"),
                now);
    }

    private static TelemetryReading reading(ChargingSession session, Instant at, String kwh, String battery) {
        return new TelemetryReading(
                "evt-" + UUID.randomUUID(),
                TelemetryReading.Type.READING,
                session.getSessionId(),
                session.getConnectorId(),
                at,
                new BigDecimal("50.00"),
                new BigDecimal(kwh),
                new BigDecimal(battery),
                null);
    }

    @Test
    @DisplayName("Llega al objetivo antes del fin: corta, libera el conector y libera el resto de la ventana")
    void targetReachedClosesAndReleasesTheRemainder() {
        long connectorId = NEXT_CONNECTOR.incrementAndGet();
        Instant now = Instant.now();
        Instant windowEnd = now.plusSeconds(3000);
        ChargingSession session = activeSession(connectorId, 80, now.minusSeconds(600), windowEnd);

        sessions.processTelemetry(reading(session, now.plusSeconds(1), "10.000", "60.0"));
        assertThat(sessionRepository
                        .findBySessionId(session.getSessionId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(SessionStatus.ACTIVE);

        sessions.processTelemetry(reading(session, now.plusSeconds(3), "18.000", "80.5"));

        ChargingSession closed =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(closed.getStopReason()).isEqualTo(SessionStopReason.TARGET_REACHED);
        assertThat(closed.getKwhDelivered()).isEqualByComparingTo("18.000");
        assertThat(closed.getPaymentStatus()).isNotNull();

        assertThat(connectorStates.findById(connectorId).orElseThrow().isAvailable())
                .isTrue();

        // RF12: el resto de la ventana ya no está tomado; quien quiera cargar ahí, puede.
        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getWindow().end()).isBefore(windowEnd);
        Instant remainder = booking.getWindow().end().plusSeconds(1);
        assertThat(bookingRepository.existsOverlapping(connectorId, BookingStatus.IN_PROGRESS, remainder, windowEnd))
                .isFalse();
        assertThat(bookingRepository.existsOverlapping(connectorId, BookingStatus.CONFIRMED, remainder, windowEnd))
                .isFalse();
    }

    /**
     * DEF-21, tal como se reportó: objetivo 80 %, la telemetría del cargador informa exactamente ese
     * porcentaje y la carga tiene que cortarse ahí, no seguir hasta el fin de la ventana. Recorre el
     * camino completo —el mensaje publicado en {@code telemetry.charging}, el listener, el corte—
     * porque el defecto era justamente que nadie consumía el tópico.
     */
    @Test
    @DisplayName("DEF-21: una lectura del 80 % publicada en el tópico corta una carga con objetivo 80 %")
    void def21TargetReadingFromTheTopicCutsTheSession() {
        long connectorId = NEXT_CONNECTOR.incrementAndGet();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ChargingSession session = activeSession(connectorId, 80, now.minusSeconds(600), now.plusSeconds(3000));
        String body =
                """
                {"eventId":"evt-%s","type":"READING","sessionId":"%s","connectorId":%d,
                 "timestamp":"%s","powerKw":50.00,"energyKwh":20.000,"batteryPercent":80.0,"endReason":null}
                """
                        .formatted(UUID.randomUUID(), session.getSessionId(), connectorId, now.plusSeconds(5));

        JmsTemplate publisher = new JmsTemplate(connectionFactory);
        publisher.setPubSubDomain(true);

        // El tópico no guarda lo publicado antes de que el listener se suscriba: se publica hasta que
        // llega. Una lectura repetida después del corte se descarta (ver readingsAfterTheCutAreDiscarded).
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    publisher.send(telemetryTopic, jms -> {
                        TextMessage message = jms.createTextMessage(body);
                        message.setStringProperty("_type", "telemetry-event");
                        return message;
                    });
                    ChargingSession closed = sessionRepository
                            .findBySessionId(session.getSessionId())
                            .orElseThrow();
                    assertThat(closed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
                    assertThat(closed.getStopReason()).isEqualTo(SessionStopReason.TARGET_REACHED);
                });

        assertThat(connectorStates.findById(connectorId).orElseThrow().isAvailable())
                .isTrue();
        assertThat(bookingRepository
                        .findById(session.getBookingId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("DEF-21: una carga sin reserva también se corta al llegar al objetivo")
    void walkInSessionIsCutAtTheTarget() {
        long connectorId = NEXT_CONNECTOR.incrementAndGet();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ChargingSession session = coordinator.persistLocalActivation(
                UUID.randomUUID().toString(), null, connectorId, DRIVER_ID, 60, new BigDecimal("22.00"), now);

        sessions.processTelemetry(reading(session, now.plusSeconds(2), "4.000", "59.9"));
        assertThat(sessionRepository
                        .findBySessionId(session.getSessionId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(SessionStatus.ACTIVE);

        sessions.processTelemetry(reading(session, now.plusSeconds(4), "4.200", "60.0"));

        ChargingSession closed =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(closed.getStopReason()).isEqualTo(SessionStopReason.TARGET_REACHED);
        assertThat(closed.getKwhDelivered()).isEqualByComparingTo("4.200");
        assertThat(connectorStates.findById(connectorId).orElseThrow().isAvailable())
                .isTrue();
    }

    @Test
    @DisplayName("Una lectura del fin de la ventana corta aunque no haya llegado al objetivo")
    void windowEndCutsBelowTheTarget() {
        long connectorId = NEXT_CONNECTOR.incrementAndGet();
        Instant now = Instant.now();
        Instant windowEnd = now.plusSeconds(60);
        ChargingSession session = activeSession(connectorId, 90, now.minusSeconds(1200), windowEnd);

        sessions.processTelemetry(reading(session, windowEnd, "22.000", "64.0"));

        ChargingSession closed =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(closed.getStopReason()).isEqualTo(SessionStopReason.WINDOW_ENDED);
        assertThat(connectorStates.findById(connectorId).orElseThrow().isAvailable())
                .isTrue();
    }

    @Test
    @DisplayName("Una lectura que llega después del corte se descarta y no vuelve a liquidar")
    void readingsAfterTheCutAreDiscarded() {
        long connectorId = NEXT_CONNECTOR.incrementAndGet();
        Instant now = Instant.now();
        ChargingSession session = activeSession(connectorId, 50, now.minusSeconds(600), now.plusSeconds(3000));

        sessions.processTelemetry(reading(session, now.plusSeconds(2), "9.000", "51.0"));
        ChargingSession closed =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();

        assertThat(sessions.processTelemetry(reading(session, now.plusSeconds(4), "9.200", "52.0")))
                .isFalse();

        ChargingSession again =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(again.getKwhDelivered()).isEqualByComparingTo(closed.getKwhDelivered());
        assertThat(again.getStoppedAt()).isEqualTo(closed.getStoppedAt());
        assertThat(again.getVersion()).isEqualTo(closed.getVersion());
    }

    @Test
    @DisplayName("Detener después del corte devuelve el corte, con su motivo, sin cobrar de nuevo")
    void driverStopAfterTheCutReturnsTheCut() {
        long connectorId = NEXT_CONNECTOR.incrementAndGet();
        Instant now = Instant.now();
        ChargingSession session = activeSession(connectorId, 70, now.minusSeconds(600), now.plusSeconds(3000));
        sessions.processTelemetry(reading(session, now.plusSeconds(2), "12.000", "75.0"));

        var result = sessions.stopSession(session.getSessionId(), DRIVER_ID);

        assertThat(result.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(result.stopReason()).isEqualTo(SessionStopReason.TARGET_REACHED);
    }
}
