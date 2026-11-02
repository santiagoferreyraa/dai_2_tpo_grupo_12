package com.ecopedia.core.pricing.domain;

/**
 * Si una carga es normal o rápida, que es lo que decide el precio base del kWh.
 *
 * <p>Lo decide la potencia <b>entregada de verdad</b>, no la del conector (Manuel, 31/10): un conector
 * de 150 kW al que la distribuidora solo le autoriza 22 kW (ECO-52) carga a potencia normal y se cobra
 * como normal. El corte es el mismo de ECO-52: ver {@link PricingRules#fastChargeThresholdKw()}.
 */
public enum ChargeSpeed {
    NORMAL,
    FAST
}
