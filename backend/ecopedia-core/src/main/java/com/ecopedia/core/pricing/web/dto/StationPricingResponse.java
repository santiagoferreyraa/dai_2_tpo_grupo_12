package com.ecopedia.core.pricing.web.dto;

import com.ecopedia.core.pricing.domain.StationPricing;
import java.math.BigDecimal;

/** La seña y la hora pico de una estación. */
public record StationPricingResponse(Long stationId, BigDecimal depositAmount, boolean peakPricingEnabled) {

    public static StationPricingResponse from(StationPricing pricing) {
        return new StationPricingResponse(
                pricing.getStationId(), pricing.getDepositAmount(), pricing.isPeakPricingEnabled());
    }
}
