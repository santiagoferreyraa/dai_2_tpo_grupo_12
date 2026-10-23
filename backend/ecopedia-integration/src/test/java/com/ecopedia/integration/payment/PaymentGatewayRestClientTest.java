package com.ecopedia.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.CardValidation;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.GatewayRefund;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import com.ecopedia.integration.payment.gateway.PaymentGatewayRestClient;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El adaptador contra la pasarela, sin la pasarela: {@link MockRestServiceServer} contesta lo que
 * contestaría ella. Lo que se prueba es la traducción en los dos sentidos —el contrato de la
 * pasarela, en snake_case, contra el de Pagos— y que cada forma de fallar termine donde tiene que
 * terminar. Que la pasarela de verdad conteste así lo prueban sus propios tests.
 */
class PaymentGatewayRestClientTest {

    private static final CardData CARD = new CardData("4242 4242 4242 4242", 9, 2035, "SANTIAGO R", null);

    private MockRestServiceServer gateway;
    private PaymentGatewayRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://gateway.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer clave-de-prueba");
        gateway = MockRestServiceServer.bindTo(builder).build();
        client = new PaymentGatewayRestClient(builder.build());
    }

    private static String error(String code) {
        return """
                {"status":400,"error":"bad_request","message":"x","cause":[{"code":"%s","description":"x"}]}
                """
                .formatted(code);
    }

    @Test
    @DisplayName("Tokeniza con la clave de acceso y devuelve marca, últimos cuatro y token")
    void tokenizes() {
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer clave-de-prueba"))
                .andExpect(
                        content()
                                .json(
                                        """
                        {"card_number":"4242 4242 4242 4242","expiration_month":9,"expiration_year":2035,
                         "cardholder":{"name":"SANTIAGO R"}}
                        """))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                {"id":"tok_abc","payment_method_id":"visa","last_four_digits":"4242",
                                 "expiration_month":9,"expiration_year":2035}
                                """));

        TokenizedCard card = client.tokenize(CARD);

        assertThat(card).isEqualTo(new TokenizedCard(CardBrand.VISA, "4242", "tok_abc"));
        gateway.verify();
    }

    @Test
    @DisplayName("Traduce el código del rechazo a una frase para el conductor")
    void translatesRejections() {
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens"))
                .andRespond(
                        withBadRequest().contentType(MediaType.APPLICATION_JSON).body(error("invalid_check_digit")));

        assertThatThrownBy(() -> client.tokenize(CARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El número de tarjeta no es válido. Revisá que esté bien copiado");
    }

    @Test
    @DisplayName("Una tarjeta que no es de prueba recibe qué tarjeta usar")
    void explainsTheTestCatalog() {
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens"))
                .andRespond(
                        withBadRequest().contentType(MediaType.APPLICATION_JSON).body(error("not_a_test_card")));

        assertThatThrownBy(() -> client.tokenize(CARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4242 4242 4242 4242");
    }

    @Test
    @DisplayName("Un código que no conoce no se muestra: el conductor recibe una frase genérica")
    void unknownCodesAreNotShown() {
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens"))
                .andRespond(
                        withBadRequest().contentType(MediaType.APPLICATION_JSON).body(error("something_new")));

        assertThatThrownBy(() -> client.tokenize(CARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La tarjeta fue rechazada. Revisá los datos o probá con otra");
    }

    @Test
    @DisplayName("Pasarela caída, con error 500 o rechazando la clave: no está disponible")
    void unavailableGateway() {
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens"))
                .andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(() -> client.tokenize(CARD)).isInstanceOf(PaymentGatewayUnavailableException.class);

        gateway.reset();
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens")).andRespond(withServerError());
        assertThatThrownBy(() -> client.tokenize(CARD)).isInstanceOf(PaymentGatewayUnavailableException.class);

        gateway.reset();
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.tokenize(CARD)).isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    @DisplayName("Cobra con la clave de idempotencia en la cabecera")
    void chargesWithIdempotencyKey() {
        gateway.expect(requestTo("http://gateway.test/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "booking-12-deposit"))
                .andExpect(
                        content()
                                .json(
                                        """
                        {"transaction_amount":1500.00,"token":"tok_abc","description":"Seña",
                         "external_reference":"booking-12"}
                        """))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                {"id":"pay_1","status":"approved","status_detail":"accredited"}
                                """));

        GatewayCharge charge =
                client.charge("tok_abc", new BigDecimal("1500.00"), "Seña", "booking-12", "booking-12-deposit");

        assertThat(charge).isEqualTo(new GatewayCharge("pay_1", true, "accredited"));
        gateway.verify();
    }

    @Test
    @DisplayName("Un cobro rechazado es una respuesta, no una excepción")
    void rejectedCharge() {
        gateway.expect(requestTo("http://gateway.test/v1/payments"))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                {"id":"pay_2","status":"rejected","status_detail":"cc_rejected_insufficient_amount"}
                                """));

        GatewayCharge charge = client.charge("tok_abc", BigDecimal.TEN, "Seña", "booking-12", "k");

        assertThat(charge.approved()).isFalse();
        assertThat(charge.statusDetail()).isEqualTo("cc_rejected_insufficient_amount");
    }

    @Test
    @DisplayName("Sin respuesta al cobrar, no está disponible: no se puede saber si cobró")
    void chargeWithoutAnswer() {
        gateway.expect(requestTo("http://gateway.test/v1/payments"))
                .andRespond(withException(new java.net.http.HttpTimeoutException("request timed out")));

        assertThatThrownBy(() -> client.charge("tok_abc", BigDecimal.TEN, "Seña", "booking-12", "k"))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    @DisplayName("Reintegra todo cuando no se pide un importe")
    void refunds() {
        gateway.expect(requestTo("http://gateway.test/v1/payments/pay_1/refunds"))
                .andExpect(header("X-Idempotency-Key", "booking-12-refund"))
                .andExpect(content().json("{\"amount\":null}"))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                {"id":"ref_1","payment_id":"pay_1","amount":1500.00,"status":"approved"}
                                """));

        GatewayRefund refund = client.refund("pay_1", null, "booking-12-refund");

        assertThat(refund).isEqualTo(new GatewayRefund("ref_1", "pay_1", new BigDecimal("1500.00")));
    }

    @Test
    @DisplayName("Valida sin cobrar, y un token que la pasarela no conoce no es cobrable")
    void validates() {
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens/tok_abc/validations"))
                .andRespond(withSuccess(
                        "{\"status\":\"approved\",\"status_detail\":\"chargeable\"}", MediaType.APPLICATION_JSON));
        assertThat(client.validate("tok_abc")).isEqualTo(new CardValidation(true, "chargeable"));

        gateway.reset();
        gateway.expect(requestTo("http://gateway.test/v1/card_tokens/tok_old/validations"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error("card_token_not_found")));
        assertThat(client.validate("tok_old")).isEqualTo(new CardValidation(false, "card_token_not_found"));
    }
}
