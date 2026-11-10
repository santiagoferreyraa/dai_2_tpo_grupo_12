package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.ConsumptionChargeResult;
import java.math.BigDecimal;

/**
 * Respuesta interna del cobro de consumo de sesión o registro de deuda (ECO-55).
 */
public record ConsumptionChargeResponse(
        String status,
        BigDecimal amount,
        String sessionId,
        String gatewayPaymentId,
        Long pendingPaymentId,
        String detail) {

    public static ConsumptionChargeResponse from(ConsumptionChargeResult result) {
        return new ConsumptionChargeResponse(
                result.status().name(),
                result.amount(),
                result.sessionId(),
                result.gatewayPaymentId(),
                result.pendingPaymentId(),
                result.detail());
    }
}
