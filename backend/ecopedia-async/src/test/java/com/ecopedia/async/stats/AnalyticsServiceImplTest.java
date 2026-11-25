package com.ecopedia.async.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecopedia.async.stats.domain.AnalyticsDashboard;
import com.ecopedia.async.stats.domain.DemandSlot;
import com.ecopedia.async.stats.domain.DemandSlotRepository;
import com.ecopedia.async.stats.domain.InvalidDateRangeException;
import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsStationDirectory;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import com.ecopedia.async.stats.service.AnalyticsServiceImpl;
import com.ecopedia.async.stats.service.StatsPersistenceCoordinator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyticsServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-25T14:00:00Z");
    private static final StationOwnershipInfo PALERMO = new StationOwnershipInfo(2L, "Palermo", 7L, "CCS2");

    private final FakeSessions sessions = new FakeSessions();
    private final FakeSlots slots = new FakeSlots();
    private final StatsStationDirectory stationDirectory = mock(StatsStationDirectory.class);

    private AnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        StatsPersistenceCoordinator coordinator = new StatsPersistenceCoordinator(sessions, slots);
        service = new AnalyticsServiceImpl(
                sessions, slots, stationDirectory, coordinator, Clock.fixed(NOW, ZoneOffset.UTC));
        when(stationDirectory.findByConnector(anyLong())).thenReturn(Optional.of(PALERMO));
    }

    @Test
    @DisplayName("Reparte el acumulado de la sesión entre franjas de 15 minutos sin contar dos veces")
    void splitsCumulativeEnergyAcrossSlots() {
        service.recordTelemetry(reading("e1", "s1", 5L, "10:05", "0", "0.000"));
        service.recordTelemetry(reading("e2", "s1", 5L, "10:14", "22.00", "3.000"));
        service.recordTelemetry(reading("e3", "s1", 5L, "10:20", "24.00", "5.000"));
        // Repetida y atrasada: ninguna suma.
        assertThat(service.recordTelemetry(reading("e3", "s1", 5L, "10:20", "24.00", "5.000")))
                .isFalse();
        assertThat(service.recordTelemetry(reading("e2", "s1", 5L, "10:14", "22.00", "3.000")))
                .isFalse();

        DemandSlot first = slots.get(5L, "10:00");
        DemandSlot second = slots.get(5L, "10:15");
        assertThat(first.getEnergyKwh()).isEqualByComparingTo("3.000");
        assertThat(first.getSessionsStarted()).isEqualTo(1);
        assertThat(first.getPeakPowerKw()).isEqualByComparingTo("22.00");
        assertThat(second.getEnergyKwh()).isEqualByComparingTo("2.000");
        assertThat(second.getSessionsStarted()).isZero();
        assertThat(second.getStationOwnerId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Si core no respondía, la franja se completa con la estación cuando vuelve")
    void backfillsStationWhenCoreComesBack() {
        when(stationDirectory.findByConnector(5L)).thenReturn(Optional.empty());
        service.recordTelemetry(reading("e1", "s1", 5L, "10:05", "20.00", "1.000"));
        assertThat(slots.get(5L, "10:00").getStationId()).isNull();

        when(stationDirectory.findByConnector(5L)).thenReturn(Optional.of(PALERMO));
        service.recordTelemetry(reading("e2", "s1", 5L, "10:20", "20.00", "4.000"));

        assertThat(slots.get(5L, "10:00").getStationId()).isEqualTo(2L);
        assertThat(slots.get(5L, "10:00").getStationOwnerId()).isEqualTo(7L);
        assertThat(slots.get(5L, "10:15").getStationId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Lectura sin sessionId lanza IllegalArgumentException")
    void rejectsInvalidReading() {
        StatsTelemetryReading invalid = new StatsTelemetryReading(
                "evt-x", StatsTelemetryReading.Type.READING, " ", 1L, NOW, BigDecimal.ONE, BigDecimal.ONE, null, null);

        assertThatThrownBy(() -> service.recordTelemetry(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(slots.all()).isEmpty();
    }

    @Test
    @DisplayName("Demanda por estación y franja, pico simultáneo y potencia por conector")
    void buildsDashboardFromSlots() {
        StationOwnershipInfo belgrano = new StationOwnershipInfo(3L, "Belgrano", 8L, "TYPE2");
        // 10:00 → Palermo: 5 + 3 kWh en dos conectores = 32 kW simultáneos. Belgrano: 2 kWh.
        // 10:15 → Palermo: 4 kWh = 16 kW. Belgrano: 7 kWh = 28 kW.
        // Red: 10 kWh (40 kW) a las 10:00 y 11 kWh (44 kW) a las 10:15.
        List<DemandSlot> rows = List.of(
                slot(10L, "10:00", PALERMO, "5.000", "22.00", 1),
                slot(11L, "10:00", PALERMO, "3.000", "50.00", 1),
                slot(20L, "10:00", belgrano, "2.000", "11.00", 1),
                slot(10L, "10:15", PALERMO, "4.000", "20.00", 0),
                slot(20L, "10:15", belgrano, "7.000", "30.00", 0));
        Instant from = at("10:00");
        Instant to = at("11:00");
        slots.inRange = rows;

        AnalyticsDashboard dashboard = service.getDashboard(null, from, to);

        assertThat(dashboard.scope()).isEqualTo("NETWORK");
        assertThat(dashboard.slotMinutes()).isEqualTo(15);
        assertThat(dashboard.totals().energyKwh()).isEqualByComparingTo("21.000");
        assertThat(dashboard.totals().sessions()).isEqualTo(3);
        assertThat(dashboard.totals().peakSimultaneousPowerKw()).isEqualByComparingTo("44.00");
        assertThat(dashboard.totals().peakSimultaneousAt()).isEqualTo(at("10:15"));

        AnalyticsDashboard.StationDemand palermo = dashboard.stations().get(0);
        assertThat(palermo.stationName()).isEqualTo("Palermo");
        assertThat(palermo.energyKwh()).isEqualByComparingTo("12.000");
        assertThat(palermo.peakSimultaneousPowerKw()).isEqualByComparingTo("32.00");
        assertThat(palermo.peakSimultaneousAt()).isEqualTo(at("10:00"));
        assertThat(palermo.slots())
                .extracting(AnalyticsDashboard.SlotDemand::demandKw)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("32.00"), new BigDecimal("16.00"));

        AnalyticsDashboard.StationDemand belgranoDemand = dashboard.stations().get(1);
        assertThat(belgranoDemand.peakSimultaneousPowerKw()).isEqualByComparingTo("28.00");

        AnalyticsDashboard.ConnectorDelivery c10 = dashboard.connectors().stream()
                .filter(c -> c.connectorId() == 10L)
                .findFirst()
                .orElseThrow();
        assertThat(c10.energyKwh()).isEqualByComparingTo("9.000");
        assertThat(c10.peakPowerKw()).isEqualByComparingTo("22.00");
        assertThat(c10.peakDemandKw()).isEqualByComparingTo("20.00");
        assertThat(c10.connectorType()).isEqualTo("CCS2");
    }

    @Test
    @DisplayName("Sin franjas devuelve ceros, sin pico y las últimas 24 horas alineadas a franja")
    void emptyDashboardDefaultsToLastDay() {
        slots.inRange = List.of();

        AnalyticsDashboard dashboard = service.getDashboard(7L, null, null);

        assertThat(dashboard.scope()).isEqualTo("OPERATOR");
        assertThat(dashboard.from()).isEqualTo(NOW.minusSeconds(86_400));
        assertThat(dashboard.to()).isEqualTo(NOW);
        assertThat(dashboard.totals().energyKwh()).isEqualByComparingTo("0");
        assertThat(dashboard.totals().peakSimultaneousPowerKw()).isEqualByComparingTo("0");
        assertThat(dashboard.totals().peakSimultaneousAt()).isNull();
        assertThat(dashboard.stations()).isEmpty();
    }

    @Test
    @DisplayName("Rango invertido o de más de 31 días lanza InvalidDateRangeException")
    void rejectsInvalidRanges() {
        assertThatThrownBy(() -> service.getDashboard(null, NOW, NOW.minusSeconds(60)))
                .isInstanceOf(InvalidDateRangeException.class);
        assertThatThrownBy(() -> service.getDashboard(null, NOW.minusSeconds(32L * 86_400), NOW))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    private static Instant at(String hhmm) {
        return Instant.parse("2026-11-25T" + hhmm + ":00Z");
    }

    private static StatsTelemetryReading reading(
            String eventId, String sessionId, long connectorId, String hhmm, String power, String energy) {
        return new StatsTelemetryReading(
                eventId,
                StatsTelemetryReading.Type.READING,
                sessionId,
                connectorId,
                at(hhmm),
                new BigDecimal(power),
                new BigDecimal(energy),
                null,
                null);
    }

    private static DemandSlot slot(
            long connectorId, String hhmm, StationOwnershipInfo station, String energy, String power, int sessions) {
        DemandSlot slot = DemandSlot.open(connectorId, at(hhmm));
        slot.assignStation(station.stationId(), station.stationName(), station.ownerId(), station.connectorType());
        slot.addReading(new BigDecimal(energy), new BigDecimal(power), sessions > 0);
        return slot;
    }

    private static final class FakeSessions implements SessionUsageStatRepository {
        private final Map<String, SessionUsageStat> byId = new HashMap<>();

        @Override
        public Optional<SessionUsageStat> findBySessionId(String sessionId) {
            return Optional.ofNullable(byId.get(sessionId));
        }

        @Override
        public SessionUsageStat save(SessionUsageStat stat) {
            byId.put(stat.getSessionId(), stat);
            return stat;
        }
    }

    private static final class FakeSlots implements DemandSlotRepository {
        private final Map<String, DemandSlot> byKey = new HashMap<>();
        private List<DemandSlot> inRange = List.of();

        DemandSlot get(long connectorId, String hhmm) {
            return byKey.get(connectorId + "@" + at(hhmm));
        }

        List<DemandSlot> all() {
            return new ArrayList<>(byKey.values());
        }

        @Override
        public Optional<DemandSlot> findByConnectorIdAndSlotStart(Long connectorId, Instant slotStart) {
            return Optional.ofNullable(byKey.get(connectorId + "@" + slotStart));
        }

        @Override
        public DemandSlot save(DemandSlot slot) {
            byKey.put(slot.getConnectorId() + "@" + slot.getSlotStart(), slot);
            return slot;
        }

        @Override
        public List<DemandSlot> findByConnectorIdAndStationIdIsNull(Long connectorId) {
            return byKey.values().stream()
                    .filter(s -> s.getConnectorId().equals(connectorId) && s.getStationId() == null)
                    .sorted(Comparator.comparing(DemandSlot::getSlotStart))
                    .toList();
        }

        @Override
        public List<DemandSlot> findInRange(Instant from, Instant to) {
            return inRange;
        }

        @Override
        public List<DemandSlot> findByOwnerInRange(Long ownerId, Instant from, Instant to) {
            return inRange;
        }
    }
}
