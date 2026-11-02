package com.ecopedia.simulation.paymentgateway.web.dto;

import com.ecopedia.simulation.paymentgateway.domain.Refund;
import java.math.BigDecimal;
import java.time.Instant;

/** Un reintegro hecho. Si la pasarela no lo puede hacer, contesta un error, no un reintegro rechazado. */
public record RefundResponse(String id, String paymentId, BigDecimal amount, String status, Instant dateCreated) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(), refund.getPaymentId(), refund.getAmount(), "approved", refund.getCreatedAt());
    }
}
