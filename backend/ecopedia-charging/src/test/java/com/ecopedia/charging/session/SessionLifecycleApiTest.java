package com.ecopedia.charging.session;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.TestTokens;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ChargingSessionNotFoundException;
import com.ecopedia.charging.session.domain.ChargingSessionService;
import com.ecopedia.charging.session.domain.ChargingSessionView;
import com.ecopedia.charging.session.domain.DriverAlreadyChargingException;
import com.ecopedia.charging.session.domain.InvalidAccessCodeException;
import com.ecopedia.charging.session.domain.SessionEndReason;
import com.ecopedia.charging.session.domain.SessionPaymentStatus;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.TooManyAccessCodeAttemptsException;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.web.ApiProblems;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
 * La API de la sesión de carga después de habilitarla, y los rechazos del código de la terminal (RF11,
 * ECO-44).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SessionLifecycleApiTest {

    private static final long DRIVER_ID = 45L;

    @Autowired
    private MockMvc mockMvc;

    @Value("${ecopedia.jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private ChargingActivationService activationService;

    @MockitoBean
    private ChargingSessionService sessionService;

    private String driver() {
        return TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR");
    }

    private static ChargingSessionView completed() {
        Instant start = Instant.parse("2026-11-02T15:00:00Z");
        return new ChargingSessionView(
                "sess-1",
                42L,
                8L,
                SessionStatus.COMPLETED,
                80,
                new BigDecimal("50.00"),
                start,
                start.plusSeconds(1800),
                new BigDecimal("20.000"),
                new BigDecimal("76.0"),
                SessionEndReason.DRIVER_STOPPED,
                new BigDecimal("7000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("4000.00"),
                SessionPaymentStatus.CHARGED);
    }

    @Test
    @DisplayName("POST /api/sessions/{id}/stop devuelve la sesión cerrada con su liquidación")
    void stopReturnsSettlement() throws Exception {
        when(sessionService.stop("sess-1", DRIVER_ID)).thenReturn(completed());

        mockMvc.perform(post("/api/sessions/sess-1/stop").header(HttpHeaders.AUTHORIZATION, driver()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endReason").value("DRIVER_STOPPED"))
                .andExpect(jsonPath("$.amountToCharge").value(4000.00))
                .andExpect(jsonPath("$.paymentStatus").value("CHARGED"));
    }

    @Test
    @DisplayName("Detener la sesión de otro conductor es el 404 genérico")
    void stopForeignSessionIs404() throws Exception {
        when(sessionService.stop("sess-ajena", DRIVER_ID))
                .thenThrow(new ChargingSessionNotFoundException("sess-ajena"));

        mockMvc.perform(post("/api/sessions/sess-ajena/stop").header(HttpHeaders.AUTHORIZATION, driver()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL));
    }

    @Test
    @DisplayName("Detener sin sesión es 401, y con rol de operador no se llega al servicio")
    void stopNeedsDriver() throws Exception {
        mockMvc.perform(post("/api/sessions/sess-1/stop")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/sessions/sess-1/stop")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 3L, "CPO")))
                .andExpect(status().is4xxClientError());
        verify(sessionService, never()).stop(any(), any());
    }

    @Test
    @DisplayName("GET /api/sessions/active: 200 con la sesión si hay una, 204 si no")
    void activeSession() throws Exception {
        when(sessionService.findActiveSession(DRIVER_ID)).thenReturn(Optional.of(completed()));
        mockMvc.perform(get("/api/sessions/active").header(HttpHeaders.AUTHORIZATION, driver()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-1"));

        when(sessionService.findActiveSession(DRIVER_ID)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/sessions/active").header(HttpHeaders.AUTHORIZATION, driver()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/sessions/{id} devuelve la sesión del conductor")
    void getSession() throws Exception {
        when(sessionService.getSession("sess-1", DRIVER_ID)).thenReturn(completed());

        mockMvc.perform(get("/api/sessions/sess-1").header(HttpHeaders.AUTHORIZATION, driver()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kwhDelivered").value(20.0));
    }

    @Test
    @DisplayName("Activación sin código de la terminal: 400 sin llegar al servicio")
    void activationWithoutCodeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, driver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetBatteryPercent\":80}"))
                .andExpect(status().isBadRequest());
        verify(activationService, never()).activate(any(), any());
    }

    @Test
    @DisplayName("Código que no abre el conector: 422 INVALID_ACCESS_CODE, con un texto fijo")
    void invalidCodeIs422() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new InvalidAccessCodeException("El código es de otro conector"));

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, driver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinOrQr\":\"123456\",\"targetBatteryPercent\":80}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"))
                .andExpect(jsonPath("$.detail").value(not(containsString("otro conector"))));
    }

    @Test
    @DisplayName("Demasiados códigos errados: 429 con Retry-After")
    void tooManyAttemptsIs429() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new TooManyAccessCodeAttemptsException(DRIVER_ID, Duration.ofSeconds(240)));

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, driver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinOrQr\":\"123456\",\"targetBatteryPercent\":80}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "240"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ACCESS_CODE_ATTEMPTS"));
    }

    @Test
    @DisplayName("Ya cargando: 409 DRIVER_ALREADY_CHARGING con la sesión activa")
    void alreadyChargingIs409() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new DriverAlreadyChargingException(DRIVER_ID, "sess-viva"));

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, driver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinOrQr\":\"123456\",\"targetBatteryPercent\":80}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_CHARGING"))
                .andExpect(jsonPath("$.activeSessionId").value("sess-viva"));
    }
}
