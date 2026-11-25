package com.ecopedia.async.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import com.ecopedia.async.stats.domain.UsageSessionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionUsageStatTest {

    private static final Instant T0 = Instant.parse("2026-11-25T13:00:00Z");

    @Test
    @DisplayName("Crea proyección ACTIVE desde la primera lectura READING y acumula energía y potencia pico")
    void createsActiveFromFirstReadingAndAccumulatesMonotonically() {
        StatsTelemetryReading r1 = new StatsTelemetryReading(
                "evt-1",
                StatsTelemetryReading.Type.READING,
                "sess-1",
                10L,
                T0,
                new BigDecimal("42.50"),
                new BigDecimal("5.200"),
                new BigDecimal("35.00"),
                null);
        StationOwnershipInfo station = new StationOwnershipInfo(1L, "Estación Obelisco", 7L, "CCS2");

        SessionUsageStat stat = SessionUsageStat.fromFirstReading(r1, station);

        assertThat(stat.getStatus()).isEqualTo(UsageSessionStatus.ACTIVE);
        assertThat(stat.getEnergyKwh()).isEqualByComparingTo("5.200");
        assertThat(stat.getPeakPowerKw()).isEqualByComparingTo("42.50");
        assertThat(stat.getReadingsCount()).isEqualTo(1);
        assertThat(stat.needsStationEnrichment()).isFalse();

        StatsTelemetryReading r2 = new StatsTelemetryReading(
                "evt-2",
                StatsTelemetryReading.Type.READING,
                "sess-1",
                10L,
                T0.plusSeconds(30),
                new BigDecimal("48.00"),
                new BigDecimal("9.800"),
                new BigDecimal("44.00"),
                null);
        assertThat(stat.applyReading(r2)).isTrue();
        assertThat(stat.getEnergyKwh()).isEqualByComparingTo("9.800");
        assertThat(stat.getPeakPowerKw()).isEqualByComparingTo("48.00");
        assertThat(stat.getLastPowerKw()).isEqualByComparingTo("48.00");
        assertThat(stat.getLastBatteryPercent()).isEqualByComparingTo("44.00");
        assertThat(stat.getReadingsCount()).isEqualTo(2);

        // Lectura con menor potencia mantiene el pico y menor kWh no retrocede
        StatsTelemetryReading r3 = new StatsTelemetryReading(
                "evt-3",
                StatsTelemetryReading.Type.READING,
                "sess-1",
                10L,
                T0.plusSeconds(60),
                new BigDecimal("20.00"),
                new BigDecimal("9.500"),
                new BigDecimal("46.00"),
                null);
        assertThat(stat.applyReading(r3)).isTrue();
        assertThat(stat.getEnergyKwh()).isEqualByComparingTo("9.800");
        assertThat(stat.getPeakPowerKw()).isEqualByComparingTo("48.00");
        assertThat(stat.getLastPowerKw()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Descarta lectura duplicada por eventId y lectura atrasada por timestamp")
    void ignoresDuplicateEventIdAndOutOfOrderTimestamp() {
        StatsTelemetryReading r1 = new StatsTelemetryReading(
                "evt-1",
                StatsTelemetryReading.Type.READING,
                "sess-2",
                10L,
                T0.plusSeconds(60),
                new BigDecimal("40.00"),
                new BigDecimal("10.000"),
                new BigDecimal("50.00"),
                null);
        SessionUsageStat stat = SessionUsageStat.fromFirstReading(r1, null);
        assertThat(stat.needsStationEnrichment()).isTrue();

        // Duplicado exacto por eventId
        assertThat(stat.applyReading(r1)).isFalse();
        assertThat(stat.getReadingsCount()).isEqualTo(1);

        // Lectura atrasada por timestamp
        StatsTelemetryReading older = new StatsTelemetryReading(
                "evt-old",
                StatsTelemetryReading.Type.READING,
                "sess-2",
                10L,
                T0,
                new BigDecimal("50.00"),
                new BigDecimal("12.000"),
                new BigDecimal("60.00"),
                null);
        assertThat(stat.applyReading(older)).isFalse();
        assertThat(stat.getReadingsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Transición a FINISHED y FAULTED es definitiva: lecturas READING posteriores no reabren la sesión")
    void terminalStatesAreIrreversible() {
        StatsTelemetryReading r1 = new StatsTelemetryReading(
                "evt-1",
                StatsTelemetryReading.Type.READING,
                "sess-3",
                10L,
                T0,
                new BigDecimal("30.00"),
                new BigDecimal("5.000"),
                new BigDecimal("50.00"),
                null);
        SessionUsageStat stat = SessionUsageStat.fromFirstReading(r1, null);

        StatsTelemetryReading finish = new StatsTelemetryReading(
                "evt-2",
                StatsTelemetryReading.Type.FINISHED,
                "sess-3",
                10L,
                T0.plusSeconds(120),
                BigDecimal.ZERO,
                new BigDecimal("12.300"),
                new BigDecimal("80.00"),
                StatsTelemetryReading.EndReason.BATTERY_FULL);
        assertThat(stat.applyReading(finish)).isTrue();
        assertThat(stat.getStatus()).isEqualTo(UsageSessionStatus.FINISHED);
        assertThat(stat.getEndedAt()).isEqualTo(T0.plusSeconds(120));
        assertThat(stat.getEndReason()).isEqualTo(StatsTelemetryReading.EndReason.BATTERY_FULL);

        StatsTelemetryReading lateReading = new StatsTelemetryReading(
                "evt-3",
                StatsTelemetryReading.Type.READING,
                "sess-3",
                10L,
                T0.plusSeconds(180),
                new BigDecimal("30.00"),
                new BigDecimal("15.000"),
                new BigDecimal("82.00"),
                null);
        assertThat(stat.applyReading(lateReading)).isFalse();
        assertThat(stat.getStatus()).isEqualTo(UsageSessionStatus.FINISHED);

        // Creación directa desde FAULT
        StatsTelemetryReading fault = new StatsTelemetryReading(
                "evt-f",
                StatsTelemetryReading.Type.FAULT,
                "sess-fault",
                11L,
                T0,
                BigDecimal.ZERO,
                new BigDecimal("1.200"),
                null,
                null);
        SessionUsageStat faulted = SessionUsageStat.fromFirstReading(fault, null);
        assertThat(faulted.getStatus()).isEqualTo(UsageSessionStatus.FAULTED);
        assertThat(faulted.getEndReason()).isEqualTo(StatsTelemetryReading.EndReason.FAULT);
    }
}
