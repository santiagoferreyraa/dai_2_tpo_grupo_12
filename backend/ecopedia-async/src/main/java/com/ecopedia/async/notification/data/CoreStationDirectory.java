package com.ecopedia.async.notification.data;

import com.ecopedia.async.notification.domain.StationDirectory;
import com.ecopedia.async.notification.domain.StationInfo;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link StationDirectory} contra la API REST de {@code ecopedia-core}.
 *
 * <p>Son dos consultas, las dos públicas en core: {@code GET /api/connectors/{id}} dice de qué
 * estación es el conector y de qué tipo, y {@code GET /api/stations/{id}} dice cómo se llama y
 * dónde queda. No hace falta reenviar ningún token: son datos que cualquiera puede leer, y este
 * componente no atiende a un usuario sino a un mensaje.
 *
 * <p>Vive en la capa de datos por lo mismo que {@code CoreConnectorCatalog} en Reservas: para
 * Notificaciones, preguntarle a otro proceso por una estación es acceso a datos que no son suyos.
 */
@Component
public class CoreStationDirectory implements StationDirectory {

    private static final Logger log = LoggerFactory.getLogger(CoreStationDirectory.class);

    /*
     * Plazos cortos, como en Reservas. Acá no hay un conductor esperando la respuesta, pero sí un
     * mensaje ocupando el listener: con core colgado, sin plazo, cada aviso tardaría lo que tarde
     * el sistema operativo en rendirse.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public CoreStationDirectory(RestClient.Builder builder, @Value("${ecopedia.core.url}") String coreUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient =
                builder.baseUrl(coreUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Optional<StationInfo> findByConnector(Long connectorId) {
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

            return Optional.of(
                    new StationInfo(station.id(), station.name(), station.address(), connector.connectorType()));
        } catch (RestClientException failure) {
            /*
             * Incluye el 404 —un conector borrado después de la reserva— y core caído. En los dos
             * casos el aviso se escribe sin el nombre: ver el contrato de StationDirectory.
             */
            log.warn(
                    "No se pudo consultar a ecopedia-core la estación del conector {}: {}",
                    connectorId,
                    failure.getMessage());
            return Optional.empty();
        }
    }

    /** Lo que se usa de {@code ConnectorResponse} de core. El resto de los campos se ignora. */
    private record ConnectorView(Long id, Long stationId, String connectorType) {}

    /** Lo que se usa de {@code StationResponse} de core. */
    private record StationView(Long id, String name, String address) {}
}
