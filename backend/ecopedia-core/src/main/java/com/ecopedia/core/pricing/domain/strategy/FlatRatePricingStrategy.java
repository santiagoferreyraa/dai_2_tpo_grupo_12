package com.ecopedia.core.pricing.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Tarifa plana: el kWh al precio de red, sin recargo (ECO-30). */
public class FlatRatePricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal energyCost(BigDecimal kwh, BigDecimal baseKwhRate) {
        return kwh.multiply(effectiveKwhRate(baseKwhRate)).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal effectiveKwhRate(BigDecimal baseKwhRate) {
        return baseKwhRate.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean isPeak() {
        return false;
    }
}
