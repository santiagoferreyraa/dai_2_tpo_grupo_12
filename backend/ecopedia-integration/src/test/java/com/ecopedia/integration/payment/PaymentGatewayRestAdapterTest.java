package com.ecopedia.integration.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import com.ecopedia.integration.payment.gateway.PaymentGatewayRestAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

/**
 * El Adapter (ECO-56): que traduzca bien en las dos direcciones.
 *
 * <p>La pasarela se reemplaza por un servidor simulado de Spring, así que estos tests no necesitan
 * la pasarela levantada: lo que se prueba es la traducción, no la red. Que la pasarela de verdad
 * conteste lo que se simula acá lo prueba {@code GatewayApiTest}, en el módulo de la pasarela.
 */
class PaymentGatewayRestAdapterTest {

    private static final String BASE_URL = "http://pasarela.test";

    private MockRestServiceServer server;
    private PaymentGatewayRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer sk_test_ecopedia");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new PaymentGatewayRestAdapter(builder.build(), new ObjectMapper());
    }

    @AfterEach
    void verifyAllRequestsWereMade() {
        server.verify();
    }

    private static CardData card(String number) {
        return new CardData(number, 9, 2029, "SANTIAGO R", "La del laburo");
    }

    private ResponseActions expectTokenization() {
        return server.expect(requestTo(BASE_URL + "/v1/card_tokens")).andExpect(method(HttpMethod.POST));
    }

    private void respondWithCardError(String code) {
        expectTokenization()
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"card_error\",\"code\":\"" + code + "\",\"message\":\"x\"}}"));
    }

    @Test
    @DisplayName("Traduce el pedido al vocabulario de la pasarela y la respuesta al de Ecopedia")
    void translatesBothWays() {
        expectTokenization()
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk_test_ecopedia"))
                // Ecopedia → pasarela: el número llega limpio y los campos con sus nombres.
                .andExpect(jsonPath("$.card_number").value("5555555555554444"))
                .andExpect(jsonPath("$.expiration_month").value(9))
                .andExpect(jsonPath("$.expiration_year").value(2029))
                .andExpect(jsonPath("$.cardholder.name").value("SANTIAGO R"))
                // Lo que es solo de Ecopedia no sale.
                .andExpect(jsonPath("$.label").doesNotExist())
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                {"id":"tok_abc","payment_method_id":"master","first_six_digits":"555555",\
                                "last_four_digits":"4444","expiration_month":9,"expiration_year":2029,\
                                "date_created":"2026-10-26T20:00:00Z"}"""));

        TokenizedCard tokenized = adapter.tokenize(card("5555 5555-5555 4444"));

        // Pasarela → Ecopedia: "master" es MASTERCARD.
        assertEquals(new TokenizedCard(CardBrand.MASTERCARD, "4444", "tok_abc"), tokenized);
    }

    @Test
    @DisplayName("Un número mal copiado vuelve con un mensaje que el conductor entiende")
    void translatesATypo() {
        respondWithCardError("incorrect_number");

        var rejected = assertThrows(IllegalArgumentException.class, () -> adapter.tokenize(card("4111111111111112")));
        assertTrue(rejected.getMessage().contains("no es válido"));
    }

    @Test
    @DisplayName("Una tarjeta vencida dice que está vencida")
    void translatesAnExpiredCard() {
        respondWithCardError("expired_card");

        var rejected = assertThrows(IllegalArgumentException.class, () -> adapter.tokenize(card("4111111111111111")));
        assertEquals("La tarjeta está vencida", rejected.getMessage());
    }

    @Test
    @DisplayName("Un código de rechazo que no conocemos no rompe nada: mensaje genérico")
    void toleratesUnknownCardErrors() {
        respondWithCardError("some_new_code_from_the_future");

        var rejected = assertThrows(IllegalArgumentException.class, () -> adapter.tokenize(card("4111111111111111")));
        assertTrue(rejected.getMessage().contains("rechazó la tarjeta"));
    }

    @Test
    @DisplayName("Una red que la pasarela acepta y Ecopedia no, se rechaza de este lado")
    void rejectsANetworkEcopediaDoesNotCharge() {
        expectTokenization()
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"tok_d\",\"payment_method_id\":\"diners\",\"last_four_digits\":\"5904\"}"));

        var rejected = assertThrows(IllegalArgumentException.class, () -> adapter.tokenize(card("30569309025904")));
        assertTrue(rejected.getMessage().contains("solo se aceptan"));
    }

    @Test
    @DisplayName("La pasarela con problemas es 'no disponible', no un error de la tarjeta")
    void serverErrorMeansUnavailable() {
        expectTokenization().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(PaymentGatewayUnavailableException.class, () -> adapter.tokenize(card("4111111111111111")));
    }

    @Test
    @DisplayName("Un 502 en HTML, como el de un proxy, también es 'no disponible'")
    void unreadableErrorBodyMeansUnavailable() {
        expectTokenization()
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>Bad gateway</html>"));

        assertThrows(PaymentGatewayUnavailableException.class, () -> adapter.tokenize(card("4111111111111111")));
    }

    @Test
    @DisplayName("Con la pasarela apagada, 'no disponible'")
    void connectionRefusedMeansUnavailable() {
        expectTokenization().andRespond(withException(new ConnectException("Connection refused")));

        assertThrows(PaymentGatewayUnavailableException.class, () -> adapter.tokenize(card("4111111111111111")));
    }

    @Test
    @DisplayName("Una clave del comercio mal configurada no se le atribuye al conductor")
    void badApiKeyMeansUnavailable() {
        expectTokenization()
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"authentication_error\",\"code\":\"invalid_api_key\"}}"));

        assertThrows(PaymentGatewayUnavailableException.class, () -> adapter.tokenize(card("4111111111111111")));
    }

    @Test
    @DisplayName("Un pedido que la pasarela no entiende es un error nuestro, y se ve como tal")
    void badRequestIsOurBug() {
        expectTokenization()
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"parameter_invalid\"}}"));

        assertThrows(IllegalStateException.class, () -> adapter.tokenize(card("4111111111111111")));
    }
}
