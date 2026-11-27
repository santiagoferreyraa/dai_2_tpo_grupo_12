package com.ecopedia.charging;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.session.domain.ChargerEndReason;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.SessionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Las consultas propias de charging contra PostgreSQL de verdad (QA-24): el cruce de reservas, que
 * decide si un horario está libre, y la lectura de telemetría, que es una sentencia condicional
 * escrita a mano. Son las que H2 podría resolver distinto que el motor de producción.
 *
 * <p>Cada prueba usa un conector propio, porque la base es la misma para todas las clases *IT.
 */
class ChargingPostgresIT extends ContainersSupport {

    /** Lejos en el futuro, para que ninguna regla de "ya pasó" se meta en el medio. */
    private static final Instant TEN = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.HOURS);

    private static final Instant ELEVEN = TEN.plus(Duration.ofHours(1));

    private static final Instant TWELVE = TEN.plus(Duration.ofHours(2));

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private ChargingSessionRepository sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway aplica todas las migraciones de charging en PostgreSQL, sin fallas")
    void migrationsRunCleanOnPostgres() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from charging.flyway_schema_history where success = false", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from charging.flyway_schema_history where success", Integer.class))
                .isPositive();
    }

    @Test
    @DisplayName("Dos reservas se cruzan si comparten un instante; una que empieza cuando la otra termina, no")
    void overlapIsHalfOpen() {
        long connector = newConnectorId();
        bookings.save(new Booking(connector, 1L, new TimeWindow(TEN, ELEVEN), Instant.now()));

        assertThat(bookings.existsOverlapping(connector, BookingStatus.CONFIRMED, TEN.plusSeconds(1800), TWELVE))
                .as("de 10:30 a 12:00 pisa la de 10 a 11")
                .isTrue();
        assertThat(bookings.existsOverlapping(connector, BookingStatus.CONFIRMED, ELEVEN, TWELVE))
                .as("de 11 a 12 empieza justo cuando la otra termina")
                .isFalse();
        assertThat(bookings.existsOverlapping(connector, BookingStatus.CONFIRMED, TEN.minus(Duration.ofHours(1)), TEN))
                .as("de 9 a 10 termina justo cuando la otra empieza")
                .isFalse();
        assertThat(bookings.existsOverlapping(connector + 1, BookingStatus.CONFIRMED, TEN, ELEVEN))
                .as("otro conector")
                .isFalse();
        assertThat(bookings.findOverlapping(
                        connector, BookingStatus.CONFIRMED, TEN.minusSeconds(60), TEN.plusSeconds(60)))
                .hasSize(1);
    }

    @Test
    @DisplayName("Una reserva cancelada deja libre su horario")
    void cancelledBookingFreesTheWindow() {
        long connector = newConnectorId();
        Booking booking = bookings.save(new Booking(connector, 2L, new TimeWindow(TEN, ELEVEN), Instant.now()));
        booking.cancel(Instant.now());
        bookings.save(booking);

        assertThat(bookings.existsOverlapping(connector, BookingStatus.CONFIRMED, TEN, ELEVEN))
                .isFalse();
        assertThat(bookings.existsOverlapping(connector, BookingStatus.CANCELLED, TEN, ELEVEN))
                .isTrue();
    }

    @Test
    @DisplayName("El cruce por conductor encuentra su otra reserva aunque sea en otro conector")
    void driverOverlapAcrossConnectors() {
        long driver = newConnectorId();
        bookings.save(new Booking(newConnectorId(), driver, new TimeWindow(TEN, ELEVEN), Instant.now()));

        assertThat(bookings.findOverlappingForDriver(driver, BookingStatus.CONFIRMED, TEN.plusSeconds(900), TWELVE))
                .hasSize(1);
        assertThat(bookings.findOverlappingForDriver(driver, BookingStatus.CONFIRMED, ELEVEN, TWELVE))
                .isEmpty();
    }

    @Test
    @DisplayName("La lectura de telemetría guarda los datos, sin motivo de corte (un null tipado en PostgreSQL)")
    void readingIsStoredWithoutEndReason() {
        String sessionId = activeSession();

        int updated = sessions.recordReading(
                sessionId, new BigDecimal("3.250"), new BigDecimal("48.50"), new BigDecimal("41.5"), TEN, null);

        assertThat(updated).isOne();
        ChargingSession stored = sessions.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("3.250");
        assertThat(stored.getCurrentPowerKw()).isEqualByComparingTo("48.50");
        assertThat(stored.getBatteryPercent()).isEqualByComparingTo("41.5");
        assertThat(stored.getLastReadingAt()).isEqualTo(TEN);
        assertThat(stored.getChargerEndReason()).isNull();
    }

    @Test
    @DisplayName("Los kWh nunca bajan, una lectura atrasada no entra y el motivo de corte se conserva")
    void readingRulesHoldInTheDatabase() {
        String sessionId = activeSession();
        sessions.recordReading(
                sessionId, new BigDecimal("6.000"), BigDecimal.TEN, new BigDecimal("55.0"), TEN.plusSeconds(20), null);

        assertThat(sessions.recordReading(
                        sessionId,
                        new BigDecimal("4.000"),
                        BigDecimal.ONE,
                        new BigDecimal("56.0"),
                        TEN.plusSeconds(30),
                        null))
                .as("una posterior con menos kWh entra, pero no baja los kWh")
                .isOne();
        assertThat(sessions.recordReading(
                        sessionId,
                        new BigDecimal("9.000"),
                        BigDecimal.ONE,
                        new BigDecimal("50.0"),
                        TEN.plusSeconds(10),
                        null))
                .as("una atrasada no entra")
                .isZero();
        sessions.recordReading(
                sessionId,
                new BigDecimal("6.500"),
                BigDecimal.ZERO,
                new BigDecimal("57.0"),
                TEN.plusSeconds(40),
                ChargerEndReason.FAULT);
        sessions.recordReading(
                sessionId, new BigDecimal("6.500"), BigDecimal.ZERO, new BigDecimal("57.0"), TEN.plusSeconds(50), null);

        ChargingSession stored = sessions.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("6.500");
        assertThat(stored.getBatteryPercent()).isEqualByComparingTo("57.0");
        assertThat(stored.getChargerEndReason())
                .as("una lectura sin motivo no borra el que ya estaba")
                .isEqualTo(ChargerEndReason.FAULT);
    }

    @Test
    @DisplayName("Una lectura de una sesión cerrada no toca nada")
    void closedSessionIgnoresReadings() {
        String sessionId = activeSession();
        ChargingSession session = sessions.findBySessionId(sessionId).orElseThrow();
        session.stop(TEN.plusSeconds(30), new BigDecimal("7.000"));
        sessions.save(session);

        assertThat(sessions.recordReading(
                        sessionId,
                        new BigDecimal("9.000"),
                        BigDecimal.TEN,
                        new BigDecimal("70.0"),
                        TEN.plusSeconds(40),
                        null))
                .isZero();
        ChargingSession stored = sessions.findBySessionId(sessionId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(stored.getKwhDelivered()).isEqualByComparingTo("7.000");
    }

    private String activeSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.save(ChargingSession.start(
                sessionId, null, newConnectorId(), 42L, 80, new BigDecimal("50.00"), TEN.minusSeconds(60)));
        return sessionId;
    }

    private static long newConnectorId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000);
    }
}
