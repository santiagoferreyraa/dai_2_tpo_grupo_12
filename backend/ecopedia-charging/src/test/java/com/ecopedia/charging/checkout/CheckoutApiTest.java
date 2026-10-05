package com.ecopedia.charging.checkout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.TestTokens;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import com.ecopedia.charging.checkout.web.dto.CheckoutBookingHttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CheckoutApiTest {

    private static final long DRIVER = 55L;
    private static final long CONNECTOR = 12L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingService bookingService;

    @MockitoBean
    private ConnectorCatalog connectorCatalog;

    @MockitoBean
    private TariffDirectory tariffDirectory;

    @MockitoBean
    private PaymentDirectory paymentDirectory;

    @Value("${ecopedia.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        when(connectorCatalog.findConnector(anyLong()))
                .thenReturn(Optional.of(new ConnectorSnapshot(CONNECTOR, 1L, "AVAILABLE")));
        when(tariffDirectory.calculateDeposit(anyLong())).thenReturn(new BigDecimal("1200.00"));
        when(paymentDirectory.hasUsablePaymentMethod(anyLong(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("POST /api/checkout/booking orquesta la confirmación completa y retorna 201 con la reserva y seña")
    void successfulCheckoutOverHttp() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(Duration.ofHours(1));
        Hold hold = bookingService.startHold(CONNECTOR, new TimeWindow(start, end), DRIVER);

        CheckoutBookingHttpRequest request = new CheckoutBookingHttpRequest(hold.id(), 1L, true);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNumber())
                .andExpect(jsonPath("$.connectorId").value(CONNECTOR))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.depositAmount").value(1200.00))
                .andExpect(jsonPath("$.paymentSummary").exists());
    }

    @Test
    @DisplayName("POST /api/checkout/booking rechaza con 400 si no se aceptó la tolerancia de 15 minutos")
    void rejectsWhenGraceNotAcceptedOverHttp() throws Exception {
        CheckoutBookingHttpRequest request = new CheckoutBookingHttpRequest(UUID.randomUUID(), 1L, false);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Debe aceptar la tolerancia de 15 minutos (RF09) para confirmar la reserva"))
                .andExpect(jsonPath("$.code").value("GRACE_PERIOD_NOT_ACCEPTED"));
    }

    @Test
    @DisplayName("POST /api/checkout/booking rechaza con 400 si el conductor no tiene medio de pago")
    void rejectsWhenNoPaymentMethodOverHttp() throws Exception {
        when(paymentDirectory.hasUsablePaymentMethod(anyLong(), any())).thenReturn(false);

        Instant start = Instant.now().plus(Duration.ofHours(5)).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(Duration.ofHours(1));
        Hold hold = bookingService.startHold(CONNECTOR, new TimeWindow(start, end), DRIVER);

        CheckoutBookingHttpRequest request = new CheckoutBookingHttpRequest(hold.id(), null, true);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Se requiere al menos un medio de pago registrado para confirmar la reserva (RF02)"))
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_REQUIRED"));
    }

    /*
     * Pagos caído no habilita: antes se asumía que el conductor tenía tarjeta, y con el 8083 apagado se
     * reservaba sin ninguna. Y la retención sigue en pie: vuelto Pagos, la misma retención se confirma.
     */
    @Test
    @DisplayName("POST /api/checkout/booking responde 503 si Pagos no contesta, y la retención sigue en pie")
    void rejectsWhenPaymentsIsDownAndKeepsTheHold() throws Exception {
        when(paymentDirectory.hasUsablePaymentMethod(anyLong(), any()))
                .thenThrow(new PaymentMethodsUnavailableException(new RuntimeException("Connection refused")));

        Instant start = Instant.now().plus(Duration.ofHours(8)).truncatedTo(ChronoUnit.SECONDS);
        Hold hold = bookingService.startHold(CONNECTOR, new TimeWindow(start, start.plus(Duration.ofHours(1))), DRIVER);
        String body = objectMapper.writeValueAsString(new CheckoutBookingHttpRequest(hold.id(), null, true));

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_CHECK_UNAVAILABLE"));

        // doReturn y no when(): when() llamaría al mock, que todavía está armado para tirar.
        doReturn(true).when(paymentDirectory).hasUsablePaymentMethod(anyLong(), any());

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/checkout/booking rechaza con 403 si se invoca sin token de autenticación")
    void rejectsAnonymous() throws Exception {
        CheckoutBookingHttpRequest request = new CheckoutBookingHttpRequest(UUID.randomUUID(), 1L, true);

        mockMvc.perform(post("/api/checkout/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/checkout/booking rechaza con 403 si el rol no es CONDUCTOR (ej. CPO)")
    void rejectsNonDriver() throws Exception {
        CheckoutBookingHttpRequest request = new CheckoutBookingHttpRequest(UUID.randomUUID(), 1L, true);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 99L, "CPO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
