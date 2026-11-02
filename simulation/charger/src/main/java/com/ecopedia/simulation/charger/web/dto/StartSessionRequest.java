package com.ecopedia.simulation.charger.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code POST /v1/connectors/{id}/session}: encender el conector para una sesión.
 *
 * <p>Solo {@code sessionId} y {@code powerLimitKw} son obligatorios. Los datos del auto son
 * opcionales: sin ellos, el simulador usa un auto típico y una batería al azar entre el 15 y el 40 %.
 */
public record StartSessionRequest(
        @NotBlank @Size(max = 64) String sessionId,
        @NotNull @Positive @DecimalMax("400") BigDecimal powerLimitKw,
        @Positive @DecimalMax("250") BigDecimal batteryCapacityKwh,
        @Positive @DecimalMax("400") BigDecimal vehicleMaxChargeKw,
        @Min(0) @Max(99) Integer initialBatteryPercent) {}
