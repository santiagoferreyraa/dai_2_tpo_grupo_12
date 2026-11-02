package com.ecopedia.charging.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.security.HostileTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cómo contesta charging los errores (QA-31, QA-32, QA-33, DEF-03): siempre en Problem Details, con
 * el estado correcto, sin mensajes internos y con un único 404.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiErrorResponseTest {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @Test
    @DisplayName("QA-33 · Sin sesión, 401 con WWW-Authenticate: Bearer y Problem Details")
    void anonymousGetsUnauthorizedWithChallenge() throws Exception {
        mockMvc.perform(get("/api/bookings/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"ecopedia\""))
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/bookings/mine"));
    }

    @Test
    @DisplayName("QA-33 · Un token vencido también da 401")
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bookings/mine")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.expired(secret, 7L, "CONDUCTOR")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(PROBLEM_JSON));
    }

    @Test
    @DisplayName("QA-32 · Las respuestas de la API traen una CSP que no permite nada, también las de error")
    void apiResponsesCarryAStrictContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/api/bookings/mine"))
                .andExpect(header().string(
                                "Content-Security-Policy",
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Una ruta inexistente da el 404 de siempre, en Problem Details")
    void missingRouteIsTheCommonNotFound() throws Exception {
        mockMvc.perform(get("/api/no-existe")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.valid(secret, 7L, "CONDUCTOR")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL));
    }

    @Test
    @DisplayName("DEF-12 · Un id con formato inválido da 400 sin el mensaje de Java")
    void malformedIdIsBadRequest() throws Exception {
        String body = mockMvc.perform(delete("/api/bookings/abc")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.valid(secret, 7L, "CONDUCTOR")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("For input string").doesNotContain("Failed to convert");
    }

    @Test
    @DisplayName("SEC-05 y DEF-03 · Con el rol equivocado, el mismo 404 que una ruta inexistente; el log, sin el email")
    void wrongRoleLooksLikeAMissingRoute(CapturedOutput output) throws Exception {
        String operator = HostileTokens.valid(secret, 8L, "CPO");

        String denied = mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missing = mockMvc.perform(get("/api/no-existe").header(HttpHeaders.AUTHORIZATION, operator))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Lo único que cambia es "instance", la ruta pedida, que quien llama ya conoce.
        assertThat(denied.replace("/api/bookings/mine", "/api/no-existe")).isEqualTo(missing);
        assertThat(output)
                .contains(
                        "Acceso denegado (403, respondido como 404): usuario 8 con [ROLE_CPO] en GET /api/bookings/mine")
                .doesNotContain("cpo@ecopedia.test");
    }
}
