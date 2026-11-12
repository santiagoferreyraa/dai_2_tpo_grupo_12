package com.ecopedia.charging.session.domain;

import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
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
 * Sesión de carga persistida de Ecopedia (RF11, RF12, RF14, ECO-55, ECO-60).
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

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private ConsumptionPaymentStatus paymentStatus = ConsumptionPaymentStatus.NOT_REQUIRED;

    @Column(name = "real_cost", precision = 10, scale = 2)
    private BigDecimal realCost;

    @Column(name = "additional_charge", precision = 10, scale = 2)
    private BigDecimal additionalCharge;

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
        this.paymentStatus = ConsumptionPaymentStatus.NOT_REQUIRED;
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
        this.paymentStatus = ConsumptionPaymentStatus.PENDING_SETTLEMENT;
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

    /**
     * Actualiza el estado de la liquidación y los costos reales y cobros adicionales (RNF06, ECO-55).
     */
    public void markSettlement(
            ConsumptionPaymentStatus paymentStatus, BigDecimal realCost, BigDecimal additionalCharge) {
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus no puede ser null");
        this.realCost = realCost;
        this.additionalCharge = additionalCharge;
    }

    /**
     * Actualiza la energía suministrada acumulada recibida por telemetría si la sesión sigue activa (RF11, RF16).
     */
    public void updateTelemetry(BigDecimal kwhDelivered) {
        if (isActive() && kwhDelivered != null) {
            if (this.kwhDelivered == null || kwhDelivered.compareTo(this.kwhDelivered) >= 0) {
                this.kwhDelivered = kwhDelivered;
            }
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

    public ConsumptionPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getRealCost() {
        return realCost;
    }

    public BigDecimal getAdditionalCharge() {
        return additionalCharge;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }
}
