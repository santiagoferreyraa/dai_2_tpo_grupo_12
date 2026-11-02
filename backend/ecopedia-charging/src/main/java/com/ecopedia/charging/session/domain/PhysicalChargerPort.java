package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

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
     * Envía la orden de corte físico al cargador.
     *
     * @param connectorId identificador del conector físico
     * @throws PhysicalChargerUnavailableException si el cargador físico no responde
     */
    void deactivate(long connectorId);
}
