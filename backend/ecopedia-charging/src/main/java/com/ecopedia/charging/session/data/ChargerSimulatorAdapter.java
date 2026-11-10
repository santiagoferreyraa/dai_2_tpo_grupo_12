package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.ChargerReading;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ChargerSimulatorAdapter implements PhysicalChargerPort {

    private static final Logger log = LoggerFactory.getLogger(ChargerSimulatorAdapter.class);

    private final RestClient restClient;

    public ChargerSimulatorAdapter(@Qualifier("chargerHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void activate(long connectorId, String sessionId, BigDecimal powerLimitKw) {
        try {
            restClient
                    .post()
                    .uri("/v1/connectors/{id}/session", connectorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StartSessionBody(sessionId, powerLimitKw))
                    .retrieve()
                    .toBodilessEntity();
            log.info(
                    "Cargador físico activado en conector {} con sesión {} (límite {} kW)",
                    connectorId,
                    sessionId,
                    powerLimitKw);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "El cargador físico rechazó la activación en conector {}: status {}",
                    connectorId,
                    ex.getStatusCode());
            throw new PhysicalChargerRejectedException(
                    "El cargador rechazó la activación del conector "
                            + connectorId
                            + " (HTTP "
                            + ex.getStatusCode().value()
                            + ")",
                    ex);
        } catch (RestClientException ex) {
            log.warn("El cargador físico no está disponible en conector {}: {}", connectorId, ex.getMessage());
            throw new PhysicalChargerUnavailableException(
                    "El cargador físico no respondió al activar el conector " + connectorId, ex);
        }
    }

    @Override
    public Optional<ChargerReading> deactivate(long connectorId) {
        try {
            ResponseEntity<StopSessionBody> answer = restClient
                    .delete()
                    .uri("/v1/connectors/{id}/session", connectorId)
                    .retrieve()
                    .toEntity(StopSessionBody.class);
            log.info("Cargador físico desactivado en conector {} (HTTP {})", connectorId, answer.getStatusCode());
            // 204: el conector no estaba cargando. Apagar dos veces es lo mismo que una.
            StopSessionBody body = answer.getBody();
            if (body == null) {
                return Optional.empty();
            }
            return Optional.of(new ChargerReading(body.energyKwh(), body.batteryPercent()));
        } catch (RestClientException ex) {
            log.warn("Error al desactivar el cargador físico en conector {}: {}", connectorId, ex.getMessage());
            throw new PhysicalChargerUnavailableException("No se pudo desactivar el cargador físico", ex);
        }
    }

    record StartSessionBody(String sessionId, BigDecimal powerLimitKw) {}

    /** Lo que contesta el simulador al apagar: {@code SessionView}, de la que solo interesan dos campos. */
    record StopSessionBody(String sessionId, BigDecimal energyKwh, BigDecimal batteryPercent) {}
}
