package com.ecopedia.charging.session.web.dto;

import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.SessionStopResult;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Respuesta HTTP al detener y liquidar una sesión de carga (RF12, RF14, ECO-55).
 */
public record SessionStopResponse(
        String sessionId,
        Long bookingId,
        Long connectorId,
        SessionStatus status,
        Instant startedAt,
        Instant stoppedAt,
        BigDecimal kwhDelivered,
        BigDecimal depositAmount,
        BigDecimal realCost,
        BigDecimal finalBilled,
        BigDecimal additionalCharge,
        ConsumptionPaymentStatus paymentStatus) {

    public static SessionStopResponse fromDomain(SessionStopResult result) {
        return new SessionStopResponse(
                result.sessionId(),
                result.bookingId(),
                result.connectorId(),
                result.status(),
                result.startedAt(),
                result.stoppedAt(),
                result.kwhDelivered(),
                result.depositAmount(),
                result.realCost(),
                result.finalBilled(),
                result.additionalCharge(),
                result.paymentStatus());
    }
}
