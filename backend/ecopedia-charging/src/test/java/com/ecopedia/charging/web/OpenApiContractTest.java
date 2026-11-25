package com.ecopedia.charging.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El contrato OpenAPI de charging (QA-25): se publica sin sesión, cubre solo {@code /api} y declara lo
 * que springdoc no puede deducir solo (el token, los errores en Problem Details y los 201/204).
 * Schemathesis valida las respuestas reales contra este mismo documento en el job de la API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    private JsonNode contract;

    @BeforeEach
    void readContract() throws Exception {
        // Sin cabecera Authorization: el contrato se lee sin sesión.
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        contract = mapper.readTree(body);
    }

    private JsonNode operation(String method, String path) {
        JsonNode operation = contract.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode())
                .as("%s %s en el contrato", method, path)
                .isFalse();
        return operation;
    }

    private List<JsonNode> allOperations() {
        List<JsonNode> operations = new ArrayList<>();
        contract.path("paths").forEach(path -> path.forEach(operations::add));
        return operations;
    }

    private static List<String> codes(JsonNode operation) {
        List<String> codes = new ArrayList<>();
        operation.path("responses").fieldNames().forEachRemaining(codes::add);
        return codes;
    }

    @Test
    @DisplayName("Se publica sin sesión y solo con las rutas de /api")
    void onlyPublicApi() {
        assertThat(contract.path("openapi").asText()).startsWith("3.");
        assertThat(contract.path("paths").fieldNames())
                .toIterable()
                .isNotEmpty()
                .allSatisfy(path -> assertThat(path).startsWith("/api/"));
    }

    @Test
    @DisplayName("Todas las operaciones piden el token y declaran el 401")
    void everyOperationNeedsTheToken() {
        assertThat(allOperations()).allSatisfy(operation -> {
            assertThat(operation.path("security").toString()).contains("bearer");
            assertThat(codes(operation)).contains("401");
        });
    }

    @Test
    @DisplayName("Los errores se declaran en Problem Details en todas las operaciones")
    void errorsAsProblemDetails() {
        assertThat(allOperations()).allSatisfy(operation -> {
            assertThat(codes(operation)).contains("400", "404", "402", "409", "422", "503");
            assertThat(operation.path("responses").path("404").path("content").has("application/problem+json"))
                    .isTrue();
        });
        assertThat(contract.path("components")
                        .path("schemas")
                        .path("Problem")
                        .path("properties")
                        .has("code"))
                .isTrue();
    }

    @Test
    @DisplayName("Los 201 y 204 están declarados como tales, no como 200")
    void createdAndNoContent() {
        assertThat(codes(operation("post", "/api/bookings/holds")))
                .contains("201")
                .doesNotContain("200");
        assertThat(codes(operation("post", "/api/checkout/booking")))
                .contains("201")
                .doesNotContain("200");
        assertThat(codes(operation("post", "/api/sessions/activation")))
                .contains("201")
                .doesNotContain("200");
        assertThat(codes(operation("delete", "/api/bookings/{bookingId}")))
                .contains("204")
                .doesNotContain("200");
        JsonNode active = operation("get", "/api/sessions/active");
        assertThat(codes(active)).contains("200", "204");
        // El 204 sale sin cuerpo: si el contrato le pusiera el de la sesión, Schemathesis lo marca.
        assertThat(active.path("responses").path("204").has("content")).isFalse();
        assertThat(active.path("responses").path("200").path("content").has("application/json"))
                .isTrue();
    }

    @Test
    @DisplayName("Las respuestas declaran JSON, no */*")
    void jsonResponses() {
        JsonNode content = operation("get", "/api/bookings/mine")
                .path("responses")
                .path("200")
                .path("content");
        assertThat(content.has("application/json")).isTrue();
        assertThat(content.has("*/*")).isFalse();
    }
}
