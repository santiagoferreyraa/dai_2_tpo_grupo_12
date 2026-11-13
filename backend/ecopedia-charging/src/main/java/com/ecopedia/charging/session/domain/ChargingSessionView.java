package com.ecopedia.charging.session.domain;

import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una sesión de carga como la ve su conductor (RF11, ECO-44): mientras carga, con la última lectura
 * del cargador (RF16), y después, con la liquidación que quedó guardada.
 *
 * <p>Los importes y el estado del cobro son {@code null} mientras la sesión sigue activa.
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
        BigDecimal currentPowerKw,
        BigDecimal batteryPercent,
        Instant lastReadingAt,
        BigDecimal depositAmount,
        BigDecimal realCost,
        BigDecimal finalBilled,
        BigDecimal additionalCharge,
        ConsumptionPaymentStatus paymentStatus) {

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
                session.getCurrentPowerKw(),
                session.getBatteryPercent(),
                session.getLastReadingAt(),
                session.getDepositApplied(),
                session.getRealCost(),
                session.getFinalBilled(),
                session.getAdditionalCharge(),
                session.getPaymentStatus());
    }
}
