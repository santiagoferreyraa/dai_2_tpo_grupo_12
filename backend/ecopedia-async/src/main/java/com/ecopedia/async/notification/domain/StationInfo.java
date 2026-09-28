package com.ecopedia.async.notification.domain;

/**
 * Lo que Notificaciones necesita saber de la estación de una reserva para escribir el aviso.
 *
 * @param connectorType el tipo de conector tal como lo nombra Terminales ({@code CCS2},
 *     {@code TYPE2}...). Va en el aviso porque una estación suele tener varios, y "tu reserva en
 *     Palermo" no dice a cuál ir.
 */
public record StationInfo(Long stationId, String name, String address, String connectorType) {}
