package com.ecopedia.charging.security;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de ataque sobre la API de reservas (QA-07, ECO-157): JWT hostil, mass assignment e
 * inyección.
 *
 * <p>Este artefacto le cree al token el rol y el id (no tiene la tabla de usuarios), así que la
 * firma es lo único que separa a un conductor de un impostor. Por eso el caso alterado acá es
 * subirse de rol: un token de CPO al que se le escribe CONDUCTOR.
 *
 * <p><b>El rechazo hoy es 403, no 401</b>: ver DEF-02 y {@link #REJECTED_AS_ANONYMOUS}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiAttackTest {

    /** Lo que recibe hoy un pedido sin sesión válida. Los criterios piden 401: DEF-02 / ECO-183. */
    private static final int REJECTED_AS_ANONYMOUS = 403;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectorCatalog connectorCatalog;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @BeforeEach
    void connectorExists() {
        when(connectorCatalog.findConnector(anyLong()))
                .thenAnswer(call -> Optional.of(new ConnectorSnapshot(call.getArgument(0), 1L, "AVAILABLE")));
    }

    // --- JWT hostil -------------------------------------------------------------------------

    @Test
    @DisplayName("JWT · Control: con un token de conductor válido, sus reservas responden 200")
    void validTokenWorks() throws Exception {
        mockMvc.perform(get("/api/bookings/mine")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.valid(secret, 501L, "CONDUCTOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JWT · Un token vencido no lista reservas")
    void rejectsExpiredToken() throws Exception {
        mockMvc.perform(get("/api/bookings/mine")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.expired(secret, 502L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token de CPO reescrito a CONDUCTOR sin volver a firmar no se convierte en conductor")
    void rejectsTamperedRole() throws Exception {
        String operator = HostileTokens.valid(secret, 503L, "CPO");
        String forged = HostileTokens.tampered(operator, "\"role\":\"CPO\"", "\"role\":\"CONDUCTOR\"");

        // Con su token real, el operador no ve el endpoint del conductor (SEC-05: 404).
        mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token con alg=none, sin firma, no lista reservas")
    void rejectsAlgNone() throws Exception {
        mockMvc.perform(get("/api/bookings/mine")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.algNone(504L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token de otro emisor, firmado con su propia clave, no lista reservas")
    void rejectsTokenFromAnotherIssuer() throws Exception {
        mockMvc.perform(get("/api/bookings/mine")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.fromAnotherIssuer(505L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    // --- Mass assignment --------------------------------------------------------------------

    @Test
    @DisplayName("Mass assignment · Retener con id, driverId, status y expiresAt de más: los pone el servidor")
    void holdIgnoresServerOwnedFields() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(3)).plus(Duration.ofHours(5));
        String injectedId = "00000000-0000-0000-0000-000000000001";

        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.valid(secret, 506L, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"connectorId":7,"start":"%s","end":"%s",
                                 "id":"%s","driverId":999,"status":"CONFIRMED",
                                 "expiresAt":"2099-01-01T00:00:00Z","depositAmount":0}
                                """
                                        .formatted(start, start.plus(Duration.ofHours(1)), injectedId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(Matchers.not(injectedId)))
                .andExpect(jsonPath("$.expiresAt").value(Matchers.not(Matchers.startsWith("2099"))))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    // --- Inyección --------------------------------------------------------------------------

    @Test
    @DisplayName("Inyección · Un connectorId con SQL en el cuerpo de la retención responde 400")
    void holdRejectsSqlInConnectorId() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(4));

        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.valid(secret, 507L, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"connectorId":"7 OR 1=1","start":"%s","end":"%s"}
                                """
                                        .formatted(start, start.plus(Duration.ofHours(1)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Inyección · La disponibilidad con SQL en el connectorId responde 400")
    void availabilityRejectsSqlInParameters() throws Exception {
        Instant from = Instant.now().plus(Duration.ofDays(1));

        mockMvc.perform(get("/api/bookings/availability")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.valid(secret, 508L, "CONDUCTOR"))
                        .param("connectorId", "7' OR '1'='1")
                        .param("from", from.toString())
                        .param("to", from.plus(Duration.ofHours(4)).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Inyección · Cancelar con SQL en el id de la reserva responde 400 y no cancela nada")
    void cancelRejectsSqlInPath() throws Exception {
        mockMvc.perform(delete("/api/bookings/{bookingId}", "1 OR 1=1")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.valid(secret, 509L, "CONDUCTOR")))
                .andExpect(status().isBadRequest());
    }
}
