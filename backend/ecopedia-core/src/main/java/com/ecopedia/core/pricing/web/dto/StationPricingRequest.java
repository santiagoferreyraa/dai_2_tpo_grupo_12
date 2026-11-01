package com.ecopedia.core.pricing.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** La seña y la hora pico de una estación, como las manda su CPO o el ADMIN. En pesos. */
public record StationPricingRequest(
        @NotNull(message = "La seña es obligatoria") @PositiveOrZero(message = "La seña no puede ser negativa")
                BigDecimal depositAmount,
        @NotNull(message = "Hay que indicar si se cobra hora pico") Boolean peakPricingEnabled) {}
