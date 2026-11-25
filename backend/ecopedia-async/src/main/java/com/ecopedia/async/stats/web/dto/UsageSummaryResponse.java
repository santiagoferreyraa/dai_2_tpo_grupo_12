package com.ecopedia.async.stats.web.dto;

import com.ecopedia.async.stats.domain.ConnectorTypeUsage;
import com.ecopedia.async.stats.domain.RecentSessionUsage;
import com.ecopedia.async.stats.domain.StationUsage;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import com.ecopedia.async.stats.domain.UsageSessionStatus;
import com.ecopedia.async.stats.domain.UsageSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Respuesta HTTP del tablero de uso {@code GET /api/stats/usage} (ECO-301). */
public record UsageSummaryResponse(
        String scope,
        Instant from,
        Instant to,
        TotalsDto totals,
        List<StationUsageDto> byStation,
        List<ConnectorTypeUsageDto> byConnectorType,
        List<RecentSessionUsageDto> recentSessions) {

    public static UsageSummaryResponse fromDomain(UsageSummary summary) {
        return new UsageSummaryResponse(
                summary.scope(),
                summary.from(),
                summary.to(),
                TotalsDto.fromDomain(summary.totals()),
                summary.byStation().stream().map(StationUsageDto::fromDomain).toList(),
                summary.byConnectorType().stream()
                        .map(ConnectorTypeUsageDto::fromDomain)
                        .toList(),
                summary.recentSessions().stream()
                        .map(RecentSessionUsageDto::fromDomain)
                        .toList());
    }

    public record TotalsDto(
            long totalSessions,
            long activeSessions,
            long finishedSessions,
            long faultedSessions,
            BigDecimal totalEnergyKwh,
            BigDecimal averageEnergyKwh,
            BigDecimal peakPowerKw,
            long totalReadings) {

        static TotalsDto fromDomain(UsageSummary.Totals totals) {
            return new TotalsDto(
                    totals.totalSessions(),
                    totals.activeSessions(),
                    totals.finishedSessions(),
                    totals.faultedSessions(),
                    totals.totalEnergyKwh(),
                    totals.averageEnergyKwh(),
                    totals.peakPowerKw(),
                    totals.totalReadings());
        }
    }

    public record StationUsageDto(
            Long stationId,
            String stationName,
            long totalSessions,
            long activeSessions,
            long faultedSessions,
            BigDecimal totalEnergyKwh,
            BigDecimal peakPowerKw) {

        static StationUsageDto fromDomain(StationUsage usage) {
            return new StationUsageDto(
                    usage.stationId(),
                    usage.stationName(),
                    usage.totalSessions(),
                    usage.activeSessions(),
                    usage.faultedSessions(),
                    usage.totalEnergyKwh(),
                    usage.peakPowerKw());
        }
    }

    public record ConnectorTypeUsageDto(String connectorType, long totalSessions, BigDecimal totalEnergyKwh) {

        static ConnectorTypeUsageDto fromDomain(ConnectorTypeUsage usage) {
            return new ConnectorTypeUsageDto(usage.connectorType(), usage.totalSessions(), usage.totalEnergyKwh());
        }
    }

    public record RecentSessionUsageDto(
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
            BigDecimal lastBatteryPercent) {

        static RecentSessionUsageDto fromDomain(RecentSessionUsage usage) {
            return new RecentSessionUsageDto(
                    usage.sessionId(),
                    usage.connectorId(),
                    usage.stationId(),
                    usage.stationName(),
                    usage.connectorType(),
                    usage.status(),
                    usage.endReason(),
                    usage.startedAt(),
                    usage.lastReadingAt(),
                    usage.endedAt(),
                    usage.energyKwh(),
                    usage.peakPowerKw(),
                    usage.lastPowerKw(),
                    usage.lastBatteryPercent());
        }
    }
}
