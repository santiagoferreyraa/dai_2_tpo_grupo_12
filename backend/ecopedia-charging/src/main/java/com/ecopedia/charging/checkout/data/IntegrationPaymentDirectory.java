package com.ecopedia.charging.checkout.data;

import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adaptador de {@link PaymentDirectory} que consulta el componente de Pagos en {@code ecopedia-integration}.
 */
@Component
public class IntegrationPaymentDirectory implements PaymentDirectory {

    private static final Logger log = LoggerFactory.getLogger(IntegrationPaymentDirectory.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient publicRestClient;
    private final RestClient internalRestClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public IntegrationPaymentDirectory(
            RestClient.Builder builder,
            @Value("${ecopedia.integration.url:http://localhost:8083}") String integrationUrl,
            @Qualifier("integrationInternalHttpClient") RestClient internalRestClient,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.publicRestClient =
                builder.baseUrl(integrationUrl).requestFactory(requestFactory).build();
        this.internalRestClient = internalRestClient;
        this.objectMapper = objectMapper;
    }

    /** Constructor para entornos de prueba o compatibilidad cuando no se inyecta el cliente interno. */
    public IntegrationPaymentDirectory(RestClient.Builder builder, String integrationUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.publicRestClient =
                builder.baseUrl(integrationUrl).requestFactory(requestFactory).build();
        this.internalRestClient = this.publicRestClient;
        this.objectMapper = new ObjectMapper();
    }

    public IntegrationPaymentDirectory(RestClient publicRestClient, RestClient internalRestClient) {
        this(publicRestClient, internalRestClient, new ObjectMapper());
    }

    public IntegrationPaymentDirectory(
            RestClient publicRestClient, RestClient internalRestClient, ObjectMapper objectMapper) {
        this.publicRestClient = publicRestClient;
        this.internalRestClient = internalRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean hasUsablePaymentMethod(Long driverId, String authToken) {
        if (authToken == null || authToken.isBlank()) {
            return false;
        }

        try {
            List<PaymentMethodSummary> cards = publicRestClient
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

    @Override
    public void validatePaymentMethodForCharging(Long driverId, Long paymentMethodId) {
        try {
            internalRestClient
                    .post()
                    .uri("/internal/payments/validations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InternalValidationRequest(driverId, paymentMethodId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 422) {
                String code = extractCode(ex);
                if ("DRIVER_HAS_DEBT".equals(code)) {
                    throw new PaymentMethodInvalidException("El conductor arrastra deuda pendiente de regularizar");
                } else if ("CARD_DECLINED".equals(code)) {
                    throw new PaymentMethodInvalidException("La tarjeta fue rechazada por la pasarela de pagos");
                } else if ("CARD_EXPIRED".equals(code)) {
                    throw new PaymentMethodInvalidException("La tarjeta registrada se encuentra vencida");
                } else {
                    throw new PaymentMethodInvalidException("No se encontró un medio de pago válido para operar");
                }
            }
            log.warn(
                    "Falla al validar tarjeta en integration para el conductor {}: HTTP {}",
                    driverId,
                    ex.getStatusCode());
            throw new PaymentMethodsUnavailableException(ex);
        } catch (RestClientException ex) {
            log.warn(
                    "No se pudo conectar con integration para validar tarjeta del conductor {}: {}",
                    driverId,
                    ex.getMessage());
            throw new PaymentMethodsUnavailableException(ex);
        }
    }

    private String extractCode(RestClientResponseException ex) {
        try {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("code")) {
                    return root.get("code").asText();
                }
            }
        } catch (JsonProcessingException unreadable) {
            // Un cuerpo que no es JSON: no hay código que leer.
        }
        return "UNKNOWN";
    }

    private record PaymentMethodSummary(Long id, String brand, String lastFourDigits, boolean isDefault) {}

    private record InternalValidationRequest(Long driverId, Long paymentMethodId) {}
}
