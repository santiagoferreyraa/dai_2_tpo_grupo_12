package com.ecopedia.async.stats.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Detalle resumido de una sesión reciente en el tablero de uso (ECO-301). */
public record RecentSessionUsage(
        String sessionId,
        Long connectorId,
        Long stationId,
        String stationName,
        String connectorType,
        UsageSessionStatus status,
        StatsTelemetryReading.EndReason endReason,
        Instant startedAt,
        Instant lastReadingAt,
        Instant endedAt,
        BigDecimal energyKwh,
        BigDecimal peakPowerKw,
        BigDecimal lastPowerKw,
        BigDecimal lastBatteryPercent) {}
