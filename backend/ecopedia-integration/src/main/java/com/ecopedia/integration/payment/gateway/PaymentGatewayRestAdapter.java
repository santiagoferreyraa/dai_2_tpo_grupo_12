package com.ecopedia.integration.payment.gateway;

import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Patrón de Diseño GoF <b>Adapter</b> entre Pagos y la pasarela de pagos externa (ECO-56).
 *
 * <p><b>Los dos roles del patrón:</b>
 *
 * <ul>
 *   <li><b>Target</b>, lo que el cliente espera: {@link PaymentGatewayPort}, en el idioma de Ecopedia
 *       ({@link CardData}, {@link TokenizedCard}, {@link CardBrand}, rechazos como
 *       {@link IllegalArgumentException} con un mensaje para el conductor).
 *   <li><b>Adaptee</b>, lo que hay: la API REST de {@code pasarela-simulada}, que habla otro idioma.
 *   <li><b>Client</b>: {@code PaymentServiceImpl}, que solo conoce el puerto y no se enteró del cambio.
 * </ul>
 *
 * <p><b>Lo que se traduce</b>, que es la razón de ser de esta clase:
 *
 * <table>
 *   <caption>Ecopedia ⇄ pasarela</caption>
 *   <tr><th>Ecopedia</th><th>Pasarela</th></tr>
 *   <tr><td>{@code number} con espacios o guiones, como se copia</td><td>{@code card_number}, solo dígitos</td></tr>
 *   <tr><td>{@code expiryMonth}, {@code expiryYear}</td><td>{@code expiration_month}, {@code expiration_year}</td></tr>
 *   <tr><td>{@code holderName}</td><td>{@code cardholder.name}</td></tr>
 *   <tr><td>{@code CardBrand.MASTERCARD}</td><td>{@code payment_method_id: "master"}</td></tr>
 *   <tr><td>{@code IllegalArgumentException("La tarjeta está vencida")}</td>
 *       <td>402 {@code {"error":{"code":"expired_card"}}}</td></tr>
 *   <tr><td>{@link PaymentGatewayUnavailableException}</td><td>conexión rechazada, timeout, 5xx o 401</td></tr>
 * </table>
 *
 * <p><b>Por qué Adapter y no llamar a la pasarela desde el servicio.</b> Si {@code PaymentServiceImpl}
 * armara el JSON y leyera los códigos de error, el negocio de Pagos quedaría escrito en el idioma de
 * un proveedor: cambiar de pasarela sería reescribir el servicio y sus tests. Con el Adapter, el
 * proveedor es un detalle de esta clase. De hecho ya pasó una vez: antes de ECO-50 el puerto lo
 * implementaba {@code LocalPaymentGateway}, en memoria, y el reemplazo no tocó una línea del servicio.
 *
 * <p><b>Qué marcas acepta Ecopedia se decide acá</b>, no en la pasarela. La pasarela tokeniza
 * también Diners y Discover, como cualquier pasarela real; que Ecopedia no sepa cobrarlas es una
 * regla de este lado, y aparece como una traducción que no tiene destino.
 *
 * <p>Por ahora el puerto tiene una sola operación. Cuando entren la seña (ECO-53), la validación de
 * tarjeta (ECO-54) y el reintegro (ECO-59), se agregan al puerto y se implementan acá contra los
 * endpoints {@code /v1/payments} y {@code /v1/card_tokens/{id}/verify}, que la pasarela ya expone.
 */
@Component
public class PaymentGatewayRestAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayRestAdapter.class);

    /** Las redes que Ecopedia sabe cobrar, con el nombre que les da la pasarela. */
    private static final Map<String, CardBrand> ACCEPTED_NETWORKS =
            Map.of("visa", CardBrand.VISA, "master", CardBrand.MASTERCARD, "amex", CardBrand.AMEX);

    private static final String UNSUPPORTED_BRAND =
            "Por ahora solo se aceptan tarjetas VISA, Mastercard y American Express";

    /*
     * Los códigos de rechazo de la pasarela, en palabras que el conductor entiende y con algo que
     * pueda hacer al respecto. Un código que no está acá cae en el mensaje genérico: la pasarela
     * puede agregar motivos nuevos sin avisarnos, y eso no tiene que romper nada.
     */
    private static final Map<String, String> CARD_ERROR_MESSAGES = Map.of(
            "invalid_number", "El número de tarjeta tiene que tener entre 13 y 19 dígitos, sin letras",
            "incorrect_number", "El número de tarjeta no es válido. Revisá que esté bien copiado",
            "invalid_expiration_month", "El mes de vencimiento tiene que estar entre 1 y 12",
            "expired_card", "La tarjeta está vencida",
            "unsupported_network", UNSUPPORTED_BRAND,
            "cc_rejected_insufficient_amount", "La tarjeta no tiene fondos suficientes",
            "cc_rejected_other_reason", "El emisor rechazó la tarjeta. Probá con otra");

    private static final String GENERIC_CARD_ERROR = "La pasarela de pagos rechazó la tarjeta";

    private static final String UNAVAILABLE =
            "La pasarela de pagos no está respondiendo. Probá de nuevo en unos minutos";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaymentGatewayRestAdapter(
            @Qualifier("paymentGatewayRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public TokenizedCard tokenize(CardData card) {
        GatewayCardToken token;
        try {
            token = restClient
                    .post()
                    .uri("/v1/card_tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toGatewayRequest(card))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw translateError(response);
                    })
                    .body(GatewayCardToken.class);
        } catch (RestClientException failure) {
            // Conexión rechazada, timeout o una respuesta que no se pudo leer.
            log.warn("No se pudo tokenizar contra la pasarela: {}", failure.getMessage());
            throw new PaymentGatewayUnavailableException(UNAVAILABLE, failure);
        }

        if (token == null || token.id() == null) {
            throw new PaymentGatewayUnavailableException(UNAVAILABLE, null);
        }
        return toTokenizedCard(token);
    }

    /** Ecopedia → pasarela. El número se limpia acá porque la pasarela solo acepta dígitos. */
    private GatewayCardTokenRequest toGatewayRequest(CardData card) {
        String digits = card.number() == null ? "" : card.number().replaceAll("[\\s-]", "");
        return new GatewayCardTokenRequest(
                digits, card.expiryMonth(), card.expiryYear(), new GatewayCardholder(card.holderName()));
    }

    /** Pasarela → Ecopedia. Una red que la pasarela acepta pero Ecopedia no, se rechaza acá. */
    private TokenizedCard toTokenizedCard(GatewayCardToken token) {
        // Map.of no admite get(null): sin red en la respuesta, se trata como red no aceptada.
        CardBrand brand = token.paymentMethodId() == null ? null : ACCEPTED_NETWORKS.get(token.paymentMethodId());
        if (brand == null) {
            /*
             * La pasarela ya emitió el token, pero no se guarda en ninguna parte: queda huérfano
             * del lado de ella, sin ningún cobro asociado. Es lo mismo que pasa con una pasarela real
             * cuando el comercio decide no usar un token, y no tiene costo.
             */
            throw new IllegalArgumentException(UNSUPPORTED_BRAND);
        }
        return new TokenizedCard(brand, token.lastFourDigits(), token.id());
    }

    /**
     * El error de la pasarela, en el idioma de Ecopedia.
     *
     * <ul>
     *   <li>402 {@code card_error}: la tarjeta no sirve. Es un dato que el conductor puede corregir,
     *       así que va como {@link IllegalArgumentException} con el motivo en castellano, y la capa
     *       web lo devuelve como 400.
     *   <li>401 o 403: nuestra clave está mal configurada. No es culpa del conductor ni lo puede
     *       arreglar: para él la pasarela no está disponible, y en el log queda el motivo real.
     *   <li>5xx: la pasarela está con problemas.
     *   <li>Cualquier otro 4xx: armamos mal el pedido. Es un error nuestro y tiene que verse como tal.
     * </ul>
     */
    private RuntimeException translateError(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        GatewayError error = readError(response);
        String code = error == null ? null : error.code();

        if (status.value() == 402) {
            log.info("La pasarela rechazó la tarjeta: {}", code);
            String message =
                    code == null ? GENERIC_CARD_ERROR : CARD_ERROR_MESSAGES.getOrDefault(code, GENERIC_CARD_ERROR);
            return new IllegalArgumentException(message);
        }
        if (status.value() == 401 || status.value() == 403) {
            log.error("La pasarela rechazó la clave del comercio. Revisar ecopedia.payment-gateway.api-key");
            return new PaymentGatewayUnavailableException(UNAVAILABLE, null);
        }
        if (status.is5xxServerError()) {
            log.warn("La pasarela contestó {}: {}", status.value(), code);
            return new PaymentGatewayUnavailableException(UNAVAILABLE, null);
        }

        log.error("La pasarela no aceptó el pedido ({} {}). Es un error de este lado", status.value(), code);
        return new IllegalStateException("La pasarela no aceptó el pedido: " + code);
    }

    private GatewayError readError(ClientHttpResponse response) {
        try {
            GatewayErrorEnvelope envelope = objectMapper.readValue(response.getBody(), GatewayErrorEnvelope.class);
            return envelope == null ? null : envelope.error();
        } catch (IOException unreadable) {
            // Un 502 de un proxy, por ejemplo, viene en HTML. Se sigue con el código HTTP solo.
            return null;
        }
    }

    /*
     * El contrato de la pasarela, de este lado. Son del Adapter y de nadie más: nada fuera de esta
     * clase tiene por qué conocer cómo se llaman los campos de un proveedor.
     */

    record GatewayCardTokenRequest(
            @JsonProperty("card_number") String cardNumber,
            @JsonProperty("expiration_month") int expirationMonth,
            @JsonProperty("expiration_year") int expirationYear,
            @JsonProperty("cardholder") GatewayCardholder cardholder) {

        /** Tapa el número por si alguien loguea el pedido entero. Ver {@link CardData#toString()}. */
        @Override
        public String toString() {
            return "GatewayCardTokenRequest[card_number=****]";
        }
    }

    record GatewayCardholder(@JsonProperty("name") String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GatewayCardToken(
            @JsonProperty("id") String id,
            @JsonProperty("payment_method_id") String paymentMethodId,
            @JsonProperty("last_four_digits") String lastFourDigits) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GatewayErrorEnvelope(@JsonProperty("error") GatewayError error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GatewayError(
            @JsonProperty("type") String type,
            @JsonProperty("code") String code,
            @JsonProperty("message") String message) {}
}
