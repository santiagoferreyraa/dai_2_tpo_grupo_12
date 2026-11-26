package com.ecopedia.async.stats.domain;

/** Metadatos de estación y propietario obtenidos desde {@code ecopedia-core} para un conector. */
public record StationOwnershipInfo(Long stationId, String stationName, Long ownerId, String connectorType) {}
