package com.ecopedia.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.integration.payment.data.JpaDepositChargeRepository;
import com.ecopedia.integration.payment.data.JpaPaymentMethodRepository;
import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.DepositChargeRepository;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.GatewayRefund;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * La ruta interna de la seña (ECO-53), con el artefacto entero, la base de verdad y la pasarela
 * reemplazada por el puerto: lo que se prueba es quién entra, qué se guarda y que la clave de
 * idempotencia haga lo que promete. El cliente REST de la pasarela lo prueba
 * {@code PaymentGatewayRestClientTest}.
 *
 * <p>Cada prueba usa su propio conductor y su propia clave: la base en memoria se comparte entre
 * pruebas del mismo contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InternalDepositApiTest {

    private static final String SERVICE_TOKEN = "Bearer ecopedia-dev-only-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentMethodRepository cards;

    @Autowired
    private DepositChargeRepository charges;

    @MockitoBean
    private PaymentGatewayPort gateway;

    @Autowired
    private JpaPaymentMethodRepository cardTable;

    @Autowired
    private JpaDepositChargeRepository chargeTable;

    /** La base en memoria es de todas las clases de test: lo que se cargó acá no tiene que verse en otra. */
    @AfterEach
    void cleanUp() {
        chargeTable.deleteAll();
        cardTable.deleteAll();
    }

    @Value("${ecopedia.jwt.secret}")
    private String jwtSecret;

    private PaymentMethod cardOf(long driverId, String lastFour) {
        return cardOf(driverId, lastFour, Instant.now());
    }

    private PaymentMethod cardOf(long driverId, String lastFour, Instant createdAt) {
        PaymentMethod card = new PaymentMethod();
        card.setCreatedAt(createdAt);
        card.setDriverId(driverId);
        card.setBrand(CardBrand.VISA);
        card.setLastFour(lastFour);
        card.setGatewayToken("tok_" + UUID.randomUUID());
        card.setExpiryMonth(12);
        card.setExpiryYear(2099);
        card.setActive(true);
        return cards.save(card);
    }

    private ResultActions charge(String authorization, long driverId, Long cardId, String key) throws Exception {
        String body = "{\"driverId\":" + driverId + ",\"paymentMethodId\":" + cardId
                + ",\"amount\":8000,\"reference\":\"hold-test\",\"idempotencyKey\":\"" + key + "\"}";
        var request = post("/internal/payments/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
        return mockMvc.perform(request);
    }

    private static String newKey() {
        return "deposit-test-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("Sin la clave de servicio no se cobra, y un token de conductor válido tampoco abre la ruta")
    void requiresTheServiceToken() throws Exception {
        PaymentMethod card = cardOf(301L, "4242");
        String driverToken = "Bearer "
                + Jwts.builder()
                        .subject("301")
                        .claim("role", "CONDUCTOR")
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + 60_000))
                        .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .compact();

        charge(null, 301L, card.getId(), newKey())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));
        charge(driverToken, 301L, card.getId(), newKey()).andExpect(status().isUnauthorized());

        verify(gateway, times(0)).charge(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un cobro aprobado es 201 con la tarjeta, y repetirlo con la misma clave no vuelve a cobrar")
    void approvedAndIdempotent() throws Exception {
        PaymentMethod card = cardOf(302L, "4242");
        String key = newKey();
        when(gateway.charge(eq(card.getGatewayToken()), any(), any(), eq("hold-test"), eq(key)))
                .thenReturn(new GatewayCharge("pay_1", true, "accredited"));

        String first = charge(SERVICE_TOKEN, 302L, card.getId(), key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.cardLastFour").value("4242"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = charge(SERVICE_TOKEN, 302L, card.getId(), key)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(second).isEqualTo(first);
        verify(gateway, times(1)).charge(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin elegir tarjeta, se cobra con la más reciente que sirva")
    void withoutChosenCardUsesTheNewestUsable() throws Exception {
        // Las fechas explícitas: dos altas seguidas pueden caer en el mismo microsegundo.
        cardOf(303L, "1111", Instant.now().minusSeconds(60));
        PaymentMethod newest = cardOf(303L, "4444", Instant.now());
        when(gateway.charge(eq(newest.getGatewayToken()), any(), any(), any(), any()))
                .thenReturn(new GatewayCharge("pay_2", true, "accredited"));

        charge(SERVICE_TOKEN, 303L, null, newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardLastFour").value("4444"));
    }

    @Test
    @DisplayName(
            "Un rechazo de la pasarela es 422 CARD_DECLINED, se guarda, y la misma clave lo repite sin volver a la pasarela")
    void declinedIsStoredAndRepeated() throws Exception {
        PaymentMethod card = cardOf(304L, "0002");
        String key = newKey();
        when(gateway.charge(anyString(), any(), any(), any(), eq(key)))
                .thenReturn(new GatewayCharge("pay_3", false, "cc_rejected_other_reason"));

        charge(SERVICE_TOKEN, 304L, card.getId(), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_DECLINED"));
        charge(SERVICE_TOKEN, 304L, card.getId(), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_DECLINED"));

        verify(gateway, times(1)).charge(anyString(), any(), any(), any(), any());
        assertThat(charges.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    @DisplayName(
            "Con la tarjeta de otro conductor, o sin ninguna, es 422 PAYMENT_METHOD_INVALID y no llega a la pasarela")
    void cardOfSomeoneElseIsInvalid() throws Exception {
        PaymentMethod someoneElses = cardOf(305L, "4242");

        charge(SERVICE_TOKEN, 306L, someoneElses.getId(), newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_INVALID"));
        charge(SERVICE_TOKEN, 307L, null, newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_INVALID"));

        verify(gateway, times(0)).charge(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("La misma clave con otro importe o conductor es 422 IDEMPOTENCY_KEY_REUSED")
    void keyReusedForAnotherCharge() throws Exception {
        PaymentMethod card = cardOf(308L, "4242");
        PaymentMethod other = cardOf(309L, "4242");
        String key = newKey();
        when(gateway.charge(anyString(), any(), any(), any(), eq(key)))
                .thenReturn(new GatewayCharge("pay_4", true, "accredited"));
        charge(SERVICE_TOKEN, 308L, card.getId(), key).andExpect(status().isCreated());

        charge(SERVICE_TOKEN, 309L, other.getId(), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("Con la pasarela caída es 503 y no queda nada guardado: el reintento con la misma clave cobra")
    void gatewayDownStoresNothing() throws Exception {
        PaymentMethod card = cardOf(310L, "4242");
        String key = newKey();
        when(gateway.charge(anyString(), any(), any(), any(), eq(key)))
                .thenThrow(new PaymentGatewayUnavailableException(new RuntimeException("timeout")));

        charge(SERVICE_TOKEN, 310L, card.getId(), key)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));

        assertThat(charges.findByIdempotencyKey(key)).isEmpty();
    }

    @Test
    @DisplayName("Devolver la seña la marca devuelta; devolverla otra vez contesta lo mismo sin volver a la pasarela")
    void refundIsIdempotent() throws Exception {
        PaymentMethod card = cardOf(311L, "4242");
        String key = newKey();
        when(gateway.charge(anyString(), any(), any(), any(), eq(key)))
                .thenReturn(new GatewayCharge("pay_5", true, "accredited"));
        when(gateway.refund(eq("pay_5"), isNull(), eq("refund-" + key)))
                .thenReturn(new GatewayRefund("ref_1", "pay_5", new BigDecimal("8000")));
        JsonNode charged = objectMapper.readTree(charge(SERVICE_TOKEN, 311L, card.getId(), key)
                .andReturn()
                .getResponse()
                .getContentAsString());
        long chargeId = charged.get("chargeId").asLong();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/internal/payments/deposits/{id}/refund", chargeId)
                            .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REFUNDED"));
        }

        verify(gateway, times(1)).refund(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Devolver un cobro rechazado es 409, y uno que no existe, 404")
    void refundOfDeclinedOrUnknown() throws Exception {
        PaymentMethod card = cardOf(312L, "0002");
        String key = newKey();
        when(gateway.charge(anyString(), any(), any(), any(), eq(key)))
                .thenReturn(new GatewayCharge("pay_6", false, "cc_rejected_other_reason"));
        charge(SERVICE_TOKEN, 312L, card.getId(), key);
        long declinedId = charges.findByIdempotencyKey(key).orElseThrow().getId();

        mockMvc.perform(post("/internal/payments/deposits/{id}/refund", declinedId)
                        .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPOSIT_NOT_REFUNDABLE"));
        mockMvc.perform(post("/internal/payments/deposits/{id}/refund", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPOSIT_CHARGE_NOT_FOUND"));
    }
}
