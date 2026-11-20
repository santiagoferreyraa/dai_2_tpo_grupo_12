package com.ecopedia.charging.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Una seña cobrada que quedó sin reserva y no se pudo devolver en el acto (ECO-62).
 *
 * <p>Pasa en el checkout: se cobró la seña, la confirmación falló —la retención venció durante el
 * cobro, o alguien ganó el turno— y la devolución en el acto tampoco salió. Antes eso quedaba solo en
 * un ERROR del log, "SEÑA COBRADA SIN RESERVA", para devolverla a mano. Ahora queda acá, y la devuelve
 * el mismo barrido que las señas de las cancelaciones ({@code DepositRefundProcessor}).
 *
 * <p>Es una fila aparte y no una reserva en {@code REFUND_PENDING} porque no hay reserva: la
 * confirmación es justamente lo que falló.
 */
@Entity
@Table(name = "pending_deposit_refunds")
public class PendingDepositRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_id", nullable = false, unique = true)
    private Long chargeId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "hold_id", nullable = false, length = 40)
    private String holdId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Constructor requerido por JPA. */
    protected PendingDepositRefund() {}

    public PendingDepositRefund(Long chargeId, Long driverId, BigDecimal amount, String holdId, Instant createdAt) {
        this.chargeId = Objects.requireNonNull(chargeId, "chargeId no puede ser null");
        this.driverId = Objects.requireNonNull(driverId, "driverId no puede ser null");
        this.amount = Objects.requireNonNull(amount, "amount no puede ser null");
        this.holdId = Objects.requireNonNull(holdId, "holdId no puede ser null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser null");
    }

    /** Pagos confirmó la devolución. */
    public void markRefunded(Instant at) {
        this.refundedAt = at;
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getChargeId() {
        return chargeId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getHoldId() {
        return holdId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
