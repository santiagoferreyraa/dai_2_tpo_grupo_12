package com.ecopedia.integration.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Un cobro de seña que la pasarela contestó (RF14, ECO-53).
 *
 * <p><b>Es persistencia de dominio, no estado conversacional</b>: Pagos sigue siendo stateless.
 * La fila existe para dos cosas que sin ella no se pueden hacer: contestar igual a un pedido
 * repetido sin volver a cobrar, y saber qué cobro de la pasarela devolver cuando se reintegra.
 *
 * <p>Un rechazo también se guarda. Si no, el reintento del mismo pedido volvería a la pasarela y
 * el resultado podría cambiar —la tarjeta con fondos un minuto después— con la misma clave: la
 * clave dejaría de nombrar un único cobro.
 */
@Entity
@Table(name = "deposit_charges")
public class DepositCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 160, unique = true)
    private String idempotencyKey;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "payment_method_id", nullable = false)
    private Long paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_brand", nullable = false, length = 20)
    private CardBrand cardBrand;

    @Column(name = "card_last_four", nullable = false, length = 4)
    private String cardLastFour;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference", nullable = false, length = 120)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepositChargeStatus status;

    @Column(name = "gateway_payment_id", length = 80)
    private String gatewayPaymentId;

    @Column(name = "status_detail", length = 80)
    private String statusDetail;

    @Column(name = "gateway_refund_id", length = 80)
    private String gatewayRefundId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    /** Lo pide JPA. */
    protected DepositCharge() {}

    public DepositCharge(DepositChargeRequest request, PaymentMethod card, GatewayCharge answer, Instant createdAt) {
        this.idempotencyKey = request.idempotencyKey();
        this.driverId = request.driverId();
        this.paymentMethodId = card.getId();
        this.cardBrand = card.getBrand();
        this.cardLastFour = card.getLastFour();
        /*
         * Con la escala de la columna: sin esto la primera respuesta decía 8000 y la repetida, leída de la
         * base, 8000.00. El mismo pedido tiene que recibir exactamente la misma respuesta.
         */
        this.amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        this.reference = request.reference();
        this.status = answer.approved() ? DepositChargeStatus.APPROVED : DepositChargeStatus.DECLINED;
        this.gatewayPaymentId = answer.paymentId();
        this.statusDetail = answer.statusDetail();
        this.createdAt = createdAt;
    }

    /**
     * Si este cobro es la respuesta a ese pedido.
     *
     * <p>La clave sola no alcanza: el mismo texto mandado con otro conductor o con otro importe no
     * es un reintento, es un pedido distinto que reusa una clave, y contestarle con este cobro sería
     * decirle "listo" a algo que nunca se cobró.
     */
    public boolean answers(DepositChargeRequest request) {
        return driverId.equals(request.driverId()) && amount.compareTo(request.amount()) == 0;
    }

    public void markRefunded(GatewayRefund refund, Instant at) {
        this.status = DepositChargeStatus.REFUNDED;
        this.gatewayRefundId = refund.refundId();
        this.refundedAt = at;
    }

    public boolean isApproved() {
        return status == DepositChargeStatus.APPROVED;
    }

    public boolean isRefunded() {
        return status == DepositChargeStatus.REFUNDED;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getDriverId() {
        return driverId;
    }

    public Long getPaymentMethodId() {
        return paymentMethodId;
    }

    public CardBrand getCardBrand() {
        return cardBrand;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DepositChargeStatus getStatus() {
        return status;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
