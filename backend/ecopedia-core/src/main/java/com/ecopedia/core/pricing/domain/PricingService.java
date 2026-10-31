package com.ecopedia.core.pricing.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Interfaz pública del componente <i>Tarificación</i> (stateless, RF06 y RF14).
 *
 * <p><b>El modelo, desde el 31/10</b> (decidido por Manuel):
 *
 * <ul>
 *   <li>Los <b>precios de la carga</b> son de toda la red y los fija el {@code ADMIN}: carga normal, carga
 *       rápida y la multa por minuto de exceso ({@link NetworkRates}).
 *   <li>La <b>seña</b> y si se cobra <b>hora pico</b> son de cada estación, y los fija su {@code CPO}
 *       ({@link StationPricing}).
 *   <li>La <b>estrategia</b> no se guarda: la elige el sistema según cuándo empieza la carga.
 * </ul>
 *
 * <p>Antes cada conector tenía su esquema con una estrategia elegida a mano; eso se descartó.
 */
public interface PricingService {

    /** Los precios de la red. */
    NetworkRates getNetworkRates();

    /** Cambia los precios de la red. Solo el {@code ADMIN}: eso lo controla la capa web. */
    NetworkRates updateNetworkRates(BigDecimal normalKwhRate, BigDecimal fastKwhRate, BigDecimal excessPenaltyPerMin);

    /**
     * La configuración de cobro de una estación, o la de omisión si nadie la configuró.
     *
     * @throws IllegalArgumentException si la estación no existe
     */
    StationPricing getStationPricing(Long stationId);

    /**
     * Cambia la seña y la hora pico de una estación.
     *
     * @throws IllegalArgumentException si la estación no existe
     */
    StationPricing updateStationPricing(Long stationId, BigDecimal depositAmount, boolean peakPricingEnabled);

    /**
     * La seña para reservar un conector: la de su estación (RF06). La consulta el checkout para cobrarla.
     *
     * @throws IllegalArgumentException si el conector no existe
     */
    BigDecimal calculateDeposit(Long connectorId);

    /**
     * Lo que costaría cargar esa energía en ese conector empezando en ese instante (RF06), a la potencia
     * máxima del conector y sin multa.
     *
     * @throws IllegalArgumentException si el conector no existe, o si el estimado no supera la seña
     */
    ChargeBreakdown estimateCost(Long connectorId, BigDecimal estimatedKwh, Instant start);

    /**
     * Lo que cuesta una carga terminada (RF14). Lo va a llamar SesionesDeCarga al cerrar (ECO-55).
     *
     * @param deliveredPowerKw la potencia a la que se cargó de verdad, que decide si se cobra como normal
     *     o rápida: una rápida que la distribuidora bajó a 22 kW se cobra normal (ECO-52)
     * @param start cuándo empezó: decide si es hora pico
     * @param excessMinutes cuánto se quedó después de su turno; 0 si no se pasó
     */
    ChargeBreakdown calculateRealCost(
            Long connectorId, BigDecimal kwhConsumed, BigDecimal deliveredPowerKw, Instant start, long excessMinutes);
}
