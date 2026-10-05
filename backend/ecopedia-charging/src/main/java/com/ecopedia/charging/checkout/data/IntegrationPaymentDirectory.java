package com.ecopedia.charging.checkout.data;

import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador de {@link PaymentDirectory} que consulta el componente de Pagos en {@code ecopedia-integration}.
 */
@Component
public class IntegrationPaymentDirectory implements PaymentDirectory {

    private static final Logger log = LoggerFactory.getLogger(IntegrationPaymentDirectory.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public IntegrationPaymentDirectory(
            RestClient.Builder builder,
            @Value("${ecopedia.integration.url:http://localhost:8083}") String integrationUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient =
                builder.baseUrl(integrationUrl).requestFactory(requestFactory).build();
    }

    @Override
    public boolean hasUsablePaymentMethod(Long driverId, String authToken) {
        if (authToken == null || authToken.isBlank()) {
            return false;
        }

        try {
            List<PaymentMethodSummary> cards = restClient
                    .get()
                    .uri("/api/payment-methods")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PaymentMethodSummary>>() {});

            return cards != null && !cards.isEmpty();
        } catch (RestClientException ex) {
            /*
             * Sin respuesta de Pagos no se confirma: la tarjeta es obligatoria para toda transacción, y asumirla
             * dejaba reservar sin tarjeta con solo tener el 8083 apagado. Es el mismo criterio que con core caído
             * al retener: 503 y "probá de nuevo en un momento".
             */
            log.warn(
                    "No se pudo consultar medios de pago a integration para el conductor {}: {}",
                    driverId,
                    ex.getMessage());
            throw new PaymentMethodsUnavailableException(ex);
        }
    }

    private record PaymentMethodSummary(Long id, String brand, String lastFourDigits, boolean isDefault) {}
}
