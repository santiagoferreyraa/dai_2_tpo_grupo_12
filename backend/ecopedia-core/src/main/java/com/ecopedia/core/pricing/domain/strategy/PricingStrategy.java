package com.ecopedia.core.pricing.domain.strategy;

import java.math.BigDecimal;

/**
 * Patrón <b>Strategy</b>: cómo se cobra la energía de una carga (RF06, ECO-30).
 *
 * <p><b>Desde el 31/10 la estrategia no se elige: la elige la situación.</b> Antes cada conector guardaba
 * una estrategia elegida a mano en un desplegable, y el servicio la resolvía con un {@code switch}. Manuel
 * lo descartó: ahora {@link PricingStrategySelector} decide según cuándo empieza la carga y si la estación
 * cobra hora pico, y el servicio usa la que le toca sin saber cuál es. Cada estrategia sabe una sola cosa,
 * cómo cobrar el kWh; cuál aplicar no es asunto de ninguna.
 *
 * <p>La multa por exceso no es una estrategia: se suma igual con tarifa plana o pico. Ver
 * {@link OccupancyPenalty}.
 */
public interface PricingStrategy {

    /**
     * Lo que cuesta la energía.
     *
     * @param kwh lo cargado (o lo estimado)
     * @param baseKwhRate el precio de red del kWh para ese tipo de carga, normal o rápida
     */
    BigDecimal energyCost(BigDecimal kwh, BigDecimal baseKwhRate);

    /** El precio que se le aplica al kWh: el que se muestra al conductor. */
    BigDecimal effectiveKwhRate(BigDecimal baseKwhRate);

    /** Si es la tarifa de hora pico. Para la respuesta, no para decidir. */
    boolean isPeak();
}
