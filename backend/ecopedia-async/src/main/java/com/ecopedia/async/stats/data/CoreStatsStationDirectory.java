package com.ecopedia.async.stats.data;

import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsStationDirectory;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link StatsStationDirectory} contra la API REST pública de {@code ecopedia-core} (ECO-301).
 *
 * <p>Usa plazos cortos ({@code 2s} de conexión, {@code 3s} de lectura) y degradación graciosa a
 * {@link Optional#empty()} si core no responde o el conector no existe, para que las lecturas de
 * telemetría no terminen en la DLQ por una caída temporal de core.
 */
@Component
public class CoreStatsStationDirectory implements StatsStationDirectory {

    private static final Logger log = LoggerFactory.getLogger(CoreStatsStationDirectory.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    @Autowired
    public CoreStatsStationDirectory(RestClient.Builder builder, @Value("${ecopedia.core.url}") String coreUrl) {
        this(configuredClient(builder, coreUrl));
    }

    public CoreStatsStationDirectory(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient configuredClient(RestClient.Builder builder, String coreUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return builder.baseUrl(coreUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Optional<StationOwnershipInfo> findByConnector(Long connectorId) {
        try {
            ConnectorView connector = restClient
                    .get()
                    .uri("/api/connectors/{id}", connectorId)
                    .retrieve()
                    .body(ConnectorView.class);
            if (connector == null || connector.stationId() == null) {
                return Optional.empty();
            }

            StationView station = restClient
                    .get()
                    .uri("/api/stations/{id}", connector.stationId())
                    .retrieve()
                    .body(StationView.class);
            if (station == null) {
                return Optional.empty();
            }

            return Optional.of(new StationOwnershipInfo(
                    station.id(), station.name(), station.ownerId(), connector.connectorType()));
        } catch (RestClientException failure) {
            log.warn(
                    "No se pudo consultar a ecopedia-core la estación del conector {}: {}",
                    connectorId,
                    failure.getMessage());
            return Optional.empty();
        }
    }

    private record ConnectorView(Long id, Long stationId, String connectorType) {}

    private record StationView(Long id, String name, Long ownerId) {}
}
