package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Puerto hacia Tarificación (ecopedia-core): cuánto cuesta la energía entregada en una sesión (RF14,
 * ECO-44).
 */
public interface ConsumptionPricing {

    /**
     * @param start el inicio de la sesión, que es lo que decide si fue hora pico
     * @throws ConsumptionPricingUnavailableException si core no contestó o contestó otra cosa
     */
    BigDecimal costOf(Long connectorId, BigDecimal kwh, Instant start);
}
