package com.ecopedia.async.stats.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Tablero consolidado de uso de la red o de un operador (ECO-301). */
public record UsageSummary(
        String scope,
        Instant from,
        Instant to,
        Totals totals,
        List<StationUsage> byStation,
        List<ConnectorTypeUsage> byConnectorType,
        List<RecentSessionUsage> recentSessions) {

    public UsageSummary {
        byStation = byStation == null ? List.of() : List.copyOf(byStation);
        byConnectorType = byConnectorType == null ? List.of() : List.copyOf(byConnectorType);
        recentSessions = recentSessions == null ? List.of() : List.copyOf(recentSessions);
    }

    public record Totals(
            long totalSessions,
            long activeSessions,
            long finishedSessions,
            long faultedSessions,
            BigDecimal totalEnergyKwh,
            BigDecimal averageEnergyKwh,
            BigDecimal peakPowerKw,
            long totalReadings) {}
}
