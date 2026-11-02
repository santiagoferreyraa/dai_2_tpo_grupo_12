package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingDeposit;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ConnectorOperationalStatus;
import com.ecopedia.charging.session.domain.ConnectorState;
import com.ecopedia.charging.session.domain.ConnectorStateRepository;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class ChargingTransactionalCoordinatorTest {

    @Autowired
    private ChargingTransactionalCoordinator coordinator;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConnectorStateRepository connectorStateRepository;

    @Autowired
    private ChargingSessionRepository chargingSessionRepository;

    private static final Long CONNECTOR_ID = 201L;
    private static final Long DRIVER_ID = 101L;

    @BeforeEach
    void cleanUp() {
        connectorStateRepository.deleteById(CONNECTOR_ID);
    }

    @Nested
    @DisplayName("Persistencia atómica de la habilitación (RNF06, ECO-60)")
    class DeclarativeActivationTransactions {

        @Test
        @DisplayName("Habilitación exitosa: reserva pasa a IN_PROGRESS, conector a OCCUPIED y sesión se crea ACTIVE")
        void successfulActivationUpdatesAllThreeStatesAtomically() {
            Instant now = Instant.parse("2026-11-02T15:00:00Z");
            TimeWindow window = new TimeWindow(now, now.plusSeconds(3600));

            Booking booking = new Booking(
                    CONNECTOR_ID,
                    DRIVER_ID,
                    window,
                    now.minusSeconds(600),
                    new BookingDeposit(new BigDecimal("5000.00"), 1L, "4242"),
                    now.minusSeconds(300),
                    UUID.randomUUID(),
                    "checkout-key-1");
            Booking savedBooking = bookingRepository.save(booking);

            String sessionId = UUID.randomUUID().toString();

            ChargingSession session = coordinator.persistLocalActivation(
                    sessionId, savedBooking.getId(), CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), now);

            assertThat(session).isNotNull();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(session.getPowerLimitKw()).isEqualByComparingTo("50.00");

            Booking updatedBooking =
                    bookingRepository.findById(savedBooking.getId()).orElseThrow();
            assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);

            ConnectorState connectorState =
                    connectorStateRepository.findById(CONNECTOR_ID).orElseThrow();
            assertThat(connectorState.getStatus()).isEqualTo(ConnectorOperationalStatus.OCCUPIED);
            assertThat(connectorState.getCurrentSessionId()).isEqualTo(sessionId);
        }

        @Test
        @DisplayName(
                "Si el conector ya está OCCUPIED, la transacción falla y la reserva queda en CONFIRMED (sin sesión)")
        void occupiedConnectorAbortsTransactionAndRollsBackBooking() {
            Instant now = Instant.parse("2026-11-02T16:00:00Z");
            TimeWindow window = new TimeWindow(now, now.plusSeconds(3600));

            // Conector ya ocupado por otra sesión
            ConnectorState busyState = ConnectorState.available(CONNECTOR_ID, now);
            busyState.occupy("other-session", now);
            connectorStateRepository.save(busyState);

            Booking booking = new Booking(
                    CONNECTOR_ID,
                    DRIVER_ID,
                    window,
                    now.minusSeconds(600),
                    BookingDeposit.none(),
                    null,
                    UUID.randomUUID(),
                    "checkout-key-busy");
            Booking savedBooking = bookingRepository.save(booking);

            String sessionId = UUID.randomUUID().toString();

            assertThatThrownBy(() -> coordinator.persistLocalActivation(
                            sessionId, savedBooking.getId(), CONNECTOR_ID, DRIVER_ID, 90, new BigDecimal("22.00"), now))
                    .isInstanceOf(SlotUnavailableException.class);

            // Verificamos que la reserva NO quedó en IN_PROGRESS
            Booking reloadedBooking =
                    bookingRepository.findById(savedBooking.getId()).orElseThrow();
            assertThat(reloadedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

            // Y que la nueva sesión no fue persistida
            assertThat(chargingSessionRepository.findBySessionId(sessionId)).isEmpty();
        }

        @Test
        @DisplayName("Si la reserva no está en estado CONFIRMED, se cancela la transacción y el conector no se ocupa")
        void invalidBookingStatusAbortsTransactionAndLeavesConnectorAvailable() {
            Instant now = Instant.parse("2026-11-02T17:00:00Z");
            TimeWindow window = new TimeWindow(now, now.plusSeconds(3600));

            // Conector inicialmente disponible
            ConnectorState state = ConnectorState.available(CONNECTOR_ID, now);
            connectorStateRepository.save(state);

            Booking booking = new Booking(
                    CONNECTOR_ID,
                    DRIVER_ID,
                    window,
                    now.minusSeconds(600),
                    BookingDeposit.none(),
                    null,
                    UUID.randomUUID(),
                    "checkout-key-cancelled");
            booking.cancel(now);
            Booking savedBooking = bookingRepository.save(booking);

            String sessionId = UUID.randomUUID().toString();

            assertThatThrownBy(() -> coordinator.persistLocalActivation(
                            sessionId, savedBooking.getId(), CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("22.00"), now))
                    .isInstanceOf(InvalidBookingRequestException.class);

            // Verificamos que el conector NO fue marcado como OCCUPIED
            ConnectorState reloadedConnector =
                    connectorStateRepository.findById(CONNECTOR_ID).orElseThrow();
            assertThat(reloadedConnector.getStatus()).isEqualTo(ConnectorOperationalStatus.AVAILABLE);

            // Y la sesión no fue creada
            assertThat(chargingSessionRepository.findBySessionId(sessionId)).isEmpty();
        }

        @Test
        @DisplayName("Compensación local: libera el conector y marca la sesión como FAULTED")
        void compensationMarksSessionFaultedAndReleasesConnector() {
            Instant now = Instant.parse("2026-11-02T18:00:00Z");
            String sessionId = UUID.randomUUID().toString();

            ChargingSession session = coordinator.persistLocalActivation(
                    sessionId, null, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("22.00"), now);

            coordinator.compensateLocalFailure(CONNECTOR_ID, sessionId, now.plusSeconds(10));

            ChargingSession faultedSession =
                    chargingSessionRepository.findBySessionId(sessionId).orElseThrow();
            assertThat(faultedSession.getStatus()).isEqualTo(SessionStatus.FAULTED);

            ConnectorState connectorState =
                    connectorStateRepository.findById(CONNECTOR_ID).orElseThrow();
            assertThat(connectorState.getStatus()).isEqualTo(ConnectorOperationalStatus.AVAILABLE);
            assertThat(connectorState.getCurrentSessionId()).isNull();
        }
    }
}
