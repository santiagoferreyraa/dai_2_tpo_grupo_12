package com.ecopedia.charging.checkout.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.checkout.domain.DepositGateway;
import com.ecopedia.charging.checkout.domain.DepositPaymentUnavailableException;
import com.ecopedia.charging.checkout.domain.DepositRefundRefusedException;
import com.ecopedia.charging.checkout.domain.PendingDepositRefund;
import com.ecopedia.charging.checkout.domain.PendingDepositRefundRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Devuelve las señas de las reservas canceladas a tiempo (ECO-53).
 *
 * <p><b>Por qué la cancelación no devuelve en el acto.</b> Cancelar es de Reservas, y Reservas no habla
 * con Pagos: ese acoplamiento es el que el Facade evita. La cancelación deja la reserva en
 * {@code REFUND_PENDING} en la misma escritura que la cancela —y con eso el conector queda libre sin
 * esperar a la pasarela, que es lo que pide RNF06—, y este barrido es el que habla con Pagos. Es el
 * patrón de una bandeja de salida: el pedido de devolución queda guardado junto con el hecho que lo
 * origina, y se reintenta hasta que sale.
 *
 * <p>La devolución es idempotente en Pagos (y en la pasarela), así que reintentar después de una
 * respuesta perdida no devuelve dos veces.
 */
@Component
public class DepositRefundProcessor {

    private static final Logger log = LoggerFactory.getLogger(DepositRefundProcessor.class);

    private final BookingService bookingService;
    private final DepositGateway depositGateway;
    private final PendingDepositRefundRepository pendingRefunds;
    private final Clock clock;

    public DepositRefundProcessor(
            BookingService bookingService,
            DepositGateway depositGateway,
            PendingDepositRefundRepository pendingRefunds,
            Clock clock) {
        this.bookingService = bookingService;
        this.depositGateway = depositGateway;
        this.pendingRefunds = pendingRefunds;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${ecopedia.booking.deposit-refund-retry-interval}",
            initialDelayString = "${ecopedia.booking.deposit-refund-retry-interval}")
    void refundPending() {
        try {
            bookingService.findPendingDepositRefunds().forEach(this::refund);
            pendingRefunds.findByRefundedAtIsNullOrderByCreatedAtAsc().forEach(this::refundWithoutBooking);
        } catch (RuntimeException failure) {
            // La base caída no tiene que matar el reloj: el próximo barrido lo vuelve a intentar.
            log.warn("No se pudo buscar las señas por devolver: {}", failure.getMessage());
        }
    }

    /**
     * Devuelve una seña cobrada que quedó sin reserva: la compensación del checkout que no salió en el
     * acto (ECO-62). Visible para los tests.
     */
    void refundWithoutBooking(PendingDepositRefund pending) {
        try {
            depositGateway.refund(pending.getChargeId());
            pending.markRefunded(clock.instant());
            pendingRefunds.save(pending);
            log.info(
                    "Seña cobrada sin reserva devuelta: cobro {} de ${} al conductor {}",
                    pending.getChargeId(),
                    pending.getAmount(),
                    pending.getDriverId());
        } catch (DepositPaymentUnavailableException later) {
            log.warn(
                    "No se pudo devolver todavía el cobro {} sin reserva: {}. Se reintenta",
                    pending.getChargeId(),
                    later.getMessage());
        } catch (DepositRefundRefusedException refused) {
            log.error(
                    "Pagos no acepta devolver el cobro {} sin reserva del conductor {}: {}. Revisar a mano",
                    pending.getChargeId(),
                    pending.getDriverId(),
                    refused.getMessage());
        }
    }

    /** Devuelve la seña de una reserva. Visible para los tests, que no esperan al reloj. */
    void refund(Booking booking) {
        try {
            if (booking.isPartialDepositRefund()) {
                depositGateway.refund(booking.getDepositChargeId(), booking.getDepositRefundAmount());
            } else {
                depositGateway.refund(booking.getDepositChargeId());
            }
            bookingService.markDepositRefunded(booking.getId());
            log.info(
                    "Seña de la reserva {} devuelta al conductor {} (cobro {})",
                    booking.getId(),
                    booking.getDriverId(),
                    booking.getDepositChargeId());
        } catch (DepositPaymentUnavailableException later) {
            log.warn(
                    "No se pudo devolver todavía la seña de la reserva {}: {}. Se reintenta",
                    booking.getId(),
                    later.getMessage());
        } catch (DepositRefundRefusedException refused) {
            // No se arregla reintentando, pero se sigue intentando para que el ERROR no se pierda en el log.
            log.error(
                    "Pagos no acepta devolver la seña de la reserva {} (cobro {}): {}. Revisar a mano",
                    booking.getId(),
                    booking.getDepositChargeId(),
                    refused.getMessage());
        }
    }
}
