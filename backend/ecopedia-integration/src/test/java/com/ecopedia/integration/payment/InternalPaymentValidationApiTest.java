package com.ecopedia.integration.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.integration.payment.data.JpaPaymentMethodRepository;
import com.ecopedia.integration.payment.data.JpaPendingPaymentRepository;
import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.CardValidation;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas de integración para el endpoint interno de validación de tarjetas y deuda (RF11, RF14, ECO-54).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InternalPaymentValidationApiTest {

    private static final String SERVICE_TOKEN = "Bearer ecopedia-dev-only-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentMethodRepository cards;

    @Autowired
    private PendingPaymentRepository pendingPayments;

    @MockitoBean
    private PaymentGatewayPort gateway;

    @Autowired
    private JpaPaymentMethodRepository cardTable;

    @Autowired
    private JpaPendingPaymentRepository pendingPaymentTable;

    @Value("${ecopedia.jwt.secret}")
    private String jwtSecret;

    @AfterEach
    void cleanUp() {
        pendingPaymentTable.deleteAll();
        cardTable.deleteAll();
    }

    private PaymentMethod cardOf(long driverId, String lastFour) {
        PaymentMethod card = new PaymentMethod();
        card.setCreatedAt(Instant.now());
        card.setDriverId(driverId);
        card.setBrand(CardBrand.VISA);
        card.setLastFour(lastFour);
        card.setGatewayToken("tok_" + UUID.randomUUID());
        card.setExpiryMonth(12);
        card.setExpiryYear(2099);
        card.setActive(true);
        return cards.save(card);
    }

    private PendingPayment debtOf(long driverId, BigDecimal amount) {
        PendingPayment debt = new PendingPayment(
                driverId, null, amount, "Consumo de sesión no saldado", "session-ref-999", Instant.now());
        return pendingPayments.save(debt);
    }

    private ResultActions validate(String authorization, Long driverId, Long cardId) throws Exception {
        String body = cardId != null
                ? "{\"driverId\":" + driverId + ",\"paymentMethodId\":" + cardId + "}"
                : "{\"driverId\":" + driverId + "}";
        var request = post("/internal/payments/validations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("Sin la clave de servicio responde 401 con INVALID_SERVICE_TOKEN")
    void requiresTheServiceToken() throws Exception {
        PaymentMethod card = cardOf(401L, "4242");
        String driverToken = "Bearer "
                + Jwts.builder()
                        .subject("401")
                        .claim("role", "CONDUCTOR")
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + 60_000))
                        .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .compact();

        validate(null, 401L, card.getId())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));

        validate(driverToken, 401L, card.getId())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));

        verify(gateway, never()).validate(any());
    }

    @Test
    @DisplayName("Tarjeta válida y sin deuda responde 200 OK con valid true")
    void validCardAndNoDebtReturnsOk() throws Exception {
        PaymentMethod card = cardOf(402L, "4242");
        when(gateway.validate(eq(card.getGatewayToken()))).thenReturn(new CardValidation(true, "chargeable"));

        validate(SERVICE_TOKEN, 402L, card.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value("NONE"));

        verify(gateway).validate(card.getGatewayToken());
    }

    @Test
    @DisplayName("Conductor con deuda impaga responde 422 DRIVER_HAS_DEBT y no llama a la pasarela")
    void driverWithDebtReturns422() throws Exception {
        PaymentMethod card = cardOf(403L, "4242");
        debtOf(403L, new BigDecimal("1500.00"));

        validate(SERVICE_TOKEN, 403L, card.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRIVER_HAS_DEBT"));

        verify(gateway, never()).validate(anyString());
    }

    @Test
    @DisplayName("Tarjeta declinada por la pasarela responde 422 CARD_DECLINED")
    void gatewayDeclinedReturns422() throws Exception {
        PaymentMethod card = cardOf(404L, "0002");
        when(gateway.validate(eq(card.getGatewayToken())))
                .thenReturn(new CardValidation(false, "cc_rejected_insufficient_amount"));

        validate(SERVICE_TOKEN, 404L, card.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_DECLINED"));

        verify(gateway).validate(card.getGatewayToken());
    }

    @Test
    @DisplayName("Conductor sin tarjetas activas responde 422 NO_PAYMENT_METHOD")
    void noPaymentMethodReturns422() throws Exception {
        validate(SERVICE_TOKEN, 405L, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_PAYMENT_METHOD"));

        verify(gateway, never()).validate(anyString());
    }

    @Test
    @DisplayName("Pasarela de pagos no disponible o caída responde 503 PAYMENT_GATEWAY_UNAVAILABLE")
    void gatewayDownReturns503() throws Exception {
        PaymentMethod card = cardOf(406L, "4242");
        when(gateway.validate(eq(card.getGatewayToken())))
                .thenThrow(new PaymentGatewayUnavailableException(new RuntimeException("timeout")));

        validate(SERVICE_TOKEN, 406L, card.getId())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
    }
}
