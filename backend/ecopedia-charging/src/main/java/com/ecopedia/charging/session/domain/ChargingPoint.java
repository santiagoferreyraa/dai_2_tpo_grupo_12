package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

/**
 * Lo que SesionesDeCarga necesita saber de un conector para decidir con qué potencia habilitarlo.
 *
 * @param maxPowerKw la potencia del conector, según Terminales.
 * @param nodeId el nodo de la distribuidora de su estación, o {@code null} si la estación no tiene:
 *     no es un error, es una estación que no ofrece carga rápida (decidido por Manuel el 28/10).
 */
public record ChargingPoint(long connectorId, long stationId, BigDecimal maxPowerKw, String nodeId) {}
