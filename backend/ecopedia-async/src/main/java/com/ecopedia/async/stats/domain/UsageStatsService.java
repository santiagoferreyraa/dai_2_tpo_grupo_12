package com.ecopedia.async.stats.domain;

import java.time.Instant;

/** Caso de uso del componente Estadísticas (ECO-301). */
public interface UsageStatsService {

    /**
     * Registra o actualiza la proyección de uso a partir de una lectura del tópico.
     *
     * @return {@code true} si aplicó cambios.
     */
    boolean recordTelemetry(StatsTelemetryReading reading);

    /** Devuelve el tablero de uso consolidado. Si {@code ownerId != null}, filtra solo sus estaciones. */
    UsageSummary getUsageSummary(Long ownerId, Instant from, Instant to);
}
