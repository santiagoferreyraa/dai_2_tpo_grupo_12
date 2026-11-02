package com.ecopedia.core.pricing.domain;

import java.math.BigDecimal;

/**
 * Lo que cuesta una carga, desglosado: para estimarla antes y para cobrarla al cerrar (RF06, RF14).
 *
 * @param speed si se cobró como carga normal o rápida.
 * @param peak si se aplicó la tarifa de hora pico.
 * @param kwhRate el precio del kWh que se aplicó, con el recargo pico si corresponde.
 * @param energyCost lo que cuesta la energía.
 * @param penalty la multa por exceso; 0 si no hubo.
 * @param total la energía más la multa.
 */
public record ChargeBreakdown(
        ChargeSpeed speed,
        boolean peak,
        BigDecimal kwhRate,
        BigDecimal energyCost,
        BigDecimal penalty,
        BigDecimal total) {}
