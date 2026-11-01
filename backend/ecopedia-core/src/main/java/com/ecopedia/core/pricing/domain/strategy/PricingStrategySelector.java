package com.ecopedia.core.pricing.domain.strategy;

import com.ecopedia.core.pricing.domain.PricingRules;
import com.ecopedia.core.pricing.domain.StationPricing;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Elige la estrategia según el caso (Manuel, 31/10): <b>hora pico si la carga empieza dentro de la franja
 * y la estación cobra hora pico; tarifa plana en cualquier otro caso.</b>
 *
 * <p><b>Decide la hora de inicio, y nada más.</b> Una carga que empieza a las 17:30 y sigue hasta las 18:30
 * se cobra entera a tarifa plana; una que empieza a las 21:30 y termina a las 22:30, entera a tarifa pico.
 * No se prorratea: el conductor sabe cuánto va a pagar el kWh en el momento de enchufar. La hora se mide
 * en la zona de {@link PricingRules#zone()}, no en la del servidor.
 *
 * <p>Las dos estrategias no tienen estado, así que se crean una vez y se reusan.
 */
public class PricingStrategySelector {

    private final PricingRules rules;
    private final PricingStrategy flat = new FlatRatePricingStrategy();
    private final PricingStrategy peak;

    public PricingStrategySelector(PricingRules rules) {
        this.rules = rules;
        this.peak = new PeakPricingStrategy(rules.peakMultiplier());
    }

    public PricingStrategy select(Instant chargeStart, StationPricing station) {
        return station.isPeakPricingEnabled() && isPeakHour(chargeStart) ? peak : flat;
    }

    /** Si ese instante cae en la franja pico, {@code [desde, hasta)}, en la hora del conductor. */
    public boolean isPeakHour(Instant instant) {
        LocalTime local = instant.atZone(rules.zone()).toLocalTime();
        LocalTime from = LocalTime.of(rules.peakStartHour(), 0);
        boolean beforeEnd = rules.peakEndHour() == 24 || local.isBefore(LocalTime.of(rules.peakEndHour(), 0));
        return !local.isBefore(from) && beforeEnd;
    }
}
