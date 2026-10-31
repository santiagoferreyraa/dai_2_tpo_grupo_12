package com.ecopedia.core.pricing.domain.strategy;

import com.ecopedia.core.pricing.domain.PricingRules;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * La multa por quedarse ocupando el conector después del turno (RF14).
 *
 * <p><b>No es una estrategia, es una regla que se suma</b> (Manuel, 31/10): con tarifa plana o pico, un
 * conductor que se pasa paga la multa igual. Antes era la estrategia "penalización por ocupación", que
 * obligaba a elegir entre cobrar pico o cobrar la multa; ahora pico y exceso se aplican juntos.
 *
 * <p>Por minuto de exceso, al precio que fija el {@code ADMIN}. Pasados
 * {@link PricingRules#penaltyEscalationAfterMinutes()} minutos (15), <b>todos</b> los minutos pasan a
 * costar ×1,5: el escalón no es solo para los minutos de más.
 */
public class OccupancyPenalty {

    private final PricingRules rules;

    public OccupancyPenalty(PricingRules rules) {
        this.rules = rules;
    }

    public BigDecimal of(long excessMinutes, BigDecimal penaltyPerMinute) {
        if (excessMinutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rate = excessMinutes > rules.penaltyEscalationAfterMinutes()
                ? penaltyPerMinute.multiply(rules.penaltyEscalationMultiplier())
                : penaltyPerMinute;
        return rate.multiply(BigDecimal.valueOf(excessMinutes)).setScale(2, RoundingMode.HALF_UP);
    }
}
