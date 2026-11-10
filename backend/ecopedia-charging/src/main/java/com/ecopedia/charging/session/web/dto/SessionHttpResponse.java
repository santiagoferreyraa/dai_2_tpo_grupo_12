package com.ecopedia.charging.session.web.dto;

import com.ecopedia.charging.session.domain.ChargingSessionView;
import com.ecopedia.charging.session.domain.SessionEndReason;
import com.ecopedia.charging.session.domain.SessionPaymentStatus;
import com.ecopedia.charging.session.domain.SessionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una sesión de carga para la pantalla del conductor (RF11, ECO-44): mientras carga y, al terminar,
 * con lo que costó y cómo se cobró. Ver {@link ChargingSessionView}.
 */
public record SessionHttpResponse(
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

    public static SessionHttpResponse fromDomain(ChargingSessionView view) {
        return new SessionHttpResponse(
                view.sessionId(),
                view.bookingId(),
                view.connectorId(),
                view.status(),
                view.targetBatteryPercent(),
                view.powerLimitKw(),
                view.startedAt(),
                view.stoppedAt(),
                view.kwhDelivered(),
                view.batteryPercent(),
                view.endReason(),
                view.realCost(),
                view.depositApplied(),
                view.finalAmount(),
                view.amountToCharge(),
                view.paymentStatus());
    }
}
