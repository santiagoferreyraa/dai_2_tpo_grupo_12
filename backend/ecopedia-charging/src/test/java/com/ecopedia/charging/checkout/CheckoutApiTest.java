package com.ecopedia.charging.checkout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.ecopedia.charging.checkout.domain.CardDeclinedException;
import com.ecopedia.charging.checkout.domain.DepositCharge;
import com.ecopedia.charging.checkout.domain.DepositGateway;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
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

    @MockitoBean
    private DepositGateway depositGateway;

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Value("${ecopedia.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        when(connectorCatalog.findConnector(anyLong()))
                .thenReturn(Optional.of(new ConnectorSnapshot(CONNECTOR, 1L, "AVAILABLE")));
        when(tariffDirectory.calculateDeposit(anyLong())).thenReturn(new BigDecimal("1200.00"));
        when(paymentDirectory.hasUsablePaymentMethod(anyLong(), any())).thenReturn(true);
        when(depositGateway.charge(anyLong(), any(), any(), any(), any())).thenReturn(new DepositCharge(9L, "4242"));
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
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNumber())
                .andExpect(jsonPath("$.connectorId").value(CONNECTOR))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.depositAmount").value(1200.00))
                .andExpect(jsonPath("$.depositStatus").value("CHARGED"))
                .andExpect(jsonPath("$.cardLastFour").value("4242"))
                .andExpect(jsonPath("$.depositRefundableUntil")
                        .value(start.minus(Duration.ofHours(5)).toString()))
                .andExpect(jsonPath("$.paymentSummary").exists());
    }

    @Test
    @DisplayName("POST /api/checkout/booking rechaza con 400 si no se aceptó la tolerancia de 15 minutos")
    void rejectsWhenGraceNotAcceptedOverHttp() throws Exception {
        CheckoutBookingHttpRequest request = new CheckoutBookingHttpRequest(UUID.randomUUID(), 1L, false);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
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
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
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
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_CHECK_UNAVAILABLE"));

        // doReturn y no when(): when() llamaría al mock, que todavía está armado para tirar.
        doReturn(true).when(paymentDirectory).hasUsablePaymentMethod(anyLong(), any());

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, DRIVER, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
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
    @DisplayName("POST /api/checkout/booking responde 404 si el rol no es CONDUCTOR (ej. CPO)")
    void rejectsNonDriver() throws Exception {
        CheckoutBookingHttpRequest request = new CheckoutBookingHttpRequest(UUID.randomUUID(), 1L, true);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 99L, "CPO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /*
     * ECO-53 y QA-29. Cada prueba usa su propio conductor: el contexto (y la base en memoria) se comparte
     * entre pruebas, y una reserva confirmada en una bloquearía al mismo conductor en la siguiente.
     */

    private Hold holdFor(long driver, long hoursFromNow) {
        Instant start = Instant.now().plus(Duration.ofHours(hoursFromNow)).truncatedTo(ChronoUnit.SECONDS);
        return bookingService.startHold(CONNECTOR, new TimeWindow(start, start.plus(Duration.ofHours(1))), driver);
    }

    @Test
    @DisplayName("POST /api/checkout/booking sin Idempotency-Key responde 400 con IDEMPOTENCY_KEY_REQUIRED y no cobra")
    void rejectsWithoutIdempotencyKey() throws Exception {
        Hold hold = holdFor(201L, 30);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 201L, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutBookingHttpRequest(hold.id(), 1L, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(depositGateway, times(0)).charge(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("El mismo checkout dos veces con la misma clave es una sola reserva y un solo cobro (QA-29)")
    void sameKeyTwiceIsOneBookingAndOneCharge() throws Exception {
        Hold hold = holdFor(202L, 33);
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(new CheckoutBookingHttpRequest(hold.id(), 1L, true));

        String first = mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 202L, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // La retención ya se consumió: sin la clave, esto sería un 404.
        String second = mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 202L, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
        verify(depositGateway, times(1)).charge(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("La misma clave con otra retención responde 422 con IDEMPOTENCY_KEY_REUSED")
    void sameKeyForAnotherHoldIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        Hold first = holdFor(203L, 36);
        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 203L, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutBookingHttpRequest(first.id(), 1L, true))))
                .andExpect(status().isCreated());

        Hold other = holdFor(203L, 40);
        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 203L, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutBookingHttpRequest(other.id(), 1L, true))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("Una tarjeta rechazada responde 402 con CARD_DECLINED, y la retención sigue para probar con otra")
    void declinedCardKeepsTheHold() throws Exception {
        when(depositGateway.charge(anyLong(), any(), any(), any(), any())).thenThrow(new CardDeclinedException());
        Hold hold = holdFor(204L, 43);
        String body = objectMapper.writeValueAsString(new CheckoutBookingHttpRequest(hold.id(), 1L, true));

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 204L, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("CARD_DECLINED"));

        // doReturn y no when(): when() llamaría al mock, que todavía está armado para tirar.
        doReturn(new DepositCharge(10L, "1111")).when(depositGateway).charge(anyLong(), any(), any(), any(), any());

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 204L, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardLastFour").value("1111"));
    }

    @Test
    @DisplayName("Con Tarificación caída responde 503 con PRICING_UNAVAILABLE y no cobra")
    void pricingDownIsServiceUnavailable() throws Exception {
        when(tariffDirectory.calculateDeposit(anyLong()))
                .thenThrow(new PricingUnavailableException(new RuntimeException("Connection refused")));
        Hold hold = holdFor(205L, 46);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 205L, "CONDUCTOR"))
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutBookingHttpRequest(hold.id(), 1L, true))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRICING_UNAVAILABLE"));

        verify(depositGateway, times(0)).charge(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("La retención informa hasta cuándo cancelar devolvería la seña, antes de pagar")
    void holdTellsTheRefundDeadline() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(50)).truncatedTo(ChronoUnit.SECONDS);
        String body = "{\"connectorId\":" + CONNECTOR + ",\"start\":\"" + start + "\",\"end\":\""
                + start.plus(Duration.ofHours(1)) + "\"}";

        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtSecret, 206L, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depositRefundableUntil")
                        .value(start.minus(Duration.ofHours(5)).toString()));
    }
}
