package com.ecopedia.integration.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.integration.payment.data.JpaPaymentMethodRepository;
import com.ecopedia.integration.payment.data.JpaPendingPaymentRepository;
import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import java.time.Instant;
import java.util.UUID;
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

/**
 * Pruebas de integración para {@code POST /internal/payments/consumption-charges} (RF14, RNF06, ECO-55).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InternalConsumptionChargeControllerTest {

    private static final String SERVICE_TOKEN = "Bearer ecopedia-dev-only-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentMethodRepository cards;

    @MockitoBean
    private PaymentGatewayPort gateway;

    @Autowired
    private JpaPaymentMethodRepository cardTable;

    @Autowired
    private JpaPendingPaymentRepository pendingPaymentTable;

    @AfterEach
    void cleanUp() {
        pendingPaymentTable.deleteAll();
        cardTable.deleteAll();
    }

    private PaymentMethod saveActiveCard(long driverId, CardBrand brand, String lastFour) {
        PaymentMethod card = new PaymentMethod();
        card.setCreatedAt(Instant.now());
        card.setDriverId(driverId);
        card.setBrand(brand);
        card.setLastFour(lastFour);
        card.setGatewayToken("tok_" + UUID.randomUUID());
        card.setExpiryMonth(12);
        card.setExpiryYear(2030);
        card.setActive(true);
        return cards.save(card);
    }

    @Test
    @DisplayName("Sin clave de servicio responde 401 Unauthorized")
    void requiresInternalToken() throws Exception {
        String body =
                """
                {"driverId":10,"amount":1500.00,"sessionId":"sess-1","idempotencyKey":"key-1"}
                """;
        mockMvc.perform(post("/internal/payments/consumption-charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con token válido y pasarela aprobando responde 200 CHARGED")
    void chargesSuccessfullyAndReturnsCharged() throws Exception {
        saveActiveCard(10L, CardBrand.VISA, "4242");

        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new GatewayCharge("gw-99", true, "accredited"));

        String body =
                """
                {"driverId":10,"amount":1500.00,"sessionId":"sess-ok","idempotencyKey":"key-ok"}
                """;
        mockMvc.perform(post("/internal/payments/consumption-charges")
                        .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHARGED"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.sessionId").value("sess-ok"))
                .andExpect(jsonPath("$.gatewayPaymentId").value("gw-99"))
                .andExpect(jsonPath("$.pendingPaymentId").doesNotExist());
    }

    @Test
    @DisplayName("Con token válido y pasarela caída registra deuda y responde 200 DEBT_RECORDED")
    void gatewayUnavailableRecordsDebtAndReturnsDebtRecorded() throws Exception {
        saveActiveCard(11L, CardBrand.MASTERCARD, "5555");

        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new PaymentGatewayUnavailableException(new RuntimeException("timeout")));

        String body =
                """
                {"driverId":11,"amount":2200.00,"sessionId":"sess-down","idempotencyKey":"key-down"}
                """;
        mockMvc.perform(post("/internal/payments/consumption-charges")
                        .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEBT_RECORDED"))
                .andExpect(jsonPath("$.amount").value(2200.00))
                .andExpect(jsonPath("$.sessionId").value("sess-down"))
                .andExpect(jsonPath("$.pendingPaymentId").isNumber());
    }
}
