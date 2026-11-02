package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingPointCatalogUnavailableException;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link ChargingPointCatalog} contra Terminales: dos lecturas públicas de ecopedia-core, el conector
 * (su potencia y su estación) y la estación (su nodo).
 */
@Component
public class CoreChargingPointCatalog implements ChargingPointCatalog {

    private final RestClient restClient;

    public CoreChargingPointCatalog(@Qualifier("coreTerminalHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<ChargingPoint> find(long connectorId) {
        try {
            ConnectorBody connector = restClient
                    .get()
                    .uri("/api/connectors/{id}", connectorId)
                    .retrieve()
                    .body(ConnectorBody.class);
            StationBody station = restClient
                    .get()
                    .uri("/api/stations/{id}", connector.stationId())
                    .retrieve()
                    .body(StationBody.class);
            String nodeId = station.nodeId() == null || station.nodeId().isBlank() ? null : station.nodeId();
            return Optional.of(new ChargingPoint(connector.id(), station.id(), connector.maxPowerKw(), nodeId));
        } catch (HttpClientErrorException.NotFound missing) {
            return Optional.empty();
        } catch (RestClientException noAnswer) {
            throw new ChargingPointCatalogUnavailableException(noAnswer);
        }
    }

    record ConnectorBody(long id, long stationId, BigDecimal maxPowerKw) {}

    record StationBody(long id, String nodeId) {}
}
