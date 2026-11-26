package com.ecopedia.async.stats.domain;

import java.time.Instant;

/** Casos de uso del componente Estadísticas, {@code AnalyticsService} en la ficha 10 (ECO-301). */
public interface AnalyticsService {

    /**
     * Suma una lectura del tópico a su franja de 15 minutos.
     *
     * @return {@code true} si aplicó cambios; {@code false} si era repetida o atrasada.
     */
    boolean recordTelemetry(StatsTelemetryReading reading);

    /**
     * El tablero del rango {@code [from, to)}. Si {@code ownerId} no es {@code null}, solo las
     * estaciones de ese operador.
     */
    AnalyticsDashboard getDashboard(Long ownerId, Instant from, Instant to);
}
