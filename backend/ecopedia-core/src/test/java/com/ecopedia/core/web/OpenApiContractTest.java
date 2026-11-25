package com.ecopedia.core.web;

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
 * El contrato OpenAPI de core (QA-25): se publica sin sesión, cubre solo {@code /api} y declara lo
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
    @DisplayName("Las operaciones con rol piden el token y declaran el 401; las públicas, no")
    void tokenOnlyWhereARoleIsRequired() {
        JsonNode profile = operation("get", "/api/users/profile");
        assertThat(profile.path("security").toString()).contains("bearer");
        assertThat(codes(profile)).contains("401");

        JsonNode login = operation("post", "/api/auth/login");
        assertThat(login.has("security")).isFalse();
        assertThat(codes(login)).doesNotContain("401");

        assertThat(contract.path("components")
                        .path("securitySchemes")
                        .path("bearer")
                        .path("scheme")
                        .asText())
                .isEqualTo("bearer");
    }

    @Test
    @DisplayName("Los errores se declaran en Problem Details en todas las operaciones")
    void errorsAsProblemDetails() {
        assertThat(allOperations()).allSatisfy(operation -> {
            assertThat(codes(operation)).contains("400", "404");
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
        assertThat(codes(operation("post", "/api/auth/register")))
                .contains("201")
                .doesNotContain("200");
        assertThat(codes(operation("post", "/api/stations"))).contains("201").doesNotContain("200");
        assertThat(codes(operation("delete", "/api/connectors/{id}")))
                .contains("204")
                .doesNotContain("200");
        assertThat(codes(operation("put", "/api/users/profile/password")))
                .contains("204")
                .doesNotContain("200");
    }

    @Test
    @DisplayName("Un campo que puede venir en null está declarado como nulable, también si es un objeto")
    void optionalFieldsAreNullable() {
        JsonNode schemas = contract.path("components").path("schemas");
        assertThat(schemas.path("StationResponse")
                        .path("properties")
                        .path("nodeId")
                        .path("nullable")
                        .asBoolean())
                .isTrue();
        JsonNode vehicle =
                schemas.path("UserProfileResponse").path("properties").path("vehicle");
        assertThat(vehicle.path("nullable").asBoolean()).isTrue();
        assertThat(vehicle.path("allOf").toString()).contains("VehicleModelResponse");
    }

    @Test
    @DisplayName("Los topes de validación quedan en el contrato")
    void limitsInTheContract() {
        JsonNode schemas = contract.path("components").path("schemas");
        JsonNode latitude = schemas.path("StationRequest").path("properties").path("latitude");
        assertThat(latitude.path("minimum").asDouble()).isEqualTo(-90);
        assertThat(latitude.path("maximum").asDouble()).isEqualTo(90);
        assertThat(schemas.path("ConfigureConnectorRequest")
                        .path("properties")
                        .path("maxPowerKw")
                        .path("maximum")
                        .asDouble())
                .isEqualTo(9999.99);
    }

    @Test
    @DisplayName("Las respuestas declaran JSON, no */*")
    void jsonResponses() {
        JsonNode content =
                operation("get", "/api/stations").path("responses").path("200").path("content");
        assertThat(content.has("application/json")).isTrue();
        assertThat(content.has("*/*")).isFalse();
    }
}
