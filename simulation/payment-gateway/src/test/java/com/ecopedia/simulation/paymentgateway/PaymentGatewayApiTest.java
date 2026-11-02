package com.ecopedia.simulation.paymentgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * La API de la pasarela de punta a punta, con la base en memoria.
 *
 * <p>La tarjeta lenta no espera acá ({@code slow-card-delay: 0s}): lo que se prueba de ella es que
 * aprueba; la demora la mide el test del cliente en Pagos.
 */
@SpringBootTest(properties = "simulation.payment-gateway.slow-card-delay=0s")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PaymentGatewayApiTest {

    private static final String ACCESS_TOKEN = "Bearer ecopedia-dev-only-gateway-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, ACCESS_TOKEN).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions tokenize(String number, int month, int year) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "card_number", number,
                "expiration_month", month,
                "expiration_year", year,
                "cardholder", Map.of("name", "SANTIAGO R")));
        return mockMvc.perform(authorized(post("/v1/card_tokens")).content(body));
    }

    private String tokenFor(String number) throws Exception {
        String body = tokenize(number, 9, 2035)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private ResultActions charge(String token, String amount, String idempotencyKey) throws Exception {
        String body =
                """
                {"transaction_amount": %s, "token": "%s", "description": "Seña", "external_reference": "booking-1"}
                """
                        .formatted(amount, token);
        return mockMvc.perform(authorized(post("/v1/payments"))
                .header("X-Idempotency-Key", idempotencyKey)
                .content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Sin la clave de acceso no se habla con la pasarela")
    void requiresTheAccessToken() throws Exception {
        mockMvc.perform(post("/v1/card_tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.cause[0].code").value("invalid_access_token"));
        mockMvc.perform(post("/v1/card_tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer otra-clave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tokeniza una tarjeta de prueba y devuelve marca y últimos cuatro, nunca el número")
    void tokenizesATestCard() throws Exception {
        String body = tokenize("4242 4242 4242 4242", 9, 2035)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("tok_")))
                .andExpect(jsonPath("$.payment_method_id").value("visa"))
                .andExpect(jsonPath("$.last_four_digits").value("4242"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("4242424242424242");
    }

    @Test
    @DisplayName("La base de la pasarela no guarda el número en ninguna columna")
    void neverStoresTheCardNumber() throws Exception {
        tokenFor("5555555555554444");

        List<Map<String, Object>> rows = jdbc.queryForList("select * from card_tokens");
        assertThat(rows).isNotEmpty();
        assertThat(rows.toString()).doesNotContain("5555555555554444");
    }

    @Test
    @DisplayName("Reconoce las tres marcas, y acepta una tarjeta que vence este mismo mes")
    void brandsAndCurrentMonth() throws Exception {
        java.time.YearMonth now = java.time.YearMonth.now();

        tokenize("5555-5555-5555-4444", now.getMonthValue(), now.getYear())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment_method_id").value("master"));
        tokenize("3782 822463 10005", 9, 2035)
                .andExpect(jsonPath("$.payment_method_id").value("amex"));
    }

    @Test
    @DisplayName("Una tarjeta real, aunque sea válida, no entra: solo las de prueba")
    void rejectsCardsOutsideTheTestCatalog() throws Exception {
        // 4012 8888 8888 1881 pasa el dígito verificador, pero no es del catálogo.
        tokenize("4012888888881881", 9, 2035)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.cause[0].code").value("not_a_test_card"));
    }

    @Test
    @DisplayName("Cada rechazo de forma tiene su código")
    void rejectsMalformedCardsWithStableCodes() throws Exception {
        tokenize("4242", 9, 2035).andExpect(jsonPath("$.cause[0].code").value("invalid_card_number"));
        tokenize("4242 4242 4242 4241", 9, 2035)
                .andExpect(jsonPath("$.cause[0].code").value("invalid_check_digit"));
        tokenize("4242-ABCD", 9, 2035).andExpect(jsonPath("$.cause[0].code").value("invalid_card_number"));
        tokenize("4242424242424242", 13, 2035)
                .andExpect(jsonPath("$.cause[0].code").value("invalid_expiration_month"));
        tokenize("4242424242424242", 1, 2020)
                .andExpect(jsonPath("$.cause[0].code").value("card_expired"));
    }

    @Test
    @DisplayName("Cobra a la tarjeta que aprueba")
    void approvesACharge() throws Exception {
        String token = tokenFor("4111111111111111");

        charge(token, "1500.00", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.status_detail").value("accredited"))
                .andExpect(jsonPath("$.transaction_amount").value(1500.00))
                .andExpect(jsonPath("$.external_reference").value("booking-1"));
    }

    @Test
    @DisplayName("La tarjeta que rechaza y la sin fondos devuelven un cobro rechazado, no un error")
    void rejectedChargesAreStillPayments() throws Exception {
        charge(tokenFor("4000000000000002"), "100", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.status_detail").value("cc_rejected_other_reason"));

        charge(tokenFor("4000000000009995"), "100", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.status_detail").value("cc_rejected_insufficient_amount"));
    }

    @Test
    @DisplayName("La misma clave de idempotencia devuelve el mismo cobro, no uno nuevo")
    void sameIdempotencyKeySameCharge() throws Exception {
        String token = tokenFor("4242424242424242");
        String key = UUID.randomUUID().toString();

        String first = json(charge(token, "500", key)).get("id").asText();
        String second = json(charge(token, "500", key)).get("id").asText();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from payments where idempotency_key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Sin clave de idempotencia, el cobro no se acepta")
    void requiresAnIdempotencyKey() throws Exception {
        String body = """
                {"transaction_amount": 100, "token": "%s"}
                """
                .formatted(tokenFor("4242424242424242"));
        mockMvc.perform(authorized(post("/v1/payments")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.cause[0].code").value("missing_idempotency_key"));
    }

    @Test
    @DisplayName("Un token que no existe da 404 con su código")
    void unknownTokenIsNotFound() throws Exception {
        charge("tok_inexistente", "100", UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.cause[0].code").value("card_token_not_found"));
    }

    @Test
    @DisplayName("Reintegra todo el cobro, y un segundo reintegro ya no tiene qué devolver")
    void refundsAPayment() throws Exception {
        String paymentId = json(charge(
                        tokenFor("4242424242424242"), "800", UUID.randomUUID().toString()))
                .get("id")
                .asText();

        mockMvc.perform(authorized(post("/v1/payments/{id}/refunds", paymentId))
                        .header("X-Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(800))
                .andExpect(jsonPath("$.payment_id").value(paymentId));

        mockMvc.perform(authorized(get("/v1/payments/{id}", paymentId)))
                .andExpect(jsonPath("$.status").value("refunded"))
                .andExpect(jsonPath("$.transaction_amount_refunded").value(800));

        mockMvc.perform(authorized(post("/v1/payments/{id}/refunds", paymentId))
                        .header("X-Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.cause[0].code").value("payment_not_refundable"));
    }

    @Test
    @DisplayName("No se reintegra más de lo cobrado, ni un cobro rechazado")
    void refundRules() throws Exception {
        String approved = json(charge(
                        tokenFor("4242424242424242"), "300", UUID.randomUUID().toString()))
                .get("id")
                .asText();
        mockMvc.perform(authorized(post("/v1/payments/{id}/refunds", approved))
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .content("{\"amount\": 301}"))
                .andExpect(jsonPath("$.cause[0].code").value("amount_exceeds_refundable"));

        String rejected = json(charge(
                        tokenFor("4000000000000002"), "300", UUID.randomUUID().toString()))
                .get("id")
                .asText();
        mockMvc.perform(authorized(post("/v1/payments/{id}/refunds", rejected))
                        .header("X-Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.cause[0].code").value("payment_not_refundable"));
    }

    @Test
    @DisplayName("Validar no cobra: la sin fondos da que sí y la que rechaza da que no")
    void validatesWithoutCharging() throws Exception {
        Integer paymentsBefore = jdbc.queryForObject("select count(*) from payments", Integer.class);

        mockMvc.perform(authorized(post("/v1/card_tokens/{id}/validations", tokenFor("4000000000009995"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        mockMvc.perform(authorized(post("/v1/card_tokens/{id}/validations", tokenFor("4000000000000002"))))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.status_detail").value("cc_rejected_other_reason"));

        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class))
                .isEqualTo(paymentsBefore);
    }

    @Test
    @DisplayName("La tarjeta lenta, sin la demora, aprueba como cualquier otra")
    void slowCardApproves() throws Exception {
        charge(tokenFor("4000000000000077"), "100", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.status").value("approved"));
    }
}
