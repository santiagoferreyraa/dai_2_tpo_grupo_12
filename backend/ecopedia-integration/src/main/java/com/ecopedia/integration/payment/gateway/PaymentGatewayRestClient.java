package com.ecopedia.integration.payment.gateway;

import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.CardValidation;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.GatewayRefund;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implementación de {@link PaymentGatewayPort} contra la pasarela simulada, por REST/JSON (RNF04).
 *
 * <p><b>Es el Adapter de §4 en código.</b> Traduce entre el contrato de Pagos y el de la pasarela,
 * que imita el de Mercado Pago: {@code card_tokens}, {@code payments} y {@code refunds}, en
 * snake_case, con la clave de idempotencia en la cabecera. Reemplazó a {@code LocalPaymentGateway},
 * que validaba y tokenizaba sin salir del contenedor, y {@code PaymentServiceImpl} no se enteró.
 *
 * <p><b>Traduce también los errores.</b> La pasarela contesta con un {@code code} estable y un
 * mensaje en inglés pensado para el log; al conductor le llega una frase en castellano que dice qué
 * hacer, elegida acá según el código (directriz 7). Todo lo que no sea una respuesta —la pasarela
 * caída, lenta o rechazando la clave de acceso— es {@link PaymentGatewayUnavailableException}.
 */
@Component
public class PaymentGatewayRestClient implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayRestClient.class);

    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    /** Lo que se le dice al conductor según el código con que la pasarela rechazó la tarjeta. */
    private static final Map<String, String> TOKENIZE_REJECTIONS = Map.of(
            "invalid_card_number", "El número de tarjeta no es válido. Revisá que esté completo y tenga solo dígitos",
            "invalid_check_digit", "El número de tarjeta no es válido. Revisá que esté bien copiado",
            "invalid_expiration_month", "El mes de vencimiento tiene que estar entre 1 y 12",
            "card_expired", "La tarjeta está vencida",
            "invalid_cardholder_name", "Falta el nombre del titular como figura en la tarjeta",
            "not_a_test_card",
                    "Por ser un entorno de prueba, solo se aceptan tarjetas de prueba. Probá con la 4242 4242 4242 4242");

    private static final String UNKNOWN_REJECTION = "La tarjeta fue rechazada. Revisá los datos o probá con otra";

    private final RestClient restClient;

    public PaymentGatewayRestClient(@Qualifier("paymentGatewayHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public TokenizedCard tokenize(CardData card) {
        TokenizeBody body = new TokenizeBody(
                card.number(), card.expiryMonth(), card.expiryYear(), new Cardholder(card.holderName()));
        try {
            CardTokenBody token = restClient
                    .post()
                    .uri("/v1/card_tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(CardTokenBody.class);
            return new TokenizedCard(brandOf(token.paymentMethodId()), token.lastFourDigits(), token.id());
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().value() == 400) {
                String code = codeOf(rejected);
                log.info("La pasarela rechazó la tarjeta: {}", code);
                throw new IllegalArgumentException(TOKENIZE_REJECTIONS.getOrDefault(code, UNKNOWN_REJECTION));
            }
            throw unavailable("tokenizar", rejected);
        } catch (ResourceAccessException noAnswer) {
            throw unavailable("tokenizar", noAnswer);
        }
    }

    @Override
    public GatewayCharge charge(
            String gatewayToken, BigDecimal amount, String description, String reference, String idempotencyKey) {
        try {
            PaymentBody payment = restClient
                    .post()
                    .uri("/v1/payments")
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChargeBody(amount, gatewayToken, description, reference))
                    .retrieve()
                    .body(PaymentBody.class);
            return new GatewayCharge(payment.id(), "approved".equals(payment.status()), payment.statusDetail());
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().is4xxClientError() && !isAuthProblem(rejected.getStatusCode())) {
                // Un token que la pasarela no conoce, un importe inválido: el cobro no se hizo.
                String code = codeOf(rejected);
                log.warn("La pasarela no aceptó el cobro {}: {}", idempotencyKey, code);
                return new GatewayCharge(null, false, code);
            }
            throw unavailable("cobrar", rejected);
        } catch (ResourceAccessException noAnswer) {
            throw unavailable("cobrar", noAnswer);
        }
    }

    @Override
    public GatewayRefund refund(String paymentId, BigDecimal amount, String idempotencyKey) {
        try {
            RefundBody refund = restClient
                    .post()
                    .uri("/v1/payments/{id}/refunds", paymentId)
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RefundRequestBody(amount))
                    .retrieve()
                    .body(RefundBody.class);
            return new GatewayRefund(refund.id(), refund.paymentId(), refund.amount());
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().is4xxClientError() && !isAuthProblem(rejected.getStatusCode())) {
                String code = codeOf(rejected);
                log.warn("La pasarela no aceptó el reintegro {} del cobro {}: {}", idempotencyKey, paymentId, code);
                throw new IllegalArgumentException("La pasarela no aceptó el reintegro: " + code);
            }
            throw unavailable("reintegrar", rejected);
        } catch (ResourceAccessException noAnswer) {
            throw unavailable("reintegrar", noAnswer);
        }
    }

    @Override
    public CardValidation validate(String gatewayToken) {
        try {
            ValidationBody validation = restClient
                    .post()
                    .uri("/v1/card_tokens/{id}/validations", gatewayToken)
                    .retrieve()
                    .body(ValidationBody.class);
            return new CardValidation("approved".equals(validation.status()), validation.statusDetail());
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().is4xxClientError() && !isAuthProblem(rejected.getStatusCode())) {
                // Un token que la pasarela ya no conoce no es cobrable, y eso es una respuesta.
                return new CardValidation(false, codeOf(rejected));
            }
            throw unavailable("validar", rejected);
        } catch (ResourceAccessException noAnswer) {
            throw unavailable("validar", noAnswer);
        }
    }

    /**
     * 401 o 403 de la pasarela quieren decir que la clave de acceso está mal configurada. Para el
     * conductor es lo mismo que una caída; para el equipo es un ERROR en el log, porque no se
     * arregla solo.
     */
    private static boolean isAuthProblem(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }

    private static PaymentGatewayUnavailableException unavailable(String operation, Exception cause) {
        if (cause instanceof RestClientResponseException response && isAuthProblem(response.getStatusCode())) {
            log.error(
                    "La pasarela rechazó la clave de acceso al {}: revisar ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN",
                    operation);
        } else {
            log.warn("La pasarela no contestó al {}: {}", operation, cause.getMessage());
        }
        return new PaymentGatewayUnavailableException(cause);
    }

    private static String codeOf(RestClientResponseException rejected) {
        try {
            ErrorBody error = rejected.getResponseBodyAs(ErrorBody.class);
            if (error != null && error.cause() != null && !error.cause().isEmpty()) {
                return error.cause().get(0).code();
            }
        } catch (RuntimeException unreadable) {
            // Un cuerpo que no es el esperado se trata como un rechazo sin código.
        }
        return "unknown";
    }

    private static CardBrand brandOf(String paymentMethodId) {
        return switch (paymentMethodId) {
            case "visa" -> CardBrand.VISA;
            case "master" -> CardBrand.MASTERCARD;
            case "amex" -> CardBrand.AMEX;
            default ->
                throw new IllegalArgumentException(
                        "Por ahora solo se aceptan tarjetas VISA, Mastercard y American Express");
        };
    }

    /* El contrato de la pasarela, en snake_case. Son de este adaptador y de nadie más. */

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TokenizeBody(String cardNumber, int expirationMonth, int expirationYear, Cardholder cardholder) {
        @Override
        public String toString() {
            return "TokenizeBody[cardNumber=****]";
        }
    }

    record Cardholder(String name) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CardTokenBody(String id, String paymentMethodId, String lastFourDigits) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ChargeBody(BigDecimal transactionAmount, String token, String description, String externalReference) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record PaymentBody(String id, String status, String statusDetail) {}

    record RefundRequestBody(BigDecimal amount) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RefundBody(String id, String paymentId, BigDecimal amount) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ValidationBody(String status, String statusDetail) {}

    record ErrorBody(int status, String message, List<Cause> cause) {}

    record Cause(String code, String description) {}
}
