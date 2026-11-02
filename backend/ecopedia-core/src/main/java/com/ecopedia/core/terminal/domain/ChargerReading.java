package com.ecopedia.core.terminal.domain;

import java.math.BigDecimal;

/** Cómo va —o cómo terminó— una sesión, según el cargador. {@code energyKwh} es el acumulado. */
public record ChargerReading(String sessionId, BigDecimal powerKw, BigDecimal energyKwh, BigDecimal batteryPercent) {}
