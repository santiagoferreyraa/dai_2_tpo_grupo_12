package com.ecopedia.core.terminal.domain;

import java.math.BigDecimal;

/**
 * La orden de encender un conector.
 *
 * <p>{@code powerLimitKw} es lo que Ecopedia permite: la potencia del conector, o menos si la
 * distribuidora no autorizó la carga rápida (RF15). Los dos datos del auto salen de su modelo
 * ({@code battery_kwh} y {@code max_charge_kw}); son opcionales porque un conductor puede no haber
 * cargado su auto, y en ese caso el cargador usa uno típico.
 */
public record ChargingOrder(
        long connectorId,
        String sessionId,
        BigDecimal powerLimitKw,
        BigDecimal batteryCapacityKwh,
        BigDecimal vehicleMaxChargeKw) {}
