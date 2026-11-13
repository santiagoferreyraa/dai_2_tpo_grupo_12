package com.ecopedia.charging.session.web.dto;

import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.session.domain.ChargingSessionView;
import com.ecopedia.charging.session.domain.SessionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una sesión de carga para la pantalla del conductor (RF11, ECO-44): mientras carga, con lo último
 * que informó el cargador, y al terminar, con lo que costó. Los nombres de la liquidación son los de
 * {@link SessionStopResponse}, para que el front lea las dos con el mismo tipo.
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
        BigDecimal currentPowerKw,
        BigDecimal batteryPercent,
        Instant lastReadingAt,
        BigDecimal depositAmount,
        BigDecimal realCost,
        BigDecimal finalBilled,
        BigDecimal additionalCharge,
        ConsumptionPaymentStatus paymentStatus) {

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
                view.currentPowerKw(),
                view.batteryPercent(),
                view.lastReadingAt(),
                view.depositAmount(),
                view.realCost(),
                view.finalBilled(),
                view.additionalCharge(),
                view.paymentStatus());
    }
}
