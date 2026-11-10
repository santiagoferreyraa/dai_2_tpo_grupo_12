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
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.DriverAlreadyBookedException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.web.dto.ActivationHttpRequest;
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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, "123456");

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
        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, "123456");

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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, "123456");

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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, "123456");

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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, "123456");

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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, "123456");

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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, "123456");

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
        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, 10L, 80, "123456");
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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, "123456");

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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, "ecopedia:connector:123456");

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

        ActivationHttpRequest request = new ActivationHttpRequest(CONNECTOR_ID, null, 80, "123456");

        mockMvc.perform(post("/api/sessions/activation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER_ID, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_BOOKED"));
    }
}
