package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

/**
 * Lo que dice el cargador al apagarlo: cuánta energía entregó en la sesión y en qué porcentaje quedó
 * la batería (ECO-44).
 *
 * @param energyKwh acumulado desde que arrancó la sesión
 * @param batteryPercent puede ser {@code null} si el cargador no lo sabe
 */
public record ChargerReading(BigDecimal energyKwh, BigDecimal batteryPercent) {}
