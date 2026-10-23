package com.ecopedia.simulation.paymentgateway.web;

import com.ecopedia.simulation.paymentgateway.service.ChargeRequest;
import com.ecopedia.simulation.paymentgateway.service.PaymentService;
import com.ecopedia.simulation.paymentgateway.web.dto.CreatePaymentRequest;
import com.ecopedia.simulation.paymentgateway.web.dto.CreateRefundRequest;
import com.ecopedia.simulation.paymentgateway.web.dto.PaymentResponse;
import com.ecopedia.simulation.paymentgateway.web.dto.RefundResponse;
import jakarta.validation.Valid;
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
 * Cobros y reintegros. Los dos exigen {@code X-Idempotency-Key}: sin ella, un reintento podría
 * cobrar dos veces, así que la pasarela no acepta el pedido.
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse charge(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        return PaymentResponse.from(payments.charge(
                new ChargeRequest(
                        request.token(),
                        request.transactionAmount(),
                        request.description(),
                        request.externalReference()),
                idempotencyKey));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse find(@PathVariable String paymentId) {
        return PaymentResponse.from(payments.find(paymentId));
    }

    @PostMapping("/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse refund(
            @PathVariable String paymentId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody(required = false) CreateRefundRequest request) {
        return RefundResponse.from(
                payments.refund(paymentId, request == null ? null : request.amount(), idempotencyKey));
    }
}
