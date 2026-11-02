package com.ecopedia.simulation.powergrid.domain;

import java.math.BigDecimal;

/**
 * Lo que contesta la distribuidora: si autoriza, hasta cuánto, y por qué no cuando no autoriza.
 *
 * @param reason {@code null} cuando autoriza.
 */
public record Feasibility(boolean feasible, BigDecimal maxAllowedPowerKw, Restriction reason) {

    /** Por qué no autoriza. Son los valores de {@code RestrictionReason} del XSD. */
    public enum Restriction {
        DEMAND_CAP_EXCEEDED,
        MAINTENANCE_WINDOW
    }
}
