package com.ecopedia.simulation.paymentgateway.web.dto;

import com.ecopedia.simulation.paymentgateway.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;

/** Un cobro, con el estado y el motivo como los nombra Mercado Pago. */
public record PaymentResponse(
        String id,
        String status,
        String statusDetail,
        BigDecimal transactionAmount,
        BigDecimal transactionAmountRefunded,
        String description,
        String externalReference,
        Instant dateCreated) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus().apiValue(),
                payment.getStatusDetail(),
                payment.getAmount(),
                payment.getRefundedAmount(),
                payment.getDescription(),
                payment.getExternalReference(),
                payment.getCreatedAt());
    }
}
