package com.ecopedia.charging.checkout.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Puerto secundario hacia el componente de Tarificación (ecopedia-core).
 *
 * <p>Permite al Facade de Checkout obtener el importe de la seña requerida para un conector
 * según la política tarifaria vigente (RF06), y a SesionesDeCarga calcular el costo real al
 * finalizar una sesión (RF14, ECO-55).
 */
public interface TariffDirectory {

    /**
     * Calcula la seña aplicable para una reserva en el conector indicado.
     *
     * @param connectorId Identificador del conector
     * @return Monto de la seña (depósito previo a la carga); 0 si el conector no tiene esquema tarifario
     * @throws PricingUnavailableException si Tarificación no contestó: no se sabe cuánto es
     */
    BigDecimal calculateDeposit(Long connectorId);

    /**
     * Calcula el costo real de una sesión de carga finalizada (RF14, ECO-55).
     *
     * @param connectorId Identificador del conector
     * @param kwhConsumed Energía total entregada en kWh
     * @param deliveredPowerKw Potencia efectiva de carga en kW
     * @param startedAt Instante de inicio de la sesión
     * @param excessMinutes Minutos de permanencia posteriores al fin de la reserva
     * @return Importe total calculado por Tarificación
     * @throws PricingUnavailableException si Tarificación no contestó o falló
     */
    BigDecimal calculateRealCost(
            Long connectorId,
            BigDecimal kwhConsumed,
            BigDecimal deliveredPowerKw,
            Instant startedAt,
            long excessMinutes);
}
