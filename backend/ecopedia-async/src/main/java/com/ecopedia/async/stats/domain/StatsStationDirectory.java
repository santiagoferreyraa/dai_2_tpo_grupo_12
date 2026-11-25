package com.ecopedia.async.stats.domain;

import java.util.Optional;

/** Puerto de consulta a {@code ecopedia-core} para enriquecer estación y propietario de un conector. */
public interface StatsStationDirectory {

    Optional<StationOwnershipInfo> findByConnector(Long connectorId);
}
