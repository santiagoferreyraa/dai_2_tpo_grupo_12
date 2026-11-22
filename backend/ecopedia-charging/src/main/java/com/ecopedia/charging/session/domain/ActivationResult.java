package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record ActivationResult(
        String sessionId,
        Long bookingId,
        Long connectorId,
        Long driverId,
        int targetBatteryPercent,
        BigDecimal powerLimitKw,
        SessionStatus status,
        Instant startedAt) {

    public static ActivationResult fromSession(ChargingSession session) {
        return new ActivationResult(
                session.getSessionId(),
                session.getBookingId(),
                session.getConnectorId(),
                session.getDriverId(),
                session.getTargetBatteryPercent(),
                session.getPowerLimitKw(),
                session.getStatus(),
                session.getStartedAt());
    }
}
