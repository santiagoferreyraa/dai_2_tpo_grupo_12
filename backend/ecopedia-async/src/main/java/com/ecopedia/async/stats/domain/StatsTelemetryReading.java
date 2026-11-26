package com.ecopedia.async.stats.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lectura de telemetría recibida del tópico {@code telemetry.charging} ({@code _type =
 * "telemetry-event"}).
 */
public record StatsTelemetryReading(
        String eventId,
        Type type,
        String sessionId,
        long connectorId,
        Instant timestamp,
        BigDecimal powerKw,
        BigDecimal energyKwh,
        BigDecimal batteryPercent,
        EndReason endReason) {

    public enum Type {
        READING,
        FINISHED,
        FAULT
    }

    public enum EndReason {
        STOPPED,
        BATTERY_FULL,
        FAULT
    }
}
