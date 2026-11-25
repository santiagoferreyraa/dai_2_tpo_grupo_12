package com.ecopedia.core.pricing.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Los precios de la red, como los manda el ADMIN desde /admin → Tarifas. En pesos.
 *
 * <p>Los topes son lo que entra en las columnas ({@link PricingLimits}), como con la seña.
 */
public record NetworkRatesRequest(
        @NotNull(message = "El precio de la carga normal es obligatorio")
                @Positive(message = "El precio de la carga normal tiene que ser mayor a cero")
                @DecimalMax(
                        value = PricingLimits.MAX_AMOUNT,
                        message = "El precio de la carga normal no puede superar $ 99.999.999,99")
                BigDecimal normalKwhRate,
        @NotNull(message = "El precio de la carga rápida es obligatorio")
                @Positive(message = "El precio de la carga rápida tiene que ser mayor a cero")
                @DecimalMax(
                        value = PricingLimits.MAX_AMOUNT,
                        message = "El precio de la carga rápida no puede superar $ 99.999.999,99")
                BigDecimal fastKwhRate,
        @NotNull(message = "La multa por minuto es obligatoria")
                @PositiveOrZero(message = "La multa por minuto no puede ser negativa")
                @DecimalMax(
                        value = PricingLimits.MAX_AMOUNT,
                        message = "La multa por minuto no puede superar $ 99.999.999,99")
                BigDecimal excessPenaltyPerMin) {}
