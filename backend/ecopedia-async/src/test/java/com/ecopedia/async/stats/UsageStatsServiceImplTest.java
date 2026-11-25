package com.ecopedia.async.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.async.stats.domain.InvalidDateRangeException;
import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsStationDirectory;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import com.ecopedia.async.stats.domain.UsageSummary;
import com.ecopedia.async.stats.service.StatsPersistenceCoordinator;
import com.ecopedia.async.stats.service.UsageStatsServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsageStatsServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-25T14:00:00Z");

    private final SessionUsageStatRepository repository = mock(SessionUsageStatRepository.class);
    private final StatsStationDirectory stationDirectory = mock(StatsStationDirectory.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private UsageStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        StatsPersistenceCoordinator coordinator = new StatsPersistenceCoordinator(repository);
        service = new UsageStatsServiceImpl(repository, stationDirectory, coordinator, clock);
    }

    @Test
    @DisplayName("Primera lectura enriquece estación desde core; si core está caído persiste igual y reintenta luego")
    void enrichesOnFirstReadingAndRetriesWhenCoreWasDown() {
        StatsTelemetryReading first = new StatsTelemetryReading(
                "evt-1",
                StatsTelemetryReading.Type.READING,
                "sess-1",
                5L,
                NOW.minusSeconds(120),
                new BigDecimal("40.00"),
                new BigDecimal("4.500"),
                new BigDecimal("30.00"),
                null);

        when(repository.findBySessionId("sess-1")).thenReturn(Optional.empty());
        when(stationDirectory.findByConnector(5L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.recordTelemetry(first)).isTrue();
        verify(repository).save(any(SessionUsageStat.class));

        // Segunda lectura con core ya disponible -> re-enriquece la fila existente
        SessionUsageStat persistedWithoutStation = SessionUsageStat.fromFirstReading(first, null);
        StatsTelemetryReading second = new StatsTelemetryReading(
                "evt-2",
                StatsTelemetryReading.Type.FINISHED,
                "sess-1",
                5L,
                NOW.minusSeconds(60),
                BigDecimal.ZERO,
                new BigDecimal("10.000"),
                new BigDecimal("80.00"),
                StatsTelemetryReading.EndReason.STOPPED);

        when(repository.findBySessionId("sess-1")).thenReturn(Optional.of(persistedWithoutStation));
        when(stationDirectory.findByConnector(5L))
                .thenReturn(Optional.of(new StationOwnershipInfo(2L, "Palermo", 7L, "CCS2")));

        assertThat(service.recordTelemetry(second)).isTrue();
        assertThat(persistedWithoutStation.getStationId()).isEqualTo(2L);
        assertThat(persistedWithoutStation.getStationOwnerId()).isEqualTo(7L);
        assertThat(persistedWithoutStation.getConnectorType()).isEqualTo("CCS2");
    }

    @Test
    @DisplayName("Lectura inválida sin sessionId o timestamp lanza IllegalArgumentException")
    void rejectsInvalidReading() {
        StatsTelemetryReading invalid = new StatsTelemetryReading(
                "evt-x", StatsTelemetryReading.Type.READING, " ", 1L, NOW, BigDecimal.ONE, BigDecimal.ONE, null, null);

        assertThatThrownBy(() -> service.recordTelemetry(invalid)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("getUsageSummary agrega métricas para ADMIN (NETWORK) y CPO (OPERATOR), y maneja 0 sesiones sin NaN")
    void summarizesForAdminAndCpoAndEmptyCase() {
        Instant from = NOW.minusSeconds(3600);
        SessionUsageStat s1 = SessionUsageStat.fromFirstReading(
                new StatsTelemetryReading(
                        "e1",
                        StatsTelemetryReading.Type.FINISHED,
                        "s1",
                        1L,
                        from.plusSeconds(60),
                        new BigDecimal("50.00"),
                        new BigDecimal("20.000"),
                        new BigDecimal("80.00"),
                        StatsTelemetryReading.EndReason.STOPPED),
                new StationOwnershipInfo(1L, "Obelisco", 7L, "CCS2"));
        SessionUsageStat s2 = SessionUsageStat.fromFirstReading(
                new StatsTelemetryReading(
                        "e2",
                        StatsTelemetryReading.Type.READING,
                        "s2",
                        2L,
                        from.plusSeconds(120),
                        new BigDecimal("22.00"),
                        new BigDecimal("10.000"),
                        new BigDecimal("50.00"),
                        null),
                new StationOwnershipInfo(2L, "Belgrano", 8L, "TYPE2"));

        when(repository.findInRange(from, NOW)).thenReturn(List.of(s1, s2));
        UsageSummary adminSummary = service.getUsageSummary(null, from, NOW);

        assertThat(adminSummary.scope()).isEqualTo("NETWORK");
        assertThat(adminSummary.totals().totalSessions()).isEqualTo(2);
        assertThat(adminSummary.totals().activeSessions()).isEqualTo(1);
        assertThat(adminSummary.totals().finishedSessions()).isEqualTo(1);
        assertThat(adminSummary.totals().totalEnergyKwh()).isEqualByComparingTo("30.000");
        assertThat(adminSummary.totals().averageEnergyKwh()).isEqualByComparingTo("15.000");
        assertThat(adminSummary.totals().peakPowerKw()).isEqualByComparingTo("50.00");
        assertThat(adminSummary.byStation()).hasSize(2);
        assertThat(adminSummary.byConnectorType()).hasSize(2);

        when(repository.findByOwnerInRange(7L, from, NOW)).thenReturn(List.of(s1));
        UsageSummary cpoSummary = service.getUsageSummary(7L, from, NOW);
        assertThat(cpoSummary.scope()).isEqualTo("OPERATOR");
        assertThat(cpoSummary.totals().totalSessions()).isEqualTo(1);

        when(repository.findInRange(any(), any())).thenReturn(List.of());
        UsageSummary emptySummary = service.getUsageSummary(null, null, null);
        assertThat(emptySummary.totals().totalSessions()).isZero();
        assertThat(emptySummary.totals().averageEnergyKwh()).isEqualByComparingTo("0.000");
        assertThat(emptySummary.totals().peakPowerKw()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("getUsageSummary con from > to lanza InvalidDateRangeException")
    void rejectsInvertedDateRange() {
        Instant from = NOW;
        Instant to = NOW.minusSeconds(60);

        assertThatThrownBy(() -> service.getUsageSummary(null, from, to)).isInstanceOf(InvalidDateRangeException.class);
    }
}
