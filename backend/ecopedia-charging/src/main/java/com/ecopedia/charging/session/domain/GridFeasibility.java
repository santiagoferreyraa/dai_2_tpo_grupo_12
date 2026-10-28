package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

/**
 * Lo que contestó la distribuidora. El motivo viaja como texto, como el estado del conector en
 * {@code ConnectorSnapshot}: si la distribuidora suma uno, este artefacto no se cae al leerlo.
 *
 * @param maxAllowedPowerKw el tope que el nodo acepta ahora, aunque diga que no.
 */
public record GridFeasibility(boolean feasible, BigDecimal maxAllowedPowerKw, String reason) {}
