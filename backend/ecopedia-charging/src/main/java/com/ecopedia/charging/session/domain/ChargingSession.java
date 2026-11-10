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

    /** El último porcentaje de batería que se supo: al apagar el cargador, o por la telemetría (ECO-45). */
    @Column(name = "battery_percent", precision = 5, scale = 2)
    private BigDecimal batteryPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private SessionEndReason endReason;

    /** Lo que costó la energía entregada, según Tarificación. {@code null} si no se pudo calcular. */
    @Column(name = "real_cost", precision = 10, scale = 2)
    private BigDecimal realCost;

    /** La seña que se descuenta del consumo (0 en una carga sin reserva). */
    @Column(name = "deposit_applied", precision = 10, scale = 2)
    private BigDecimal depositApplied;

    /** Lo que termina pagando el conductor por la sesión: el consumo, con la seña como piso (RF14). */
    @Column(name = "final_amount", precision = 10, scale = 2)
    private BigDecimal finalAmount;

    /** Lo que se cobra al cerrar: la diferencia entre el consumo y la seña ya cobrada. */
    @Column(name = "amount_to_charge", precision = 10, scale = 2)
    private BigDecimal amountToCharge;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private SessionPaymentStatus paymentStatus = SessionPaymentStatus.NOT_SETTLED;

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

    /**
     * Cierra la sesión con lo que dijo el cargador al apagarse (RF11, ECO-44). Los kWh solo crecen: si
     * el cargador no contestó y la telemetría ya había anotado más, queda lo de la telemetría.
     */
    public void complete(Instant stoppedAt, ChargerReading reading, SessionEndReason reason) {
        if (status != SessionStatus.ACTIVE) {
            throw new IllegalStateException("La sesión " + sessionId + " ya no está activa: " + status);
        }
        this.status = SessionStatus.COMPLETED;
        this.stoppedAt =
                Objects.requireNonNull(stoppedAt, "stoppedAt no puede ser null").truncatedTo(ChronoUnit.MICROS);
        this.endReason = Objects.requireNonNull(reason, "reason no puede ser null");
        if (reading != null) {
            if (reading.energyKwh() != null && reading.energyKwh().compareTo(this.kwhDelivered) > 0) {
                this.kwhDelivered = reading.energyKwh();
            }
            if (reading.batteryPercent() != null) {
                this.batteryPercent = reading.batteryPercent();
            }
        }
    }

    /** Anota la liquidación de la sesión ya cerrada (RF14). */
    public void recordSettlement(ChargeSettlement settlement) {
        this.realCost = settlement.realCost();
        this.depositApplied = settlement.depositAmount();
        this.finalAmount = settlement.finalBilledAmount();
        this.amountToCharge = settlement.amountToCharge();
        this.paymentStatus = settlement.requiresAdditionalCharge()
                ? SessionPaymentStatus.PENDING
                : SessionPaymentStatus.NOT_REQUIRED;
    }

    /** Anota cómo terminó el cobro del consumo, después de la transacción local (RNF06). */
    public void recordPayment(SessionPaymentStatus paymentStatus) {
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus no puede ser null");
    }

    public boolean belongsTo(Long driverId) {
        return this.driverId.equals(driverId);
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

    public Long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public BigDecimal getBatteryPercent() {
        return batteryPercent;
    }

    public SessionEndReason getEndReason() {
        return endReason;
    }

    public BigDecimal getRealCost() {
        return realCost;
    }

    public BigDecimal getDepositApplied() {
        return depositApplied;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public BigDecimal getAmountToCharge() {
        return amountToCharge;
    }

    public SessionPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }
}
