package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.AccessCodeDirectory;
import com.ecopedia.charging.session.domain.ChargingPointCatalogUnavailableException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link AccessCodeDirectory} contra Terminales: la ruta interna de core que dice qué conector tiene
 * un código (ECO-44). El código va en el cuerpo, no en la URL, para no quedar en ningún log de accesos.
 */
@Component
public class CoreAccessCodeDirectory implements AccessCodeDirectory {

    private final RestClient restClient;

    public CoreAccessCodeDirectory(@Qualifier("coreInternalHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<Long> findConnectorId(String code) {
        try {
            ConnectorBody connector = restClient
                    .post()
                    .uri("/internal/terminals/connectors/resolve-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResolveBody(code))
                    .retrieve()
                    .body(ConnectorBody.class);
            if (connector == null) {
                // Un 2xx sin cuerpo no es una respuesta válida: se trata igual que si no hubiera contestado.
                throw new ResourceAccessException("ecopedia-core contestó sin cuerpo");
            }
            return Optional.of(connector.id());
        } catch (HttpClientErrorException.NotFound unknownCode) {
            return Optional.empty();
        } catch (RestClientException noAnswer) {
            // Incluye un 401 por la clave de servicio: es una falla de configuración, no del conductor.
            throw new ChargingPointCatalogUnavailableException(noAnswer);
        }
    }

    record ResolveBody(String code) {}

    record ConnectorBody(long id) {}
}
