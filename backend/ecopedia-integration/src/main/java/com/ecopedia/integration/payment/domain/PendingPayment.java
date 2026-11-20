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
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Representa una deuda o cobro pendiente de regularizar por un conductor (RF02, RF14, RNF06, ECO-54).
 *
 * <p>Persiste los cobros fallidos o saldos impagos de sesiones de carga anteriores.
 * Mientras exista un registro abierto ({@link PendingPaymentStatus#PENDING} o
 * {@link PendingPaymentStatus#DEFAULTED}), el conductor tiene bloqueada la habilitación física de
 * cargadores y la creación de reservas.
 *
 * <p><b>Los reintentos (ECO-62).</b> Después del cobro original, el barrido la vuelve a cobrar tres
 * veces, cada 20 minutos; si fallan las tres, queda vencida ({@link #recordFailedRetry}). Una
 * pasarela que no contesta no cuenta como intento: no es culpa del conductor, y marcarle la cuenta
 * por una caída nuestra sería castigarlo por algo que no hizo.
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

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /**
     * Quién está cobrando esta deuda, hasta cuándo. Lo escriben y lo borran solo las sentencias del
     * repositorio ({@code claim} y {@code releaseClaim}), nunca el guardado de la entidad: así guardar
     * una deuda leída antes no pisa el reclamo de otro.
     */
    @Column(name = "claimed_until", insertable = false, updatable = false)
    private Instant claimedUntil;

    @Column(name = "bookings_cancelled", nullable = false)
    private boolean bookingsCancelled;

    @Column(name = "gateway_payment_id", length = 80)
    private String gatewayPaymentId;

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

    /** Si el conductor todavía la debe: pendiente o vencida. */
    public boolean isOpen() {
        return this.status != null && this.status.isOpen();
    }

    /** Si fallaron los tres reintentos (ECO-62). */
    public boolean isDefaulted() {
        return this.status == PendingPaymentStatus.DEFAULTED;
    }

    /**
     * Marca la deuda como saldada.
     *
     * @param settledAt Momento en que se confirmó el cobro.
     */
    public void markSettled(Instant settledAt) {
        this.status = PendingPaymentStatus.SETTLED;
        this.settledAt = settledAt != null ? settledAt : Instant.now();
        this.nextAttemptAt = null;
    }

    /**
     * La saldó un cobro aprobado: un reintento del barrido o el conductor desde el perfil (ECO-62).
     *
     * @param cardId la tarjeta con la que se cobró
     * @param paymentId el cobro de la pasarela
     */
    public void markSettled(Instant settledAt, Long cardId, String paymentId) {
        markSettled(settledAt);
        this.paymentMethodId = cardId;
        this.gatewayPaymentId = paymentId;
        this.lastAttemptAt = this.settledAt;
    }

    /** El primer reintento, a un intervalo del cobro original que falló (ECO-62). */
    public void scheduleFirstRetry(Duration retryInterval) {
        this.nextAttemptAt = this.createdAt.plus(retryInterval);
    }

    /**
     * Un reintento del barrido que no se cobró: la tarjeta rechazó o el conductor no tiene ninguna
     * que sirva (ECO-62). Al llegar a {@code maxRetries}, la deuda queda vencida y no se reintenta más.
     *
     * @return {@code true} si con este reintento la deuda quedó vencida
     */
    public boolean recordFailedRetry(Instant at, Long cardId, String reason, int maxRetries, Duration retryInterval) {
        if (!isPending()) {
            throw new IllegalStateException("La deuda " + id + " no está pendiente: no se reintenta");
        }
        this.retryCount++;
        this.lastAttemptAt = at;
        if (cardId != null) {
            this.paymentMethodId = cardId;
        }
        if (reason != null && !reason.isBlank()) {
            String trimmed = reason.trim();
            this.reason = trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
        }
        if (this.retryCount >= maxRetries) {
            this.status = PendingPaymentStatus.DEFAULTED;
            this.nextAttemptAt = null;
            return true;
        }
        this.nextAttemptAt = at.plus(retryInterval);
        return false;
    }

    /**
     * La pasarela no contestó: el reintento no cuenta y se repite al intervalo siguiente, con la
     * misma clave (si la pasarela había cobrado, contesta el mismo cobro y no cobra dos veces).
     */
    public void postponeRetry(Instant at, Duration retryInterval) {
        this.lastAttemptAt = at;
        this.nextAttemptAt = at.plus(retryInterval);
    }

    /**
     * La clave del próximo reintento: una por reintento, porque la pasarela es idempotente por clave
     * y con la misma devolvería el mismo rechazo. Como la pasarela caída no suma un reintento, el
     * próximo intento después de una caída usa la misma clave, y eso es lo que se busca.
     */
    public String nextRetryKey() {
        return "debt-" + id + "-retry-" + (retryCount + 1);
    }

    /** Reservas ya anuló las reservas confirmadas del conductor por esta deuda vencida. */
    public void markBookingsCancelled() {
        this.bookingsCancelled = true;
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

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getClaimedUntil() {
        return claimedUntil;
    }

    public boolean isBookingsCancelled() {
        return bookingsCancelled;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
