package com.ecopedia.charging.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Sesión de carga persistida de Ecopedia (RF11, RF12, RF14, ECO-60).
 *
 * <p>Nace en estado {@link SessionStatus#ACTIVE} bajo la transacción declarativa local junto con el
 * cambio de estado del conector a ocupado y la reserva a en curso.
 */
@Entity
@Table(name = "charging_sessions")
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "connector_id", nullable = false)
    private Long connectorId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "target_battery_percent", nullable = false)
    private int targetBatteryPercent;

    @Column(name = "power_limit_kw", nullable = false, precision = 6, scale = 2)
    private BigDecimal powerLimitKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "kwh_delivered", nullable = false, precision = 8, scale = 3)
    private BigDecimal kwhDelivered = BigDecimal.ZERO;

    /** La potencia de la última lectura del cargador (ECO-40). {@code null} hasta que llega una. */
    @Column(name = "current_power_kw", precision = 6, scale = 2)
    private BigDecimal currentPowerKw;

    /** El porcentaje de batería de la última lectura: contra él se compara el objetivo. */
    @Column(name = "battery_percent", precision = 5, scale = 2)
    private BigDecimal batteryPercent;

    @Column(name = "last_reading_at")
    private Instant lastReadingAt;

    /**
     * Por qué el cargador dio por terminada la sesión, si ya avisó que cortó. {@code null} mientras
     * sigue cargando. Que tenga valor con la sesión todavía {@code ACTIVE} quiere decir que el
     * cargador terminó por su cuenta y falta cerrarla de este lado.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "charger_end_reason", length = 20)
    private ChargerEndReason chargerEndReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ChargingSession() {}

    public ChargingSession(
            String sessionId,
            Long bookingId,
            Long connectorId,
            Long driverId,
            int targetBatteryPercent,
            BigDecimal powerLimitKw,
            Instant startedAt) {
        this.sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        this.bookingId = bookingId;
        this.connectorId = Objects.requireNonNull(connectorId, "connectorId no puede ser null");
        this.driverId = Objects.requireNonNull(driverId, "driverId no puede ser null");
        this.targetBatteryPercent = targetBatteryPercent;
        this.powerLimitKw = Objects.requireNonNull(powerLimitKw, "powerLimitKw no puede ser null");
        this.startedAt =
                Objects.requireNonNull(startedAt, "startedAt no puede ser null").truncatedTo(ChronoUnit.MICROS);
        this.status = SessionStatus.ACTIVE;
        this.kwhDelivered = BigDecimal.ZERO;
    }

    public static ChargingSession start(
            String sessionId,
            Long bookingId,
            Long connectorId,
            Long driverId,
            int targetBatteryPercent,
            BigDecimal powerLimitKw,
            Instant startedAt) {
        return new ChargingSession(
                sessionId, bookingId, connectorId, driverId, targetBatteryPercent, powerLimitKw, startedAt);
    }

    public void stop(Instant stoppedAt, BigDecimal kwhDelivered) {
        this.status = SessionStatus.COMPLETED;
        this.stoppedAt = stoppedAt != null
                ? stoppedAt.truncatedTo(ChronoUnit.MICROS)
                : Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (kwhDelivered != null) {
            this.kwhDelivered = kwhDelivered;
        }
    }

    public void markFaulted(Instant stoppedAt, BigDecimal kwhDelivered) {
        this.status = SessionStatus.FAULTED;
        this.stoppedAt = stoppedAt != null
                ? stoppedAt.truncatedTo(ChronoUnit.MICROS)
                : Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (kwhDelivered != null) {
            this.kwhDelivered = kwhDelivered;
        }
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public int getTargetBatteryPercent() {
        return targetBatteryPercent;
    }

    public BigDecimal getPowerLimitKw() {
        return powerLimitKw;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public BigDecimal getKwhDelivered() {
        return kwhDelivered;
    }

    public BigDecimal getCurrentPowerKw() {
        return currentPowerKw;
    }

    public BigDecimal getBatteryPercent() {
        return batteryPercent;
    }

    public Instant getLastReadingAt() {
        return lastReadingAt;
    }

    public ChargerEndReason getChargerEndReason() {
        return chargerEndReason;
    }

    /** El cargador avisó que terminó (batería llena o falla) y la sesión sigue abierta de este lado. */
    public boolean wasEndedByCharger() {
        return chargerEndReason == ChargerEndReason.BATTERY_FULL || chargerEndReason == ChargerEndReason.FAULT;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }
}
