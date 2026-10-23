package com.ecopedia.charging.checkout.data;

import com.ecopedia.charging.checkout.domain.TariffDirectory;
import java.math.BigDecimal;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador de {@link TariffDirectory} que consulta el componente de Tarificación en {@code ecopedia-core}.
 */
@Component
public class CoreTariffDirectory implements TariffDirectory {

    private static final Logger log = LoggerFactory.getLogger(CoreTariffDirectory.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public CoreTariffDirectory(RestClient.Builder builder, @Value("${ecopedia.core.url}") String coreUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient =
                builder.baseUrl(coreUrl).requestFactory(requestFactory).build();
    }

    @Override
    public BigDecimal calculateDeposit(Long connectorId) {
        try {
            // Consulta el endpoint de estimación para obtener la seña fijada por el esquema tarifario
            EstimateResponse response = restClient
                    .get()
                    .uri("/api/pricing/estimate?connectorId={id}&estimatedKwh=10", connectorId)
                    .retrieve()
                    .body(EstimateResponse.class);

            if (response != null && response.deposit() != null) {
                return response.deposit();
            }
            return BigDecimal.ZERO;
        } catch (HttpClientErrorException.NotFound notFound) {
            log.warn("No se encontró esquema tarifario para el conector {}. Se asume seña 0", connectorId);
            return BigDecimal.ZERO;
        } catch (RestClientException ex) {
            log.warn(
                    "No se pudo consultar la seña a core para el conector {}: {}. Se asume seña 0",
                    connectorId,
                    ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private record EstimateResponse(
            Long connectorId, BigDecimal estimatedKwh, BigDecimal deposit, BigDecimal estimatedCost) {}
}
