package com.ecopedia.charging.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.TestTokens;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.web.dto.ActivationHttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SessionApiTest {

    private static final long DRIVER_ID = 45L;
    private static final long CONNECTOR_ID = 8L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ecopedia.jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private ChargingActivationService activationService;

    @Test
    @DisplayName("POST /api/sessions/activation exitoso: responde 201 Created con los datos de la sesión activa")
    void activateSessionSuccessfully() throws Exception {
        Instant now = Instant.parse("2026-11-02T15:00:00Z");
        ActivationResult result = new ActivationResult(
                "session-12345", 10L, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), SessionStatus.ACTIVE, now);

        when(activationService.activate(any(ActivationRequest.class), any())).thenReturn(result);

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, "QR-CONN-8");

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("session-12345"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.connectorId").value(CONNECTOR_ID))
                .andExpect(jsonPath("$.powerLimitKw").value(50.00));
    }

    @Test
    @DisplayName("POST /api/sessions/activation anónimo responde 401 Unauthorized")
    void unauthenticatedActivationReturnsUnauthorized() throws Exception {
        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, "QR-CONN-8");

        mockMvc.perform(post("/api/sessions/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Conector ocupado concurrente responde 409 Conflict con code SLOT_UNAVAILABLE")
    void slotUnavailableReturnsConflict() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new SlotUnavailableException(CONNECTOR_ID));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Tarjeta inválida o con deuda responde 422 Unprocessable Entity con code PAYMENT_METHOD_INVALID")
    void paymentInvalidReturnsUnprocessableEntity() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new PaymentMethodInvalidException("Tarjeta rechazada"));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_INVALID"));
    }

    @Test
    @DisplayName("Cargador físico fuera de servicio responde 503 con code CHARGER_UNAVAILABLE")
    void chargerUnavailableReturnsServiceUnavailable() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new PhysicalChargerUnavailableException("El cargador físico no responde"));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHARGER_UNAVAILABLE"));
    }
}
