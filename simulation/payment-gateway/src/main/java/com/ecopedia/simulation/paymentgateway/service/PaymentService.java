package com.ecopedia.simulation.paymentgateway.service;

import com.ecopedia.simulation.paymentgateway.data.PaymentRepository;
import com.ecopedia.simulation.paymentgateway.data.RefundRepository;
import com.ecopedia.simulation.paymentgateway.domain.CardBehavior;
import com.ecopedia.simulation.paymentgateway.domain.CardToken;
import com.ecopedia.simulation.paymentgateway.domain.GatewayException;
import com.ecopedia.simulation.paymentgateway.domain.Payment;
import com.ecopedia.simulation.paymentgateway.domain.PaymentStatus;
import com.ecopedia.simulation.paymentgateway.domain.Refund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobros y reintegros.
 *
 * <p><b>Los dos piden una clave de idempotencia</b>, y es lo que hace seguro reintentar. Si Pagos
 * cobra, la respuesta se pierde en el camino y vuelve a pedir, con la misma clave recibe el cobro
 * que ya se hizo en vez de un segundo cobro. Sin esto, la compensación con reintentos de RNF06
 * podría cobrarle dos veces al conductor.
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final CardTokenService cardTokens;
    private final Clock clock;
    private final Duration slowCardDelay;

    public PaymentService(
            PaymentRepository payments,
            RefundRepository refunds,
            CardTokenService cardTokens,
            Clock clock,
            @Value("${simulation.payment-gateway.slow-card-delay:8s}") Duration slowCardDelay) {
        this.payments = payments;
        this.refunds = refunds;
        this.cardTokens = cardTokens;
        this.clock = clock;
        this.slowCardDelay = slowCardDelay;
    }

    /**
     * Cobra. Un rechazo no es un error: vuelve como un cobro con estado {@code rejected}, igual que
     * en Mercado Pago, y queda guardado.
     */
    public Payment charge(ChargeRequest request, String idempotencyKey) {
        Optional<Payment> previous = payments.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            log.info(
                    "Cobro repetido con la clave {}: se devuelve {}",
                    idempotencyKey,
                    previous.get().getId());
            return previous.get();
        }
        requirePositive(request.amount());

        CardToken token = cardTokens.find(request.cardTokenId());
        if (token.getBehavior() == CardBehavior.SLOW) {
            Pause.of(slowCardDelay);
        }

        PaymentStatus status = PaymentStatus.APPROVED;
        String detail = "accredited";
        if (token.isExpiredAt(YearMonth.now(clock))) {
            status = PaymentStatus.REJECTED;
            detail = "cc_rejected_card_expired";
        } else if (token.getBehavior() == CardBehavior.DECLINE) {
            status = PaymentStatus.REJECTED;
            detail = "cc_rejected_other_reason";
        } else if (token.getBehavior() == CardBehavior.INSUFFICIENT_FUNDS) {
            status = PaymentStatus.REJECTED;
            detail = "cc_rejected_insufficient_amount";
        }

        Payment payment = payments.save(new Payment(
                idempotencyKey,
                token.getId(),
                request.amount(),
                status,
                detail,
                request.description(),
                request.externalReference(),
                clock.instant()));
        log.info("Cobro {} por ${}: {} ({})", payment.getId(), payment.getAmount(), status.apiValue(), detail);
        return payment;
    }

    /** Devuelve todo lo que queda por devolver, o el importe pedido si se pide menos. */
    public Refund refund(String paymentId, BigDecimal amount, String idempotencyKey) {
        Optional<Refund> previous = refunds.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            log.info(
                    "Reintegro repetido con la clave {}: se devuelve {}",
                    idempotencyKey,
                    previous.get().getId());
            return previous.get();
        }

        Payment payment = find(paymentId);
        BigDecimal refundable = payment.refundable();
        if (refundable.signum() <= 0) {
            throw GatewayException.badRequest(
                    "payment_not_refundable", "Payment was rejected or is already fully refunded");
        }

        BigDecimal toRefund = amount == null ? refundable : amount;
        requirePositive(toRefund);
        if (toRefund.compareTo(refundable) > 0) {
            throw GatewayException.badRequest(
                    "amount_exceeds_refundable", "Refund amount exceeds the refundable amount");
        }

        payment.registerRefund(toRefund);
        payments.save(payment);
        Refund refund = refunds.save(new Refund(idempotencyKey, payment.getId(), toRefund, clock.instant()));
        log.info("Reintegro {} de ${} sobre el cobro {}", refund.getId(), toRefund, payment.getId());
        return refund;
    }

    @Transactional(readOnly = true)
    public Payment find(String paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> GatewayException.notFound("payment_not_found", "Payment not found"));
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw GatewayException.badRequest("invalid_amount", "Amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw GatewayException.badRequest("invalid_amount", "Amount cannot have more than two decimals");
        }
    }
}
