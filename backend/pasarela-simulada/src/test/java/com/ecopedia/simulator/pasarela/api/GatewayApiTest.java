package com.ecopedia.simulator.pasarela.api;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** La API de la pasarela tal como la ve un comercio: autenticación, tokens, pagos y reintegros. */
@SpringBootTest
@AutoConfigureMockMvc
class GatewayApiTest {

    private static final String AUTH = "Bearer sk_test_ecopedia";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path)
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String card(String number) {
        return """
                {"card_number":"%s","expiration_month":9,"expiration_year":2099,\
                "cardholder":{"name":"SANTIAGO R"}}"""
                .formatted(number);
    }

    private String tokenFor(String number) throws Exception {
        String body = mockMvc.perform(postJson("/v1/card_tokens", card(number)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private static String payment(String token, long amount) {
        return """
                {"amount":%d,"currency_id":"ARS","card_token":"%s","external_reference":"booking-1"}"""
                .formatted(amount, token);
    }

    @Test
    @DisplayName("Sin la clave del comercio no contesta nada")
    void requiresTheApiKey() throws Exception {
        mockMvc.perform(post("/v1/card_tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(card("4111111111111111")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    @DisplayName("Tokeniza y contesta en su propio vocabulario, en snake_case")
    void issuesAToken() throws Exception {
        mockMvc.perform(postJson("/v1/card_tokens", card("4111111111111111")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(startsWith("tok_")))
                .andExpect(jsonPath("$.payment_method_id").value("visa"))
                .andExpect(jsonPath("$.last_four_digits").value("1111"))
                .andExpect(jsonPath("$.card_number").doesNotExist());
    }

    @Test
    @DisplayName("Una tarjeta mal tipeada vuelve como 402 con código de la pasarela")
    void rejectsATypo() throws Exception {
        mockMvc.perform(postJson("/v1/card_tokens", card("4111111111111112")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.type").value("card_error"))
                .andExpect(jsonPath("$.error.code").value("incorrect_number"));
    }

    @Test
    @DisplayName("Un cuerpo incompleto es un error del comercio: 400")
    void rejectsAnIncompleteBody() throws Exception {
        mockMvc.perform(postJson("/v1/card_tokens", "{\"card_number\":\"4111111111111111\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
    }

    @Test
    @DisplayName("Verifica una tarjeta cobrable y rechaza la de fondos insuficientes")
    void verifiesTheCard() throws Exception {
        String good = tokenFor("4111111111111111");
        String broke = tokenFor("4000000000009995");

        mockMvc.perform(post("/v1/card_tokens/" + good + "/verify").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        mockMvc.perform(post("/v1/card_tokens/" + broke + "/verify").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.status_detail").value("cc_rejected_insufficient_amount"));
    }

    @Test
    @DisplayName("Cobra en centavos y la misma clave de idempotencia no cobra dos veces")
    void chargesOnceWithTheSameIdempotencyKey() throws Exception {
        String token = tokenFor("4111111111111111");

        String first = mockMvc.perform(postJson("/v1/payments", payment(token, 150000))
                        .header("X-Idempotency-Key", "booking-1-deposit"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.amount").value(150000))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String retry = mockMvc.perform(postJson("/v1/payments", payment(token, 150000))
                        .header("X-Idempotency-Key", "booking-1-deposit"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstPayment = objectMapper.readTree(first);
        assertEquals(
                firstPayment.get("id").asText(),
                objectMapper.readTree(retry).get("id").asText());
    }

    @Test
    @DisplayName("Rechaza el cobro contra una tarjeta sin fondos")
    void rejectsAChargeWithoutFunds() throws Exception {
        String token = tokenFor("4000000000009995");

        mockMvc.perform(postJson("/v1/payments", payment(token, 1000)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("cc_rejected_insufficient_amount"));
    }

    @Test
    @DisplayName("Reintegra parcial y no deja reintegrar más de lo cobrado")
    void refundsUpToTheChargedAmount() throws Exception {
        String token = tokenFor("4111111111111111");
        String body = mockMvc.perform(postJson("/v1/payments", payment(token, 1000)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String paymentId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(postJson("/v1/payments/" + paymentId + "/refunds", "{\"amount\":400}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(400));

        mockMvc.perform(postJson("/v1/payments/" + paymentId + "/refunds", "{\"amount\":700}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("amount_exceeds_payment"));

        mockMvc.perform(get("/v1/payments/" + paymentId).header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(jsonPath("$.status").value("partially_refunded"))
                .andExpect(jsonPath("$.amount_refunded").value(400));
    }

    @Test
    @DisplayName("Un token que no existe es 404")
    void unknownTokenIsNotFound() throws Exception {
        mockMvc.perform(postJson("/v1/payments", payment("tok_inexistente", 1000)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("resource_not_found"));
    }
}
