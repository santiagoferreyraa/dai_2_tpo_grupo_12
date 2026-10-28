package com.ecopedia.core.terminal.charger;

import com.ecopedia.core.terminal.domain.ChargerPort;
import com.ecopedia.core.terminal.domain.ChargerReading;
import com.ecopedia.core.terminal.domain.ChargerRejectedException;
import com.ecopedia.core.terminal.domain.ChargerRejectedException.Reason;
import com.ecopedia.core.terminal.domain.ChargerUnavailableException;
import com.ecopedia.core.terminal.domain.ChargingOrder;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implementación de {@link ChargerPort} contra el simulador de cargadores, por REST (RNF04).
 *
 * <p><b>El diseño lo llamaba {@code ChargerOcppAdapter}</b>, y se renombró: habla REST, no OCPP, y el
 * nombre tiene que decir contra qué está escrito, como {@code PaymentGatewayRestClient} y
 * {@code PowerGridWsClient}. OCPP sigue siendo el ejemplo de por qué existe el puerto: otro protocolo
 * sería otro adaptador.
 *
 * <p><b>Traduce los errores a dos formas.</b> Un 409 con su {@code code} es un "no" del cargador
 * —{@link ChargerRejectedException}, con el motivo—; todo lo que no sea una respuesta —caído, lento,
 * la clave rechazada, o un 503 porque no pudo publicar la telemetría— es
 * {@link ChargerUnavailableException}.
 */
@Component
public class ChargerRestClient implements ChargerPort {

    private static final Logger log = LoggerFactory.getLogger(ChargerRestClient.class);

    private final RestClient restClient;

    public ChargerRestClient(@Qualifier("chargerHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ChargerReading activate(ChargingOrder order) {
        try {
            ConnectorBody connector = restClient
                    .post()
                    .uri("/v1/connectors/{id}/session", order.connectorId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StartBody(
                            order.sessionId(),
                            order.powerLimitKw(),
                            order.batteryCapacityKwh(),
                            order.vehicleMaxChargeKw()))
                    .retrieve()
                    .body(ConnectorBody.class);
            return connector.session().toReading();
        } catch (RestClientResponseException rejected) {
            throw translate("encender", order.connectorId(), rejected);
        } catch (ResourceAccessException noAnswer) {
            throw unavailable("encender", order.connectorId(), noAnswer);
        }
    }

    @Override
    public Optional<ChargerReading> deactivate(long connectorId) {
        try {
            SessionBody session = restClient
                    .delete()
                    .uri("/v1/connectors/{id}/session", connectorId)
                    .retrieve()
                    .body(SessionBody.class);
            // 204: no estaba cargando, no hay cuerpo.
            return Optional.ofNullable(session).map(SessionBody::toReading);
        } catch (RestClientResponseException rejected) {
            throw translate("apagar", connectorId, rejected);
        } catch (ResourceAccessException noAnswer) {
            throw unavailable("apagar", connectorId, noAnswer);
        }
    }

    private static RuntimeException translate(
            String operation, long connectorId, RestClientResponseException response) {
        int status = response.getStatusCode().value();
        if (status == 409) {
            String code = codeOf(response);
            Reason reason = "connector_faulted".equals(code) ? Reason.CONNECTOR_FAULTED : Reason.CONNECTOR_BUSY;
            log.warn("El cargador no aceptó {} el conector {}: {}", operation, connectorId, code);
            return new ChargerRejectedException(reason, "El cargador rechazó la orden: " + code, response);
        }
        if (status == 400) {
            log.error(
                    "El cargador no entendió la orden de {} el conector {}: {}",
                    operation,
                    connectorId,
                    codeOf(response));
            return new ChargerRejectedException(
                    Reason.INVALID_REQUEST, "El cargador no entendió la orden: " + codeOf(response), response);
        }
        return unavailable(operation, connectorId, response);
    }

    /**
     * 401 o 403 quieren decir que la clave de acceso está mal configurada: para quien llama es lo
     * mismo que una caída, pero para el equipo es un ERROR en el log, porque no se arregla solo.
     */
    private static ChargerUnavailableException unavailable(String operation, long connectorId, Exception cause) {
        if (cause instanceof RestClientResponseException response
                && (response.getStatusCode().value() == 401
                        || response.getStatusCode().value() == 403)) {
            log.error(
                    "El cargador rechazó la clave de acceso al {} el conector {}: revisar ECOPEDIA_CHARGER_ACCESS_TOKEN",
                    operation,
                    connectorId);
        } else {
            log.warn("El cargador no contestó al {} el conector {}: {}", operation, connectorId, cause.getMessage());
        }
        return new ChargerUnavailableException("El cargador no está disponible", cause);
    }

    private static String codeOf(RestClientResponseException response) {
        try {
            ErrorBody error = response.getResponseBodyAs(ErrorBody.class);
            if (error != null && error.code() != null) return error.code();
        } catch (RuntimeException unreadable) {
            // Un cuerpo que no es el esperado se trata como un rechazo sin código.
        }
        return "unknown";
    }

    /* El contrato del cargador. Es de este adaptador y de nadie más. */

    record StartBody(
            String sessionId, BigDecimal powerLimitKw, BigDecimal batteryCapacityKwh, BigDecimal vehicleMaxChargeKw) {}

    record ConnectorBody(long connectorId, String status, SessionBody session) {}

    record SessionBody(String sessionId, BigDecimal powerKw, BigDecimal energyKwh, BigDecimal batteryPercent) {
        ChargerReading toReading() {
            return new ChargerReading(sessionId, powerKw, energyKwh, batteryPercent);
        }
    }

    record ErrorBody(int status, String code, String message) {}
}
