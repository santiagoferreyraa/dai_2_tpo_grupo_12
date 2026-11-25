package com.ecopedia.async.stats.service;

import com.ecopedia.async.stats.domain.ConnectorTypeUsage;
import com.ecopedia.async.stats.domain.InvalidDateRangeException;
import com.ecopedia.async.stats.domain.RecentSessionUsage;
import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StationUsage;
import com.ecopedia.async.stats.domain.StatsStationDirectory;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import com.ecopedia.async.stats.domain.UsageSessionStatus;
import com.ecopedia.async.stats.domain.UsageStatsService;
import com.ecopedia.async.stats.domain.UsageSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de los casos de uso de Estadísticas (ECO-301).
 *
 * <p>Enriquece datos de estación y propietario contra {@code ecopedia-core} fuera de la transacción
 * de escritura para no retener conexiones JDBC ante latencias externas.
 */
@Service
public class UsageStatsServiceImpl implements UsageStatsService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int RECENT_SESSIONS_LIMIT = 20;
    private static final String UNKNOWN_CONNECTOR_TYPE = "DESCONOCIDO";

    private final SessionUsageStatRepository repository;
    private final StatsStationDirectory stationDirectory;
    private final StatsPersistenceCoordinator coordinator;
    private final Clock clock;

    public UsageStatsServiceImpl(
            SessionUsageStatRepository repository,
            StatsStationDirectory stationDirectory,
            StatsPersistenceCoordinator coordinator,
            Clock clock) {
        this.repository = repository;
        this.stationDirectory = stationDirectory;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Override
    public boolean recordTelemetry(StatsTelemetryReading reading) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        if (reading.sessionId() == null || reading.sessionId().isBlank()) {
            throw new IllegalArgumentException("La lectura de telemetría debe incluir sessionId");
        }
        if (reading.type() == null || reading.timestamp() == null) {
            throw new IllegalArgumentException("La lectura de telemetría debe incluir type y timestamp");
        }

        Optional<SessionUsageStat> existing = repository.findBySessionId(reading.sessionId());
        StationOwnershipInfo stationInfo = null;
        if (existing.isEmpty() || existing.get().needsStationEnrichment()) {
            stationInfo =
                    stationDirectory.findByConnector(reading.connectorId()).orElse(null);
        }

        return coordinator.upsertReading(reading, stationInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public UsageSummary getUsageSummary(Long ownerId, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }
        Instant effectiveTo = to != null ? to : clock.instant();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new InvalidDateRangeException();
        }

        List<SessionUsageStat> stats = ownerId == null
                ? repository.findInRange(effectiveFrom, effectiveTo)
                : repository.findByOwnerInRange(ownerId, effectiveFrom, effectiveTo);

        String scope = ownerId == null ? "NETWORK" : "OPERATOR";
        UsageSummary.Totals totals = computeTotals(stats);
        List<StationUsage> byStation = aggregateByStation(stats);
        List<ConnectorTypeUsage> byConnectorType = aggregateByConnectorType(stats);
        List<RecentSessionUsage> recentSessions = mapRecentSessions(stats);

        return new UsageSummary(scope, effectiveFrom, effectiveTo, totals, byStation, byConnectorType, recentSessions);
    }

    private static UsageSummary.Totals computeTotals(List<SessionUsageStat> stats) {
        long totalSessions = stats.size();
        long active = 0;
        long finished = 0;
        long faulted = 0;
        BigDecimal totalEnergy = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        BigDecimal peakPower = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        long totalReadings = 0;

        for (SessionUsageStat stat : stats) {
            switch (stat.getStatus()) {
                case ACTIVE -> active++;
                case FINISHED -> finished++;
                case FAULTED -> faulted++;
            }
            totalEnergy = totalEnergy.add(stat.getEnergyKwh());
            if (stat.getPeakPowerKw().compareTo(peakPower) > 0) {
                peakPower = stat.getPeakPowerKw();
            }
            totalReadings += stat.getReadingsCount();
        }

        BigDecimal averageEnergy = totalSessions == 0
                ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
                : totalEnergy.divide(BigDecimal.valueOf(totalSessions), 3, RoundingMode.HALF_UP);

        return new UsageSummary.Totals(
                totalSessions, active, finished, faulted, totalEnergy, averageEnergy, peakPower, totalReadings);
    }

    private static List<StationUsage> aggregateByStation(List<SessionUsageStat> stats) {
        Map<StationKey, List<SessionUsageStat>> grouped = stats.stream()
                .collect(Collectors.groupingBy(s -> new StationKey(s.getStationId(), s.getStationName())));

        return grouped.entrySet().stream()
                .map(entry -> {
                    StationKey key = entry.getKey();
                    List<SessionUsageStat> group = entry.getValue();
                    long active = group.stream()
                            .filter(s -> s.getStatus() == UsageSessionStatus.ACTIVE)
                            .count();
                    long faulted = group.stream()
                            .filter(s -> s.getStatus() == UsageSessionStatus.FAULTED)
                            .count();
                    BigDecimal energy = group.stream()
                            .map(SessionUsageStat::getEnergyKwh)
                            .reduce(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP), BigDecimal::add);
                    BigDecimal peak = group.stream()
                            .map(SessionUsageStat::getPeakPowerKw)
                            .max(Comparator.naturalOrder())
                            .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    return new StationUsage(
                            key.stationId(), key.stationName(), group.size(), active, faulted, energy, peak);
                })
                .sorted(Comparator.comparing(StationUsage::totalEnergyKwh).reversed())
                .toList();
    }

    private static List<ConnectorTypeUsage> aggregateByConnectorType(List<SessionUsageStat> stats) {
        Map<String, List<SessionUsageStat>> grouped = stats.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getConnectorType() != null ? s.getConnectorType() : UNKNOWN_CONNECTOR_TYPE));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<SessionUsageStat> group = entry.getValue();
                    BigDecimal energy = group.stream()
                            .map(SessionUsageStat::getEnergyKwh)
                            .reduce(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP), BigDecimal::add);
                    return new ConnectorTypeUsage(entry.getKey(), group.size(), energy);
                })
                .sorted(Comparator.comparing(ConnectorTypeUsage::totalEnergyKwh).reversed())
                .toList();
    }

    private static List<RecentSessionUsage> mapRecentSessions(List<SessionUsageStat> stats) {
        return stats.stream()
                .sorted(Comparator.comparing(SessionUsageStat::getLastReadingAt).reversed())
                .limit(RECENT_SESSIONS_LIMIT)
                .map(s -> new RecentSessionUsage(
                        s.getSessionId(),
                        s.getConnectorId(),
                        s.getStationId(),
                        s.getStationName(),
                        s.getConnectorType(),
                        s.getStatus(),
                        s.getEndReason(),
                        s.getStartedAt(),
                        s.getLastReadingAt(),
                        s.getEndedAt(),
                        s.getEnergyKwh(),
                        s.getPeakPowerKw(),
                        s.getLastPowerKw(),
                        s.getLastBatteryPercent()))
                .toList();
    }

    private record StationKey(Long stationId, String stationName) {}
}
