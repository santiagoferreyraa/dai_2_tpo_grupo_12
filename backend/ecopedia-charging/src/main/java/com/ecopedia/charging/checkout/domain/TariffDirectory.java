package com.ecopedia.charging.checkout.domain;

import java.math.BigDecimal;

/**
 * Puerto secundario hacia el componente de Tarificación (ecopedia-core).
 *
 * <p>Permite al Facade de Checkout obtener el importe de la seña requerida para un conector
 * según la política tarifaria vigente (RF06).
 */
public interface TariffDirectory {

    /**
     * Calcula la seña aplicable para una reserva en el conector indicado.
     *
     * @param connectorId Identificador del conector
     * @return Monto de la seña (depósito previo a la carga)
     */
    BigDecimal calculateDeposit(Long connectorId);
}
