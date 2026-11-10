package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una sesión de carga como la ve su conductor (RF11, ECO-44): mientras carga y después, con la
 * liquidación.
 *
 * <p>Mientras la sesión está activa, {@code kwhDelivered} y {@code batteryPercent} son lo último que
 * se supo; los actualiza la telemetría (ECO-45). Los importes son {@code null} hasta que cierra.
 */
public record ChargingSessionView(
        String sessionId,
        Long bookingId,
        Long connectorId,
        SessionStatus status,
        int targetBatteryPercent,
        BigDecimal powerLimitKw,
        Instant startedAt,
        Instant stoppedAt,
        BigDecimal kwhDelivered,
        BigDecimal batteryPercent,
        SessionEndReason endReason,
        BigDecimal realCost,
        BigDecimal depositApplied,
        BigDecimal finalAmount,
        BigDecimal amountToCharge,
        SessionPaymentStatus paymentStatus) {

    public static ChargingSessionView of(ChargingSession session) {
        return new ChargingSessionView(
                session.getSessionId(),
                session.getBookingId(),
                session.getConnectorId(),
                session.getStatus(),
                session.getTargetBatteryPercent(),
                session.getPowerLimitKw(),
                session.getStartedAt(),
                session.getStoppedAt(),
                session.getKwhDelivered(),
                session.getBatteryPercent(),
                session.getEndReason(),
                session.getRealCost(),
                session.getDepositApplied(),
                session.getFinalAmount(),
                session.getAmountToCharge(),
                session.getPaymentStatus());
    }
}
