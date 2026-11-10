package com.ecopedia.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * El cobro del consumo al cerrar una sesión de carga (RF14, RNF06, ECO-44): cobra, y si no puede,
 * anota la deuda en vez de fallar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InternalConsumptionApiTest {

    private static final String SERVICE_TOKEN = "Bearer ecopedia-dev-only-internal-token";
    private static final long DRIVER_ID = 7L;

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

    @AfterEach
    void cleanUp() {
        pendingPaymentTable.deleteAll();
        cardTable.deleteAll();
    }

    private void cardOf(long driverId) {
        PaymentMethod card = new PaymentMethod();
        card.setCreatedAt(Instant.now());
        card.setDriverId(driverId);
        card.setBrand(CardBrand.VISA);
        card.setLastFour("4242");
        card.setGatewayToken("tok_" + UUID.randomUUID());
        card.setExpiryMonth(12);
        card.setExpiryYear(2099);
        card.setActive(true);
        cards.save(card);
    }

    private ResultActions charge(String reference) throws Exception {
        return mockMvc.perform(post("/internal/payments/consumptions")
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":%d,\"amount\":1250.50,\"reference\":\"%s\"}".formatted(DRIVER_ID, reference)));
    }

    @Test
    @DisplayName("La pasarela aprueba: CHARGED, con la sesión como base de la clave de idempotencia")
    void approvedChargeIsCharged() throws Exception {
        cardOf(DRIVER_ID);
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new GatewayCharge("pay_1", true, "accredited"));

        charge("session-abc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHARGED"))
                .andExpect(jsonPath("$.pendingPaymentId").doesNotExist());

        verify(gateway)
                .charge(
                        anyString(),
                        eq(new BigDecimal("1250.50")),
                        anyString(),
                        eq("session-abc"),
                        eq("consumption-session-abc"));
        assertThat(pendingPayments.existsByDriverIdAndStatus(DRIVER_ID, PendingPaymentStatus.PENDING))
                .isFalse();
    }

    @Test
    @DisplayName("La pasarela rechaza: 200 con DEBT_REGISTERED, y el conductor queda con deuda")
    void declinedChargeBecomesDebt() throws Exception {
        cardOf(DRIVER_ID);
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new GatewayCharge(null, false, "cc_rejected_insufficient_amount"));

        charge("session-def")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEBT_REGISTERED"))
                .andExpect(jsonPath("$.pendingPaymentId").isNumber());

        assertThat(pendingPayments.existsByDriverIdAndStatus(DRIVER_ID, PendingPaymentStatus.PENDING))
                .isTrue();
    }

    @Test
    @DisplayName("La pasarela no contesta: también queda como deuda, no como error")
    void gatewayDownBecomesDebt() throws Exception {
        cardOf(DRIVER_ID);
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new PaymentGatewayUnavailableException(new RuntimeException("timeout")));

        charge("session-ghi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEBT_REGISTERED"));
    }

    @Test
    @DisplayName("Sin tarjeta usable: deuda, sin preguntarle a la pasarela")
    void noCardBecomesDebtWithoutGateway() throws Exception {
        charge("session-jkl")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEBT_REGISTERED"));

        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Repetir el pedido de una sesión que ya quedó como deuda no anota otra ni vuelve a cobrar")
    void repeatedDebtIsIdempotent() throws Exception {
        charge("session-mno").andExpect(jsonPath("$.status").value("DEBT_REGISTERED"));
        charge("session-mno").andExpect(jsonPath("$.status").value("DEBT_REGISTERED"));

        assertThat(pendingPayments.findByDriverIdAndStatus(DRIVER_ID, PendingPaymentStatus.PENDING))
                .hasSize(1);
        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Sin la clave de servicio no se cobra nada")
    void needsServiceToken() throws Exception {
        mockMvc.perform(post("/internal/payments/consumptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":7,\"amount\":10,\"reference\":\"session-x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
