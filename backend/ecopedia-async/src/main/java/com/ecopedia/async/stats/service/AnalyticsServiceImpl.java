package com.ecopedia.async.stats.service;

import com.ecopedia.async.stats.domain.AnalyticsDashboard;
import com.ecopedia.async.stats.domain.AnalyticsDashboard.ConnectorDelivery;
import com.ecopedia.async.stats.domain.AnalyticsDashboard.SlotDemand;
import com.ecopedia.async.stats.domain.AnalyticsDashboard.StationDemand;
import com.ecopedia.async.stats.domain.AnalyticsService;
import com.ecopedia.async.stats.domain.DemandSlot;
import com.ecopedia.async.stats.domain.DemandSlotRepository;
import com.ecopedia.async.stats.domain.InvalidDateRangeException;
import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsStationDirectory;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de Estadísticas (ECO-301).
 *
 * <p>Escribir: consulta a {@code ecopedia-core} la estación del conector fuera de la transacción y
 * le pasa la lectura a {@link StatsPersistenceCoordinator}. Leer: arma el tablero sumando franjas,
 * sin tocar lecturas crudas.
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    static final Duration DEFAULT_RANGE = Duration.ofDays(1);
    static final long MAX_RANGE_DAYS = 31;

    private static final int ENERGY_SCALE = 3;
    private static final int POWER_SCALE = 2;
    /** Franjas por hora: la potencia media de una franja son sus kWh por esto. */
    private static final BigDecimal SLOTS_PER_HOUR =
            BigDecimal.valueOf(Duration.ofHours(1).dividedBy(DemandSlot.SLOT));

    private final SessionUsageStatRepository sessions;
    private final DemandSlotRepository slots;
    private final StatsStationDirectory stationDirectory;
    private final StatsPersistenceCoordinator coordinator;
    private final Clock clock;

    public AnalyticsServiceImpl(
            SessionUsageStatRepository sessions,
            DemandSlotRepository slots,
            StatsStationDirectory stationDirectory,
            StatsPersistenceCoordinator coordinator,
            Clock clock) {
        this.sessions = sessions;
        this.slots = slots;
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

        Optional<SessionUsageStat> existing = sessions.findBySessionId(reading.sessionId());
        StationOwnershipInfo stationInfo = null;
        if (existing.isEmpty() || existing.get().needsStationEnrichment()) {
            stationInfo =
                    stationDirectory.findByConnector(reading.connectorId()).orElse(null);
        }

        return coordinator.upsertReading(reading, stationInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDashboard getDashboard(Long ownerId, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw InvalidDateRangeException.inverted();
        }
        Instant effectiveTo = to != null ? to : clock.instant();
        Instant effectiveFrom = DemandSlot.startOf(from != null ? from : effectiveTo.minus(DEFAULT_RANGE));
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw InvalidDateRangeException.inverted();
        }
        if (Duration.between(effectiveFrom, effectiveTo).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw InvalidDateRangeException.tooLong(MAX_RANGE_DAYS);
        }

        List<DemandSlot> rows = ownerId == null
                ? slots.findInRange(effectiveFrom, effectiveTo)
                : slots.findByOwnerInRange(ownerId, effectiveFrom, effectiveTo);

        return new AnalyticsDashboard(
                ownerId == null ? "NETWORK" : "OPERATOR",
                effectiveFrom,
                effectiveTo,
                (int) DemandSlot.SLOT.toMinutes(),
                totals(rows),
                byStation(rows),
                byConnector(rows));
    }

    private static AnalyticsDashboard.Totals totals(List<DemandSlot> rows) {
        Peak peak = peakOf(energyBySlot(rows));
        return new AnalyticsDashboard.Totals(energyOf(rows), sessionsOf(rows), peak.demandKw(), peak.at());
    }

    private static List<StationDemand> byStation(List<DemandSlot> rows) {
        // HashMap y no groupingBy: las franjas sin estación (core caído) tienen id null.
        Map<Long, List<DemandSlot>> grouped = new HashMap<>();
        for (DemandSlot row : rows) {
            grouped.computeIfAbsent(row.getStationId(), id -> new ArrayList<>()).add(row);
        }

        List<StationDemand> result = new ArrayList<>();
        for (Map.Entry<Long, List<DemandSlot>> entry : grouped.entrySet()) {
            List<DemandSlot> group = entry.getValue();
            Peak peak = peakOf(energyBySlot(group));
            result.add(new StationDemand(
                    entry.getKey(),
                    latestStationName(group),
                    energyOf(group),
                    sessionsOf(group),
                    peak.demandKw(),
                    peak.at(),
                    slotDemands(group)));
        }
        result.sort(Comparator.comparing(StationDemand::energyKwh).reversed());
        return result;
    }

    private static List<ConnectorDelivery> byConnector(List<DemandSlot> rows) {
        Map<Long, List<DemandSlot>> grouped = new LinkedHashMap<>();
        for (DemandSlot row : rows) {
            grouped.computeIfAbsent(row.getConnectorId(), id -> new ArrayList<>())
                    .add(row);
        }

        List<ConnectorDelivery> result = new ArrayList<>();
        for (Map.Entry<Long, List<DemandSlot>> entry : grouped.entrySet()) {
            List<DemandSlot> group = entry.getValue();
            DemandSlot reference = group.get(group.size() - 1);
            BigDecimal peakPower = group.stream()
                    .map(DemandSlot::getPeakPowerKw)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO)
                    .setScale(POWER_SCALE, RoundingMode.HALF_UP);
            result.add(new ConnectorDelivery(
                    entry.getKey(),
                    reference.getStationId(),
                    reference.getStationName(),
                    reference.getConnectorType(),
                    energyOf(group),
                    sessionsOf(group),
                    peakPower,
                    peakOf(energyBySlot(group)).demandKw(),
                    slotDemands(group)));
        }
        result.sort(Comparator.comparing(ConnectorDelivery::energyKwh).reversed());
        return result;
    }

    /** Las franjas del grupo, sumando los conectores que caen en la misma, en orden. */
    private static List<SlotDemand> slotDemands(List<DemandSlot> group) {
        NavigableMap<Instant, List<DemandSlot>> perSlot = new TreeMap<>();
        for (DemandSlot row : group) {
            perSlot.computeIfAbsent(row.getSlotStart(), start -> new ArrayList<>())
                    .add(row);
        }
        return perSlot.entrySet().stream()
                .map(slot -> {
                    BigDecimal energy = energyOf(slot.getValue());
                    return new SlotDemand(slot.getKey(), energy, demandOf(energy), sessionsOf(slot.getValue()));
                })
                .toList();
    }

    /** La energía de cada franja sumando todos los conectores: la base de la potencia simultánea. */
    private static Map<Instant, BigDecimal> energyBySlot(List<DemandSlot> rows) {
        Map<Instant, BigDecimal> perSlot = new TreeMap<>();
        for (DemandSlot row : rows) {
            perSlot.merge(row.getSlotStart(), row.getEnergyKwh(), BigDecimal::add);
        }
        return perSlot;
    }

    /** La franja de mayor demanda. Ante empate, la primera. Sin franjas, cero y sin momento. */
    private static Peak peakOf(Map<Instant, BigDecimal> energyBySlot) {
        Instant at = null;
        BigDecimal max = BigDecimal.ZERO;
        for (Map.Entry<Instant, BigDecimal> slot : energyBySlot.entrySet()) {
            if (at == null || slot.getValue().compareTo(max) > 0) {
                at = slot.getKey();
                max = slot.getValue();
            }
        }
        return new Peak(demandOf(max), at);
    }

    private static BigDecimal demandOf(BigDecimal energyKwh) {
        return energyKwh.multiply(SLOTS_PER_HOUR).setScale(POWER_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal energyOf(List<DemandSlot> rows) {
        return rows.stream()
                .map(DemandSlot::getEnergyKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ENERGY_SCALE, RoundingMode.HALF_UP);
    }

    private static long sessionsOf(List<DemandSlot> rows) {
        return rows.stream().mapToLong(DemandSlot::getSessionsStarted).sum();
    }

    /** El nombre de la franja más reciente, por si la estación se renombró en el medio. */
    private static String latestStationName(List<DemandSlot> group) {
        return group.stream()
                .max(Comparator.comparing(DemandSlot::getSlotStart))
                .map(DemandSlot::getStationName)
                .orElse(null);
    }

    private record Peak(BigDecimal demandKw, Instant at) {}
}
