package com.ecopedia.simulator.pasarela.api;

import com.ecopedia.simulator.pasarela.domain.Ledger;
import com.ecopedia.simulator.pasarela.domain.Ledger.Payment;
import com.ecopedia.simulator.pasarela.domain.Ledger.Refund;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cobros y reintegros.
 *
 * <p><b>Los importes van en centavos y como entero</b> ({@code amount: 150000} son $1.500,00).
 * Es como trabajan las pasarelas para no arrastrar errores de redondeo, y es otra diferencia con
 * Ecopedia, que maneja {@code BigDecimal} en pesos: convertir de uno a otro es trabajo del Adapter.
 *
 * <p>Todavía nadie en Ecopedia llama a estos endpoints: los van a usar la seña (ECO-53), la
 * validación de tarjeta (ECO-54) y el reintegro (ECO-59). Están desde ahora porque son parte de la
 * pasarela que se simula, no de una funcionalidad de Ecopedia.
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final Ledger ledger;

    public PaymentController(Ledger ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey) {
        Payment payment = ledger.charge(
                request.cardToken(),
                request.amount(),
                request.currencyId(),
                request.description(),
                request.externalReference(),
                idempotencyKey);
        return PaymentResponse.from(payment);
    }

    @GetMapping("/{id}")
    public PaymentResponse find(@PathVariable String id) {
        return PaymentResponse.from(ledger.findPayment(id));
    }

    /** Sin cuerpo o sin {@code amount}, reintegra todo lo que queda del pago. */
    @PostMapping("/{id}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse refund(@PathVariable String id, @Valid @RequestBody(required = false) RefundRequest request) {
        Refund refund = ledger.refund(id, request == null ? null : request.amount());
        return new RefundResponse(refund.id(), refund.paymentId(), refund.amount(), "approved", refund.createdAt());
    }

    public record PaymentRequest(
            @NotNull @Positive Long amount,
            @NotBlank String currencyId,
            @NotBlank String cardToken,
            String description,
            String externalReference) {}

    public record PaymentResponse(
            String id,
            String status,
            String statusDetail,
            long amount,
            long amountRefunded,
            String currencyId,
            String cardToken,
            String description,
            String externalReference,
            Instant dateCreated) {

        static PaymentResponse from(Payment payment) {
            String status = payment.amountRefunded() == 0
                    ? "approved"
                    : payment.amountRefunded() == payment.amount() ? "refunded" : "partially_refunded";
            return new PaymentResponse(
                    payment.id(),
                    status,
                    "accredited",
                    payment.amount(),
                    payment.amountRefunded(),
                    payment.currencyId(),
                    payment.cardToken(),
                    payment.description(),
                    payment.externalReference(),
                    payment.createdAt());
        }
    }

    public record RefundRequest(@Positive Long amount) {}

    public record RefundResponse(String id, String paymentId, long amount, String status, Instant dateCreated) {}
}
