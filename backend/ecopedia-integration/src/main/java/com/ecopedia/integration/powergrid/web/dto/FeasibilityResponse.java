package com.ecopedia.integration.powergrid.web.dto;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import java.math.BigDecimal;

/** La respuesta de la distribuidora, tal cual: {@code reason} es {@code null} cuando autoriza. */
public record FeasibilityResponse(boolean feasible, BigDecimal maxAllowedPowerKw, String reason) {

    public static FeasibilityResponse from(Feasibility feasibility) {
        return new FeasibilityResponse(
                feasibility.feasible(),
                feasibility.maxAllowedPowerKw(),
                feasibility.reason() == null ? null : feasibility.reason().name());
    }
}
