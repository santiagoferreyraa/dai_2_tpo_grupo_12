package com.ecopedia.simulation.charger.service;

import java.math.BigDecimal;

/**
 * La orden de activar un conector.
 *
 * <p>{@code powerLimitKw} es lo que Ecopedia permite: la potencia del conector, o menos si la
 * distribuidora no autorizó la carga rápida. Lo demás describe al auto, que en la realidad lo
 * informaría él mismo al enchufarse; acá se puede fijar o dejar en {@code null}, y el simulador usa
 * un auto típico.
 */
public record ChargeOrder(
        String sessionId,
        BigDecimal powerLimitKw,
        BigDecimal batteryCapacityKwh,
        BigDecimal vehicleMaxChargeKw,
        Integer initialBatteryPercent) {}
