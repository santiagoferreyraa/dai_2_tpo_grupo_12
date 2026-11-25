package com.ecopedia.async.stats.web.dto;

import com.ecopedia.async.stats.domain.AnalyticsDashboard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** La respuesta de {@code GET /api/analytics} (ECO-301). Ver {@link AnalyticsDashboard}. */
public record AnalyticsResponse(
        String scope,
        Instant from,
        Instant to,
        int slotMinutes,
        TotalsDto totals,
        List<StationDemandDto> stations,
        List<ConnectorDeliveryDto> connectors) {

    public static AnalyticsResponse fromDomain(AnalyticsDashboard dashboard) {
        AnalyticsDashboard.Totals totals = dashboard.totals();
        return new AnalyticsResponse(
                dashboard.scope(),
                dashboard.from(),
                dashboard.to(),
                dashboard.slotMinutes(),
                new TotalsDto(
                        totals.energyKwh(),
                        totals.sessions(),
                        totals.peakSimultaneousPowerKw(),
                        totals.peakSimultaneousAt()),
                dashboard.stations().stream().map(StationDemandDto::fromDomain).toList(),
                dashboard.connectors().stream()
                        .map(ConnectorDeliveryDto::fromDomain)
                        .toList());
    }

    public record TotalsDto(
            BigDecimal energyKwh, long sessions, BigDecimal peakSimultaneousPowerKw, Instant peakSimultaneousAt) {}

    public record StationDemandDto(
            Long stationId,
            String stationName,
            BigDecimal energyKwh,
            long sessions,
            BigDecimal peakSimultaneousPowerKw,
            Instant peakSimultaneousAt,
            List<SlotDemandDto> slots) {

        static StationDemandDto fromDomain(AnalyticsDashboard.StationDemand station) {
            return new StationDemandDto(
                    station.stationId(),
                    station.stationName(),
                    station.energyKwh(),
                    station.sessions(),
                    station.peakSimultaneousPowerKw(),
                    station.peakSimultaneousAt(),
                    station.slots().stream()
                            .map(slot -> new SlotDemandDto(
                                    slot.start(), slot.energyKwh(), slot.demandKw(), slot.sessionsStarted()))
                            .toList());
        }
    }

    public record SlotDemandDto(Instant start, BigDecimal energyKwh, BigDecimal demandKw, long sessionsStarted) {}

    public record ConnectorDeliveryDto(
            Long connectorId,
            Long stationId,
            String stationName,
            String connectorType,
            BigDecimal energyKwh,
            long sessions,
            BigDecimal peakPowerKw,
            BigDecimal peakDemandKw) {

        static ConnectorDeliveryDto fromDomain(AnalyticsDashboard.ConnectorDelivery connector) {
            return new ConnectorDeliveryDto(
                    connector.connectorId(),
                    connector.stationId(),
                    connector.stationName(),
                    connector.connectorType(),
                    connector.energyKwh(),
                    connector.sessions(),
                    connector.peakPowerKw(),
                    connector.peakDemandKw());
        }
    }
}
