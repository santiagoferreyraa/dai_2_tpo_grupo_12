package com.ecopedia.charging.session.domain;

import java.util.Optional;

/** Puerto hacia Terminales (ecopedia-core): la potencia de un conector y el nodo de su estación. */
public interface ChargingPointCatalog {

    /**
     * @return vacío si el conector o su estación no existen
     * @throws ChargingPointCatalogUnavailableException si core no contestó
     */
    Optional<ChargingPoint> find(long connectorId);
}
