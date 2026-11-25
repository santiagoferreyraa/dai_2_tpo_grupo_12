package com.ecopedia.async.stats.domain;

import java.math.BigDecimal;

/** Métricas agregadas por estación para el tablero de uso (ECO-301). */
public record StationUsage(
        Long stationId,
        String stationName,
        long totalSessions,
        long activeSessions,
        long faultedSessions,
        BigDecimal totalEnergyKwh,
        BigDecimal peakPowerKw) {}
