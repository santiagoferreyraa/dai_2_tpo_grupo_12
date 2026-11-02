package com.ecopedia.core.pricing.web.dto;

import com.ecopedia.core.pricing.domain.NetworkRates;
import com.ecopedia.core.pricing.domain.PricingRules;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Los precios de la red y las reglas con las que se aplican.
 *
 * <p>Las reglas viajan aunque no se editen desde la pantalla: así /admin las muestra con los valores que
 * de verdad están configurados, en vez de repetir "18 a 22" o "×1,2" escritos en el front.
 */
public record NetworkRatesResponse(
        BigDecimal normalKwhRate,
        BigDecimal fastKwhRate,
        BigDecimal excessPenaltyPerMin,
        BigDecimal fastChargeThresholdKw,
        int peakStartHour,
        int peakEndHour,
        BigDecimal peakMultiplier,
        int penaltyEscalationAfterMinutes,
        BigDecimal penaltyEscalationMultiplier,
        Instant updatedAt) {

    public static NetworkRatesResponse from(NetworkRates rates, PricingRules rules) {
        return new NetworkRatesResponse(
                rates.getNormalKwhRate(),
                rates.getFastKwhRate(),
                rates.getExcessPenaltyPerMin(),
                rules.fastChargeThresholdKw(),
                rules.peakStartHour(),
                rules.peakEndHour(),
                rules.peakMultiplier(),
                rules.penaltyEscalationAfterMinutes(),
                rules.penaltyEscalationMultiplier(),
                rates.getUpdatedAt());
    }
}
