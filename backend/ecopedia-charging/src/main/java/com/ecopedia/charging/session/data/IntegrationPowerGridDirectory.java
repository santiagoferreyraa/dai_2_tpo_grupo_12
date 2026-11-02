package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.GridFeasibility;
import com.ecopedia.charging.session.domain.GridRequestRejectedException;
import com.ecopedia.charging.session.domain.GridUnavailableException;
import com.ecopedia.charging.session.domain.PowerGridDirectory;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link PowerGridDirectory} contra RedEléctrica, por su ruta interna en ecopedia-integration.
 *
 * <p>Un 422 es un pedido que la distribuidora rechazó, con el motivo; cualquier otra cosa que no
 * sea una respuesta —503, la clave rechazada, integration caído— es "no disponible". Un 401 deja
 * ERROR en el log: es la clave de servicio mal configurada, y no se arregla sola.
 */
@Component
public class IntegrationPowerGridDirectory implements PowerGridDirectory {

    private static final Logger log = LoggerFactory.getLogger(IntegrationPowerGridDirectory.class);

    private final RestClient restClient;

    public IntegrationPowerGridDirectory(@Qualifier("integrationInternalHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public GridFeasibility checkFeasibility(String nodeId, BigDecimal requestedPowerKw) {
        try {
            return restClient
                    .post()
                    .uri("/internal/power-grid/feasibility")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new FeasibilityBody(nodeId, requestedPowerKw))
                    .retrieve()
                    .body(GridFeasibility.class);
        } catch (RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 422) {
                throw new GridRequestRejectedException(codeOf(response));
            }
            if (status == 401) {
                log.error("ecopedia-integration rechazó la clave de servicio: revisar ECOPEDIA_INTERNAL_TOKEN");
            }
            throw new GridUnavailableException("RedEléctrica contestó " + status, response);
        } catch (RestClientException noAnswer) {
            throw new GridUnavailableException("ecopedia-integration no contestó", noAnswer);
        }
    }

    private static String codeOf(RestClientResponseException response) {
        try {
            ErrorBody error = response.getResponseBodyAs(ErrorBody.class);
            if (error != null && error.code() != null) return error.code();
        } catch (RuntimeException unreadable) {
            // Un cuerpo que no es el esperado se trata como un rechazo sin código.
        }
        return "UNKNOWN";
    }

    record FeasibilityBody(String nodeId, BigDecimal requestedPowerKw) {}

    record ErrorBody(String code) {}
}
