package com.ecopedia.core.pricing.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * La seña y la hora pico de una estación, como las manda su CPO o el ADMIN. En pesos.
 *
 * <p>El tope de la seña es lo que entra en la columna ({@code NUMERIC(10, 2)}): más que eso daba 500
 * al guardar (lo encontró Schemathesis, QA-25).
 */
public record StationPricingRequest(
        @NotNull(message = "La seña es obligatoria")
                @PositiveOrZero(message = "La seña no puede ser negativa")
                @DecimalMax(value = PricingLimits.MAX_AMOUNT, message = "La seña no puede superar $ 99.999.999,99")
                BigDecimal depositAmount,
        @NotNull(message = "Hay que indicar si se cobra hora pico") Boolean peakPricingEnabled) {}
