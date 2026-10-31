package com.ecopedia.core.pricing.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tarifa de hora pico: el kWh al precio de red por el recargo de pico, ×1,2 (Manuel, 31/10).
 *
 * <p>Hay dos precios pico y no uno, porque el recargo se aplica sobre el precio de cada tipo de carga:
 * con la normal a $ 100, la pico normal es $ 120; con la rápida a $ 150, la pico rápida es $ 180.
 */
public class PeakPricingStrategy implements PricingStrategy {

    private final BigDecimal multiplier;

    public PeakPricingStrategy(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public BigDecimal energyCost(BigDecimal kwh, BigDecimal baseKwhRate) {
        return kwh.multiply(effectiveKwhRate(baseKwhRate)).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal effectiveKwhRate(BigDecimal baseKwhRate) {
        return baseKwhRate.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean isPeak() {
        return true;
    }
}
