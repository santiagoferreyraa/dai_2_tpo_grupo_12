package com.ecopedia.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import com.ecopedia.core.vehicle.domain.VehicleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Cómo contesta core los errores (QA-31, QA-32, QA-33): siempre en Problem Details, con el estado
 * correcto, sin mensajes internos y con un único 404 para todo lo que no se encuentra.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiErrorResponseTest {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    /* Solo para provocar un error inesperado; ningún otro caso de esta clase lo usa. */
    @MockitoBean
    private VehicleService vehicleService;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private String adminBearer() {
        User admin = userService.register(
                new RegistrationData("admin-errores@ecopedia.test", "unaClave123", "Admin errores", Role.ADMIN));
        return "Bearer " + tokenProvider.generateToken(admin.getId(), admin.getEmail(), Role.ADMIN);
    }

    private String notFoundBody(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("DEF-07/08 · Una estación inexistente da el mismo 404 que una ruta inexistente, sin el id")
    void unknownStationLooksLikeAMissingRoute() throws Exception {
        String station = mockMvc.perform(get("/api/stations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(station).doesNotContain("Estación");
        assertThat(station.replace("/api/stations/999999", "/api/no-existe")).isEqualTo(notFoundBody("/api/no-existe"));
    }

    @Test
    @DisplayName("DEF-07 · Un usuario inexistente da 404, no 400")
    void unknownUserIsNotFound() throws Exception {
        mockMvc.perform(get("/api/users/999999").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL));
    }

    @Test
    @DisplayName("DEF-07 · El precio de una estación inexistente da 404, no 400")
    void unknownStationPricingIsNotFound() throws Exception {
        mockMvc.perform(get("/api/pricing/stations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON));
    }

    @Test
    @DisplayName("DEF-12 · Un parámetro con formato inválido da 400 sin el mensaje de Java")
    void malformedParameterIsBadRequest() throws Exception {
        assertMalformed(get("/api/connectors").param("stationId", "abc"));
        assertMalformed(get("/api/search").param("lat", "1 OR 1=1--").param("lon", "-58.38"));
    }

    private void assertMalformed(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("For input string").doesNotContain("Failed to convert");
    }

    @Test
    @DisplayName("DEF-12 · Un id de usuario con formato inválido da 400 sin el mensaje de Java")
    void malformedUserIdIsBadRequest() throws Exception {
        assertMalformed(get("/api/users/abc").header(HttpHeaders.AUTHORIZATION, adminBearer()));
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON da 400 en Problem Details")
    void unreadableBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("La solicitud no es válida."));
    }

    @Test
    @DisplayName("Un método no permitido da 405 en Problem Details")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/vehicle-models"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(header().exists(HttpHeaders.ALLOW));
    }

    @Test
    @DisplayName("Un error inesperado da 500 con un texto genérico, sin el mensaje interno")
    void unexpectedErrorHidesItsMessage() throws Exception {
        when(vehicleService.listAvailableModels()).thenThrow(new IllegalStateException("detalle interno secreto"));

        String body = mockMvc.perform(get("/api/vehicle-models"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(ApiProblems.UNEXPECTED_DETAIL))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("secreto");
    }

    @Test
    @DisplayName("QA-33 · Sin sesión, 401 con WWW-Authenticate: Bearer y Problem Details")
    void anonymousGetsUnauthorizedWithChallenge() throws Exception {
        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"ecopedia\""))
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/users/profile"));
    }

    @Test
    @DisplayName("QA-32 · Las respuestas de la API traen una CSP que no permite nada, también las de error")
    void apiResponsesCarryAStrictContentSecurityPolicy() throws Exception {
        String policy = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", policy))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", policy));
    }
}
