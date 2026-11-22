package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Puerto hacia el sistema externo del cargador físico (RNF06, ECO-60).
 *
 * <p>Las operaciones contra este puerto se ejecutan fuera de la transacción local de base de datos
 * y no participan del rollback: si la llamada falla, la consistencia se garantiza por compensación.
 */
public interface PhysicalChargerPort {

    /**
     * Envía la orden física de activación al cargador.
     *
     * @param connectorId identificador del conector físico
     * @param sessionId identificador único de la sesión de carga
     * @param powerLimitKw potencia límite en kW autorizada
     * @throws PhysicalChargerRejectedException si el hardware rechaza la activación
     * @throws PhysicalChargerUnavailableException si el cargador físico no responde
     */
    void activate(long connectorId, String sessionId, BigDecimal powerLimitKw);

    /**
     * Envía la orden de corte físico al cargador y devuelve los kWh entregados si el cargador los informó.
     *
     * @param connectorId identificador del conector físico
     * @return energía entregada en kWh si estaba presente en la respuesta; vacío si devolvió 204
     * @throws PhysicalChargerUnavailableException si el cargador físico no responde
     */
    Optional<BigDecimal> deactivate(long connectorId);
}
