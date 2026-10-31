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
     * estado es válido desde cualquier otro.
     */
    public void cancel(Instant now) {
        this.status = BookingStatus.CANCELLED;
        if (depositStatus == DepositStatus.CHARGED) {
            this.depositStatus =
                    now.isAfter(depositRefundableUntil) ? DepositStatus.FORFEITED : DepositStatus.REFUND_PENDING;
        }
    }

    /**
     * El conductor no se presentó (RF09, ECO-57): la reserva deja de bloquear el conector y al
     * conductor, y la seña queda para el operador. Es para lo que existe la seña.
     */
    public void markNoShow() {
        this.status = BookingStatus.NO_SHOW;
        if (depositStatus == DepositStatus.CHARGED) {
            this.depositStatus = DepositStatus.FORFEITED;
        }
    }

    /** Pagos confirmó la devolución. */
    public void markDepositRefunded() {
        this.depositStatus = DepositStatus.REFUNDED;
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
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

    public String getCheckoutKey() {
        return checkoutKey;
    }
}
