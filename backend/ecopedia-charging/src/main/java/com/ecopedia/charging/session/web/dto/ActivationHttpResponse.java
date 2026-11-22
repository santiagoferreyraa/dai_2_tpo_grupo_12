package com.ecopedia.charging.session.web.dto;

import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.SessionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ActivationHttpResponse(
        String sessionId,
        Long bookingId,
        Long connectorId,
        Long driverId,
        int targetBatteryPercent,
        BigDecimal powerLimitKw,
        SessionStatus status,
        Instant startedAt) {

    public static ActivationHttpResponse fromDomain(ActivationResult result) {
        return new ActivationHttpResponse(
                result.sessionId(),
                result.bookingId(),
                result.connectorId(),
                result.driverId(),
                result.targetBatteryPercent(),
                result.powerLimitKw(),
                result.status(),
                result.startedAt());
    }
}
