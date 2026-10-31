package com.ecopedia.core.pricing.web.dto;

import java.math.BigDecimal;

/**
 * La seña de un conector, sola (RF06, ECO-53).
 *
 * <p>Es lo que necesitan el checkout y la pantalla de reservar, y por eso tiene su propia respuesta
 * y no viaja dentro de la estimación: ahí la seña venía con un consumo inventado, y la estimación
 * rechaza con 400 un consumo que no supera la seña, así que una seña alta se perdía.
 */
public record DepositResponse(Long connectorId, BigDecimal depositAmount) {}
