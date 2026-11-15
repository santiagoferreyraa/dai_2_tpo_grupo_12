package com.ecopedia.charging.booking.domain;

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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Una reserva confirmada: el conector queda bloqueado para el resto durante su ventana (RF08).
 *
 * <p>Es lo que queda de una {@link Hold} cuando el conductor confirma. A diferencia de la
 * retención, esto sí es dominio persistido y no estado conversacional: sobrevive a un reinicio
 * del proceso, y tiene que sobrevivir, porque el conductor ya se comprometió.
 *
 * <p>{@code connectorId} y {@code driverId} son identificadores sueltos y no relaciones JPA: el
 * conector y el usuario viven en {@code ecopedia-core}, otro artefacto. Ver la migración.
 *
 * <p><b>La seña viaja con la reserva desde ECO-53.</b> Se cobra antes de confirmar y la reserva
 * anota lo cobrado; lo que pasa después con esa plata —devolverla o quedársela el operador— lo
 * deciden los cambios de estado de acá, que son los que saben por qué terminó la reserva.
 *
 * <p><b>Patrón State.</b> Cada transición (cancelar, la incomparecencia, habilitar, liberar o
 * liquidar por falla, cerrar la carga) se la pasa al estado en que está la reserva
 * ({@link BookingState}); si ese estado no la admite, la rechaza con
 * {@link BookingStateConflictException}, que la API responde con 409. La reserva no tiene un solo
 * {@code if} sobre su estado: solo los métodos de paquete que los estados usan para cambiarla.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connector_id", nullable = false)
    private Long connectorId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_status", nullable = false, length = 20)
    private DepositStatus depositStatus = DepositStatus.NONE;

    @Column(name = "deposit_charge_id")
    private Long depositChargeId;

    @Column(name = "deposit_card_last_four", length = 4)
    private String depositCardLastFour;

    @Column(name = "deposit_refundable_until")
    private Instant depositRefundableUntil;

    @Column(name = "deposit_refund_amount", precision = 10, scale = 2)
    private BigDecimal depositRefundAmount;

    @Column(name = "checkout_key", length = 100, updatable = false)
    private String checkoutKey;

    @Column(name = "hold_id", length = 36, updatable = false)
    private String holdId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Lo pide JPA. */
    protected Booking() {}

    /**
     * Una reserva sin seña ni checkout: la que confirman los tests de Reservas y, cuando exista, la
     * carga sin reserva (ECO-58), que es una reserva instantánea sin seña.
     *
     * <p>El instante de creación entra por parámetro y no sale de {@code Instant.now()}: el resto
     * del componente decide todo contra el {@code Clock} que le inyecta el contenedor, y una fecha
     * que se toma por su cuenta es la única que un test no puede fijar.
     */
    public Booking(Long connectorId, Long driverId, TimeWindow window, Instant createdAt) {
        this.connectorId = connectorId;
        this.driverId = driverId;
        this.windowStart = window.start();
        this.windowEnd = window.end();
        /*
         * A microsegundos, que es lo que guarda la base: igual que las ventanas (TimeWindow). Sin esto la
         * respuesta del checkout llevaba los nanosegundos del reloj de Windows y la misma reserva leída
         * de la base, no; un checkout repetido (QA-29) no devolvía exactamente la misma respuesta.
         */
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Una reserva confirmada por el checkout, con la seña ya cobrada.
     *
     * @param refundableUntil hasta cuándo cancelar devuelve la seña; se ignora si no hubo seña.
     * @param holdId la retención que se confirmó y {@code checkoutKey} la clave del pedido: juntas
     *     permiten reconocer un checkout repetido (QA-29).
     */
    public Booking(
            Long connectorId,
            Long driverId,
            TimeWindow window,
            Instant createdAt,
            BookingDeposit deposit,
            Instant refundableUntil,
            UUID holdId,
            String checkoutKey) {
        this(connectorId, driverId, window, createdAt);
        if (deposit.isCharged()) {
            this.depositAmount = deposit.amount();
            this.depositStatus = DepositStatus.CHARGED;
            this.depositChargeId = deposit.chargeId();
            this.depositCardLastFour = deposit.cardLastFour();
            this.depositRefundableUntil = refundableUntil;
        }
        this.holdId = holdId == null ? null : holdId.toString();
        this.checkoutKey = checkoutKey;
    }

    public Long getId() {
        return id;
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public TimeWindow getWindow() {
        return new TimeWindow(windowStart, windowEnd);
    }

    public BookingStatus getStatus() {
        return status;
    }

    /**
     * Cancela la reserva y con eso libera la ventana: el cruce solo mira las {@code CONFIRMED},
     * así que a partir de acá el slot se puede volver a reservar.
     *
     * <p>Y decide la seña: <b>se devuelve si se cancela hasta {@link #getDepositRefundableUntil()}
     * inclusive</b> —5 horas antes del inicio, decidido por Manuel el 31/10— y queda para el
     * operador si se cancela después. La devolución no se hace acá: la reserva queda en
     * {@link DepositStatus#REFUND_PENDING} y la pide quien habla con Pagos.
     *
     * <p>Es un método con nombre y no un {@code setStatus} abierto porque no todo cambio de
     * estado es válido desde cualquier otro: solo se cancela una reserva confirmada cuya ventana
     * todavía no terminó. Una que terminó no tiene nada que liberar, y dejar reescribirla sería
     * borrar el historial de una reserva que el conductor usó o a la que no se presentó (RF09, que
     * tiene su propio estado). Lo demás es {@link BookingStateConflictException} (QA-26). Cancelar
     * una ya cancelada no llega acá: el servicio lo resuelve antes, porque es idempotente.
     */
    public void cancel(Instant now) {
        state().cancel(this, now);
    }

    /**
     * La carga no pudo iniciarse por causas ajenas al conductor —conector fuera de servicio,
     * cargador que no responde o rechazo de la distribuidora (RF13, ECO-59)—: la reserva se libera
     * sin penalización y la seña se reintegra entera, sin importar el plazo de preaviso. Solo sobre
     * una reserva confirmada o en curso.
     */
    public void failActivation() {
        state().failActivation(this);
    }

    /**
     * La carga en curso se interrumpió por una falla ajena al conductor (RF13, ECO-59): se liquida
     * el consumo real sin aplicar el piso de la seña y, si el consumo real fue menor que la seña,
     * se reintegra la diferencia. Solo sobre una reserva confirmada o en curso.
     */
    public void settleFaultedCharge(BigDecimal realCost, Instant now) {
        state().settleFaultedCharge(this, realCost, now);
    }

    /**
     * El conductor no se presentó (RF09, ECO-57): la reserva deja de bloquear el conector y al
     * conductor, y la seña queda para el operador. Es para lo que existe la seña. Solo sobre una
     * reserva confirmada: el barrido ya las filtra así, y esto lo asegura para cualquier otro.
     */
    public void markNoShow() {
        state().markNoShow(this);
    }

    /** Pagos confirmó la devolución. */
    public void markDepositRefunded() {
        this.depositStatus = DepositStatus.REFUNDED;
    }

    /**
     * Consume la reserva al iniciar la sesión de carga (RF11, ECO-60).
     * Transición a IN_PROGRESS bajo la transacción declarativa de habilitación. Solo desde
     * {@code CONFIRMED} y dentro de su rango de habilitación ({@link #checkActivationWindow}): lo
     * demás es 409, como cualquier transición inválida.
     *
     * @param earlyActivation cuánto antes del inicio se admite habilitar (15 minutos)
     */
    public void startCharging(Instant now, Duration earlyActivation) {
        state().startCharging(this, now, earlyActivation);
    }

    /**
     * Si la reserva se puede habilitar ahora: desde {@code earlyActivation} antes del inicio hasta el
     * fin de la ventana (Manuel, 12/11). Antes, {@link ActivationTooEarlyException}; con la ventana
     * terminada, el 409 de estado, porque ya no queda turno que usar. No mira el estado: eso lo hace
     * {@link #startCharging}, que la llama solo desde {@code CONFIRMED}.
     */
    public void checkActivationWindow(Instant now, Duration earlyActivation) {
        Instant availableFrom = getWindow().start().minus(earlyActivation);
        if (now.isBefore(availableFrom)) {
            throw new ActivationTooEarlyException(id, availableFrom);
        }
        if (!getWindow().end().isAfter(now)) {
            throw new BookingStateConflictException(id, "con la ventana terminada", "la habilitación de la carga");
        }
    }

    /**
     * Finaliza la reserva al detenerse la sesión de carga (RF12, RF14, ECO-55) y recorta la ventana
     * si concluyó antes del fin programado para liberar el tiempo remanente.
     */
    public void completeCharging(Instant stoppedAt) {
        state().completeCharging(this, stoppedAt);
    }

    // ---- Lo que usan los estados para cambiar la reserva (patrón State). De paquete: desde afuera
    // ---- la reserva solo cambia por las transiciones de arriba.

    private BookingState state() {
        return status.behavior();
    }

    void moveTo(BookingStatus next) {
        this.status = next;
    }

    /** Si la carga o la liquidación terminan dentro de la ventana, el resto se libera. */
    void trimWindowAt(Instant at) {
        // A microsegundos, como guarda la base (ver el constructor).
        Instant instant = at.truncatedTo(ChronoUnit.MICROS);
        if (instant.isAfter(this.windowStart) && instant.isBefore(this.windowEnd)) {
            this.windowEnd = instant;
        }
    }

    /** Cancelar hasta el plazo de preaviso inclusive devuelve la seña; después, queda para el operador. */
    void settleDepositOnCancellation(Instant now) {
        if (depositStatus != DepositStatus.CHARGED) {
            return;
        }
        if (now.isAfter(depositRefundableUntil)) {
            this.depositStatus = DepositStatus.FORFEITED;
        } else {
            refundWholeDeposit();
        }
    }

    void refundWholeDeposit() {
        if (depositStatus == DepositStatus.CHARGED) {
            this.depositStatus = DepositStatus.REFUND_PENDING;
            this.depositRefundAmount = this.depositAmount;
        }
    }

    void forfeitDeposit() {
        if (depositStatus == DepositStatus.CHARGED) {
            this.depositStatus = DepositStatus.FORFEITED;
        }
    }

    /** Sin el piso de la seña (RF13): si el consumo fue menor, se reintegra la diferencia. */
    void settleDepositAgainst(BigDecimal realCost) {
        if (depositStatus != DepositStatus.CHARGED) {
            return;
        }
        if (realCost.compareTo(this.depositAmount) < 0) {
            this.depositStatus = DepositStatus.REFUND_PENDING;
            this.depositRefundAmount = this.depositAmount.subtract(realCost);
        } else {
            this.depositRefundAmount = BigDecimal.ZERO;
        }
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    public boolean isInProgress() {
        return status == BookingStatus.IN_PROGRESS;
    }

    public boolean isCancelled() {
        return status == BookingStatus.CANCELLED;
    }

    public boolean belongsTo(Long driverId) {
        return this.driverId.equals(driverId);
    }

    /** Si esta reserva salió de confirmar esa retención. */
    public boolean confirmedHold(UUID holdId) {
        return holdId != null && holdId.toString().equals(this.holdId);
    }

    /** Si se devuelve o se devolvió la seña. */
    public boolean isDepositRefunded() {
        return depositStatus == DepositStatus.REFUND_PENDING || depositStatus == DepositStatus.REFUNDED;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public DepositStatus getDepositStatus() {
        return depositStatus;
    }

    public Long getDepositChargeId() {
        return depositChargeId;
    }

    public String getDepositCardLastFour() {
        return depositCardLastFour;
    }

    public Instant getDepositRefundableUntil() {
        return depositRefundableUntil;
    }

    public BigDecimal getDepositRefundAmount() {
        if (depositRefundAmount != null) {
            return depositRefundAmount;
        }
        return isDepositRefunded() ? depositAmount : BigDecimal.ZERO;
    }

    public boolean isPartialDepositRefund() {
        return depositRefundAmount != null
                && depositRefundAmount.signum() > 0
                && depositAmount != null
                && depositRefundAmount.compareTo(depositAmount) < 0;
    }

    public String getCheckoutKey() {
        return checkoutKey;
    }
}
