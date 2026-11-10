package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingDeposit;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargerReading;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ConnectorOperationalStatus;
import com.ecopedia.charging.session.domain.ConnectorStateRepository;
import com.ecopedia.charging.session.domain.SessionEndReason;
import com.ecopedia.charging.session.domain.SessionPaymentStatus;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * El cierre de una sesión en la transacción local (RF11, RNF06, ECO-44): la sesión, el conector y la
 * reserva cambian juntos, contra la base de verdad.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ChargingSessionCompletionTest {

    private static final Long CONNECTOR_ID = 301L;
    private static final Long DRIVER_ID = 131L;

    @Autowired
    private ChargingTransactionalCoordinator coordinator;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConnectorStateRepository connectorStateRepository;

    @Autowired
    private ChargingSessionRepository sessionRepository;

    @BeforeEach
    void cleanUp() {
        connectorStateRepository.deleteById(CONNECTOR_ID);
    }

    private ChargingSession activeSession(Instant start, Instant windowEnd) {
        Booking booking = bookingRepository.save(new Booking(
                CONNECTOR_ID,
                DRIVER_ID,
                new TimeWindow(start, windowEnd),
                start.minusSeconds(600),
                new BookingDeposit(new BigDecimal("3000.00"), 1L, "4242"),
                start.minusSeconds(300),
                UUID.randomUUID(),
                "checkout-key-" + UUID.randomUUID()));
        return coordinator.persistLocalActivation(
                UUID.randomUUID().toString(),
                booking.getId(),
                CONNECTOR_ID,
                DRIVER_ID,
                80,
                new BigDecimal("50.00"),
                start);
    }

    @Test
    @DisplayName(
            "Cerrar: sesión COMPLETED con lo que dijo el cargador, conector libre y reserva cerrada al instante del corte")
    void completionClosesSessionConnectorAndBooking() {
        Instant start = Instant.parse("2026-11-03T15:00:00Z");
        Instant windowEnd = start.plusSeconds(3600);
        Instant stop = start.plusSeconds(1500);
        ChargingSession session = activeSession(start, windowEnd);

        Optional<ChargingSession> closed = coordinator.persistLocalCompletion(
                session.getSessionId(),
                new ChargerReading(new BigDecimal("18.400"), new BigDecimal("72.5")),
                SessionEndReason.DRIVER_STOPPED,
                stop);

        assertThat(closed).isPresent();
        ChargingSession stored =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(stored.getEndReason()).isEqualTo(SessionEndReason.DRIVER_STOPPED);
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("18.400");
        assertThat(stored.getBatteryPercent()).isEqualByComparingTo("72.5");
        assertThat(stored.getStoppedAt()).isEqualTo(stop);

        assertThat(connectorStateRepository.findById(CONNECTOR_ID).orElseThrow().getStatus())
                .isEqualTo(ConnectorOperationalStatus.AVAILABLE);

        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        // Lo que quedaba de la ventana vuelve a estar libre.
        assertThat(booking.getWindow().end()).isEqualTo(stop);
    }

    @Test
    @DisplayName("Cerrar dos veces: el segundo no toca nada y avisa que ya estaba cerrada")
    void secondCompletionIsNoOp() {
        Instant start = Instant.parse("2026-11-03T17:00:00Z");
        ChargingSession session = activeSession(start, start.plusSeconds(3600));

        coordinator.persistLocalCompletion(
                session.getSessionId(),
                new ChargerReading(new BigDecimal("5"), null),
                SessionEndReason.DRIVER_STOPPED,
                start.plusSeconds(600));
        Optional<ChargingSession> again = coordinator.persistLocalCompletion(
                session.getSessionId(),
                new ChargerReading(new BigDecimal("9"), null),
                SessionEndReason.TARGET_REACHED,
                start.plusSeconds(900));

        assertThat(again).isEmpty();
        ChargingSession stored =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(stored.getEndReason()).isEqualTo(SessionEndReason.DRIVER_STOPPED);
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Sin lectura del cargador, cierra igual con lo último que se sabía")
    void completionWithoutReadingKeepsLastKnownEnergy() {
        Instant start = Instant.parse("2026-11-03T19:00:00Z");
        ChargingSession session = activeSession(start, start.plusSeconds(3600));

        coordinator.persistLocalCompletion(
                session.getSessionId(), null, SessionEndReason.DRIVER_STOPPED, start.plusSeconds(300));

        ChargingSession stored =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("0");
        assertThat(connectorStateRepository.findById(CONNECTOR_ID).orElseThrow().isAvailable())
                .isTrue();
    }

    @Test
    @DisplayName("La liquidación y el cobro quedan anotados en la sesión")
    void settlementIsRecorded() {
        Instant start = Instant.parse("2026-11-03T21:00:00Z");
        ChargingSession session = activeSession(start, start.plusSeconds(3600));
        coordinator.persistLocalCompletion(
                session.getSessionId(),
                new ChargerReading(new BigDecimal("20"), null),
                SessionEndReason.DRIVER_STOPPED,
                start.plusSeconds(1200));

        ChargeSettlement settlement = new ChargeSettlement(
                new BigDecimal("3000.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("4000.00"),
                BigDecimal.ZERO,
                ChargeSettlement.Outcome.COMPLETED);
        coordinator.recordSettlement(session.getSessionId(), settlement, null);
        coordinator.recordSettlement(session.getSessionId(), null, SessionPaymentStatus.CHARGED);

        ChargingSession stored =
                sessionRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(stored.getRealCost()).isEqualByComparingTo("7000.00");
        assertThat(stored.getDepositApplied()).isEqualByComparingTo("3000.00");
        assertThat(stored.getAmountToCharge()).isEqualByComparingTo("4000.00");
        assertThat(stored.getPaymentStatus()).isEqualTo(SessionPaymentStatus.CHARGED);
    }
}
