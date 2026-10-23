package com.ecopedia.simulation.paymentgateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Un reintegro sobre un cobro aprobado. En Ecopedia hay uno solo: la seña ante una falla del operador (RF13). */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    private String id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Refund() {}

    public Refund(String idempotencyKey, String paymentId, BigDecimal amount, Instant now) {
        this.id = "ref_" + UUID.randomUUID().toString().replace("-", "");
        this.idempotencyKey = idempotencyKey;
        this.paymentId = paymentId;
        this.amount = amount;
        this.createdAt = now;
    }

    public String getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
