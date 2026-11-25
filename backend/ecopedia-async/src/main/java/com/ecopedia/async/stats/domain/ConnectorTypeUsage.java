package com.ecopedia.async.stats.domain;

import java.math.BigDecimal;

/** Métricas agregadas por tipo de conector para el tablero de uso (ECO-301). */
public record ConnectorTypeUsage(String connectorType, long totalSessions, BigDecimal totalEnergyKwh) {}
