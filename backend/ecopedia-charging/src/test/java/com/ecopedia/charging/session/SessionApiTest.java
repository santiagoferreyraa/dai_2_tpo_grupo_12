package com.ecopedia.charging.session;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.TestTokens;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingStateConflictException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.DriverAlreadyBookedException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.SessionNotFoundException;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.SessionStopResult;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.web.dto.ActivationHttpRequest;
import com.ecopedia.charging.session.web.dto.StopSessionRequest;
import com.ecopedia.charging.web.ApiProblems;
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
    @DisplayName("Cargador físico fuera de servicio responde 503 con code CHARGER_UNAVAILABLE y texto fijo")
    void chargerUnavailable_returnsFixedDetail() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new PhysicalChargerUnavailableException("tok_123 / body del simulador"));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHARGER_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(ApiProblems.CHARGER_UNAVAILABLE_DETAIL))
                .andExpect(content().string(not(containsString("tok_123"))))
                .andExpect(content().string(not(containsString("body del simulador"))));
    }

    @Test
    @DisplayName("Cargador físico rechaza encendido responde 409 con code CHARGER_REJECTED y texto fijo")
    void chargerRejected_returnsFixedDetail() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new PhysicalChargerRejectedException("tok_123 / body del simulador"));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGER_REJECTED"))
                .andExpect(jsonPath("$.detail").value(ApiProblems.CHARGER_REJECTED_DETAIL))
                .andExpect(content().string(not(containsString("tok_123"))))
                .andExpect(content().string(not(containsString("body del simulador"))));
    }

    @Test
    @DisplayName("Conector inexistente responde 404 genérico sin code ni id")
    void connectorNotFound_returnsGeneric404() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new ConnectorNotFoundException(CONNECTOR_ID));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("Reserva inexistente o ajena devuelve exactamente el mismo cuerpo 404 que conector inexistente")
    void bookingNotFound_returnsSameBodyAsConnectorNotFound() throws Exception {
        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, null);
        String payload = objectMapper.writeValueAsString(request);
        String token = TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR");

        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new ConnectorNotFoundException(CONNECTOR_ID));
        String connectorBody = mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new BookingNotFoundException(10L));
        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(content().json(connectorBody));
    }

    @Test
    @DisplayName("Pagos o pasarela no disponible responde 503 con code PAYMENT_CHECK_UNAVAILABLE")
    void paymentMethodsUnavailableReturnsServiceUnavailable() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new PaymentMethodsUnavailableException(new RuntimeException("timeout")));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_CHECK_UNAVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/sessions/activation walk-in (bookingId = null): responde 201 Created y activa sesión")
    void activateWalkInSuccessfully() throws Exception {
        Instant now = Instant.parse("2026-11-02T15:00:00Z");
        ActivationResult result = new ActivationResult(
                "session-walkin-1",
                55L,
                CONNECTOR_ID,
                DRIVER_ID,
                80,
                new BigDecimal("50.00"),
                SessionStatus.ACTIVE,
                now);

        when(activationService.activate(any(ActivationRequest.class), any())).thenReturn(result);

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, "QR-WALKIN");

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("session-walkin-1"))
                .andExpect(jsonPath("$.bookingId").value(55))
                .andExpect(jsonPath("$.connectorId").value(CONNECTOR_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Conductor con otra carga activa responde 409 Conflict con code DRIVER_ALREADY_BOOKED")
    void driverAlreadyBookedReturnsConflict() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(DriverAlreadyBookedException.charging(10L, DRIVER_ID));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_BOOKED"));
    }

    @Test
    @DisplayName("POST /api/sessions/{sessionId}/stop exitoso: responde 200 OK con resumen de liquidación")
    void stopSessionSuccessfully() throws Exception {
        Instant startedAt = Instant.parse("2026-11-02T15:00:00Z");
        Instant stoppedAt = Instant.parse("2026-11-02T15:30:00Z");
        SessionStopResult result = new SessionStopResult(
                "session-stop-1",
                10L,
                CONNECTOR_ID,
                SessionStatus.COMPLETED,
                startedAt,
                stoppedAt,
                new BigDecimal("18.500"),
                new BigDecimal("5000.00"),
                new BigDecimal("7200.00"),
                new BigDecimal("7200.00"),
                new BigDecimal("2200.00"),
                ConsumptionPaymentStatus.CHARGED);

        when(activationService.stopSession("session-stop-1", DRIVER_ID, new BigDecimal("18.500")))
                .thenReturn(result);

        mockMvc.perform(post("/api/sessions/session-stop-1/stop")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StopSessionRequest(new BigDecimal("18.500")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-stop-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.kwhDelivered").value(18.500))
                .andExpect(jsonPath("$.realCost").value(7200.00))
                .andExpect(jsonPath("$.additionalCharge").value(2200.00))
                .andExpect(jsonPath("$.paymentStatus").value("CHARGED"));
    }

    @Test
    @DisplayName("POST /api/sessions/{sessionId}/stop sobre sesión ajena o inexistente responde 404 opaco")
    void stopForeignOrMissingSessionReturnsOpaqueNotFound() throws Exception {
        when(activationService.stopSession("session-foreign", DRIVER_ID, null))
                .thenThrow(new SessionNotFoundException("session-foreign"));

        mockMvc.perform(post("/api/sessions/session-foreign/stop")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    /**
     * Patrón State: habilitar una reserva que ya no está confirmada es una transición inválida. 409
     * {@code BOOKING_STATE_CONFLICT}, el mismo texto que en Reservas, y ni el id ni el estado de la
     * reserva en la respuesta (directiva 7). Hasta el 10/11 era un 400 con "La reserva N no está
     * confirmada".
     */
    @Test
    @DisplayName("Habilitar una reserva que ya no está confirmada responde 409 BOOKING_STATE_CONFLICT sin pistas")
    void activatingUnconfirmedBookingReturnsStateConflict() throws Exception {
        when(activationService.activate(any(ActivationRequest.class), any()))
                .thenThrow(new BookingStateConflictException(10L, "en CANCELLED", "la habilitación de la carga"));

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, null);

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BookingStateConflictException.CODE))
                .andExpect(jsonPath("$.detail").value(ApiProblems.STATE_CONFLICT_DETAIL))
                .andExpect(content().string(not(containsString("CANCELLED"))));
    }

    /** Cerrar la carga de una reserva que ya se cerró: antes del 10/11 caía en el manejador general (500). */
    @Test
    @DisplayName("Cerrar la carga de una reserva ya cerrada responde 409 BOOKING_STATE_CONFLICT, no 500")
    void stoppingOnClosedBookingReturnsStateConflict() throws Exception {
        when(activationService.stopSession("session-closed", DRIVER_ID, null))
                .thenThrow(new BookingStateConflictException(10L, "en COMPLETED", "el cierre de la carga"));

        mockMvc.perform(post("/api/sessions/session-closed/stop")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BookingStateConflictException.CODE))
                .andExpect(jsonPath("$.detail").value(ApiProblems.STATE_CONFLICT_DETAIL));
    }

    @Test
    @DisplayName("POST /api/sessions/{sessionId}/stop sin autenticación responde 401 Unauthorized")
    void stopUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/sessions/session-stop-1/stop")).andExpect(status().isUnauthorized());
    }
}
