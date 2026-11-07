package com.ecopedia.integration.payment.domain;

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
import java.util.Objects;

/**
 * Representa una deuda o cobro pendiente de regularizar por un conductor (RF02, RF14, RNF06, ECO-54).
 *
 * <p>Persiste los cobros fallidos o saldos impagos de sesiones de carga anteriores.
 * Mientras exista un registro en estado {@link PendingPaymentStatus#PENDING}, el conductor tiene
 * bloqueada la habilitación física de cargadores y la creación de reservas.
 */
@Entity
@Table(name = "pending_payments")
public class PendingPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 120)
    private String reason;

    @Column(name = "reference", nullable = false, length = 120)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingPaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Constructor requerido por JPA. */
    protected PendingPayment() {}

    public PendingPayment(
            Long driverId,
            Long paymentMethodId,
            BigDecimal amount,
            String reason,
            String reference,
            Instant createdAt) {
        this.driverId = Objects.requireNonNull(driverId, "driverId no puede ser null");
        this.paymentMethodId = paymentMethodId;
        this.amount = Objects.requireNonNull(amount, "amount no puede ser null");
        this.reason = Objects.requireNonNull(reason, "reason no puede ser null");
        this.reference = Objects.requireNonNull(reference, "reference no puede ser null");
        this.status = PendingPaymentStatus.PENDING;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Verifica si la deuda continúa pendiente de cobro.
     */
    public boolean isPending() {
        return this.status == PendingPaymentStatus.PENDING;
    }

    /**
     * Marca la deuda como saldada.
     *
     * @param settledAt Momento en que se confirmó el cobro.
     */
    public void markSettled(Instant settledAt) {
        this.status = PendingPaymentStatus.SETTLED;
        this.settledAt = settledAt != null ? settledAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Long getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(Long paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public PendingPaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PendingPaymentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
