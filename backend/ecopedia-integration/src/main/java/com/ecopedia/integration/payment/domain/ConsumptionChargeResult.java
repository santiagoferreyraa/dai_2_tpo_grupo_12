package com.ecopedia.integration.payment.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado del cobro del consumo final de una sesión (RF14, RNF06, ECO-55).
 *
 * <p>Si el cobro falla o no puede realizarse, {@code status} será {@link Status#DEBT_RECORDED} con el
 * identificador de la deuda persistida en {@code pendingPaymentId}.
 */
public record ConsumptionChargeResult(
        Status status,
        BigDecimal amount,
        String sessionId,
        String gatewayPaymentId,
        Long pendingPaymentId,
        String detail) {

    public enum Status {
        /** El cobro del consumo adicional fue aprobado por la pasarela. */
        CHARGED,
        /** El cobro falló o fue rechazado y quedó registrado como deuda pendiente (PendingPayment). */
        DEBT_RECORDED
    }

    public ConsumptionChargeResult {
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(amount, "amount no puede ser null");
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
    }

    public static ConsumptionChargeResult charged(
            BigDecimal amount, String sessionId, String gatewayPaymentId, String detail) {
        return new ConsumptionChargeResult(Status.CHARGED, amount, sessionId, gatewayPaymentId, null, detail);
    }

    public static ConsumptionChargeResult debtRecorded(
            BigDecimal amount, String sessionId, Long pendingPaymentId, String detail) {
        return new ConsumptionChargeResult(Status.DEBT_RECORDED, amount, sessionId, null, pendingPaymentId, detail);
    }
}
