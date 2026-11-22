package com.ecopedia.core.pricing.web.dto;

import com.ecopedia.core.pricing.domain.ChargeBreakdown;
import com.ecopedia.core.pricing.domain.ChargeSpeed;
import java.math.BigDecimal;

/**
 * Desglose del costo real de una sesión de carga finalizada (RF14, ECO-55).
 */
public record ChargeBreakdownResponse(
        BigDecimal energyKwh,
        BigDecimal appliedKwhRate,
        BigDecimal baseEnergyCost,
        BigDecimal discountAmount,
        long excessMinutes,
        BigDecimal excessPenaltyAmount,
        BigDecimal totalCost,
        String strategyDescription,
        boolean fastTierApplied,
        boolean peakApplied) {

    public static ChargeBreakdownResponse from(BigDecimal kwhConsumed, long excessMinutes, ChargeBreakdown breakdown) {
        boolean fast = breakdown.speed() == ChargeSpeed.FAST;
        String description = (fast ? "Carga rápida" : "Carga normal") + (breakdown.peak() ? " (hora pico)" : "");
        return new ChargeBreakdownResponse(
                kwhConsumed,
                breakdown.kwhRate(),
                breakdown.energyCost(),
                BigDecimal.ZERO,
                excessMinutes,
                breakdown.penalty(),
                breakdown.total(),
                description,
                fast,
                breakdown.peak());
    }
}
