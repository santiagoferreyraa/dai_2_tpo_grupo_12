package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.ConsumptionPricing;
import com.ecopedia.charging.session.domain.ConsumptionPricingUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link ConsumptionPricing} contra Tarificación: la misma ruta pública que usa la pantalla de reservar
 * para estimar, {@code /api/pricing/estimate}, con los kWh que entregó de verdad el cargador y el
 * inicio real de la sesión (ECO-44). Así el precio que se cobra y el que se anunció salen de la
 * misma regla —tarifa normal o rápida, hora pico—.
 *
 * <p>La penalización por permanencia (RF06) no entra todavía: se devenga después de terminar la
 * carga, con el auto todavía enchufado, y la mide el corte automático (ECO-45).
 */
@Component
public class CoreConsumptionPricing implements ConsumptionPricing {

    private final RestClient restClient;

    public CoreConsumptionPricing(@Qualifier("coreTerminalHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public BigDecimal costOf(Long connectorId, BigDecimal kwh, Instant start) {
        if (kwh.signum() <= 0) {
            // Sin energía no hay nada que preguntar: core rechaza un estimado de 0 kWh.
            return BigDecimal.ZERO;
        }
        try {
            EstimateBody estimate = restClient
                    .get()
                    .uri(
                            "/api/pricing/estimate?connectorId={id}&estimatedKwh={kwh}&start={start}",
                            connectorId,
                            kwh.toPlainString(),
                            start.toString())
                    .retrieve()
                    .body(EstimateBody.class);
            if (estimate == null || estimate.estimatedCost() == null) {
                throw new ConsumptionPricingUnavailableException(
                        new IllegalStateException("Respuesta sin estimatedCost"));
            }
            return estimate.estimatedCost();
        } catch (RestClientException noAnswer) {
            throw new ConsumptionPricingUnavailableException(noAnswer);
        }
    }

    record EstimateBody(BigDecimal estimatedCost) {}
}
