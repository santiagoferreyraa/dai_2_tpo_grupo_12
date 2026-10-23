package com.ecopedia.simulation.paymentgateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un cobro, aprobado o rechazado.
 *
 * <p><b>El rechazado también se guarda</b>, igual que en una pasarela real: el intento existió, y
 * la misma clave de idempotencia tiene que devolver el mismo rechazo en vez de volver a intentar.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private String id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "card_token_id", nullable = false)
    private String cardTokenId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "refunded_amount", nullable = false)
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "status_detail", nullable = false)
    private String statusDetail;

    private String description;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(
            String idempotencyKey,
            String cardTokenId,
            BigDecimal amount,
            PaymentStatus status,
            String statusDetail,
            String description,
            String externalReference,
            Instant now) {
        this.id = "pay_" + UUID.randomUUID().toString().replace("-", "");
        this.idempotencyKey = idempotencyKey;
        this.cardTokenId = cardTokenId;
        this.amount = amount;
        this.refundedAmount = BigDecimal.ZERO;
        this.status = status;
        this.statusDetail = statusDetail;
        this.description = description;
        this.externalReference = externalReference;
        this.createdAt = now;
    }

    /** Lo que todavía se puede devolver: lo cobrado menos lo ya reintegrado. */
    public BigDecimal refundable() {
        return status == PaymentStatus.REJECTED ? BigDecimal.ZERO : amount.subtract(refundedAmount);
    }

    /** Suma un reintegro. Si con él se devolvió todo, el cobro pasa a {@code refunded}. */
    public void registerRefund(BigDecimal refunded) {
        this.refundedAmount = refundedAmount.add(refunded);
        if (refundedAmount.compareTo(amount) >= 0) {
            this.status = PaymentStatus.REFUNDED;
        }
    }

    public String getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCardTokenId() {
        return cardTokenId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public String getDescription() {
        return description;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
