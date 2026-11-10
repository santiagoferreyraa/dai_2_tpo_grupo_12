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
     * Envía la orden de corte físico al cargador y devuelve cómo terminó la sesión (ECO-44).
     *
     * <p>Apagar dos veces es lo mismo que una: si el conector ya no estaba cargando, devuelve vacío.
     *
     * @param connectorId identificador del conector físico
     * @return lo entregado en la sesión, o vacío si el conector no estaba cargando
     * @throws PhysicalChargerUnavailableException si el cargador físico no responde
     */
    Optional<ChargerReading> deactivate(long connectorId);
}
