package com.ecopedia.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.integration.payment.data.JpaPaymentMethodRepository;
import com.ecopedia.integration.payment.data.JpaPendingPaymentRepository;
import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.DebtCollectionService;
import com.ecopedia.integration.payment.domain.DriverBookingsPort;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La deuda del conductor de punta a punta contra la base (ECO-62): verla y pagarla desde el perfil, el
 * reclamo que impide dos cobros a la vez, la cuenta marcada al tercer reintento y la consulta interna
 * del checkout. El reloj del barrido está apagado: la prueba lo llama a mano.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "ecopedia.scheduling.enabled=false")
class DebtApiTest {

    private static final long DRIVER = 31L;
    private static final long OTHER_DRIVER = 32L;
    private static final String SERVICE_TOKEN = "Bearer ecopedia-dev-only-internal-token";
    private static final Duration INTERVAL = Duration.ofMinutes(20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaPendingPaymentRepository debtTable;

    @Autowired
    private JpaPaymentMethodRepository cardTable;

    @Autowired
    private PendingPaymentRepository debts;

    @Autowired
    private PaymentMethodRepository cards;

    @Autowired
    private DebtCollectionService debtCollection;

    @MockitoBean
    private PaymentGatewayPort gateway;

    @MockitoBean
    private DriverBookingsPort driverBookings;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @AfterEach
    void cleanUp() {
        debtTable.deleteAll();
        cardTable.deleteAll();
    }

    private String driver(long userId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "usuario" + userId + "@ecopedia.test")
                .claim("role", "CONDUCTOR")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }

    private PaymentMethod card(long driverId) {
        PaymentMethod card = new PaymentMethod();
        card.setCreatedAt(Instant.now());
        card.setDriverId(driverId);
        card.setBrand(CardBrand.VISA);
        card.setLastFour("4242");
        card.setGatewayToken("tok_" + UUID.randomUUID());
        card.setExpiryMonth(12);
        card.setExpiryYear(2099);
        card.setActive(true);
        return cards.save(card);
    }

    /** Una deuda de una carga que se cerró hace {@code minutesAgo} minutos, con su primer reintento agendado. */
    private PendingPayment debt(long driverId, long minutesAgo) {
        PendingPayment debt = new PendingPayment(
                driverId,
                null,
                new BigDecimal("4320.00"),
                "rechazada",
                "session-" + UUID.randomUUID(),
                Instant.now().minus(Duration.ofMinutes(minutesAgo)));
        debt.scheduleFirstRetry(INTERVAL);
        return debt;
    }

    private void gatewayAnswers(boolean approved) {
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new GatewayCharge(
                        "pay-" + UUID.randomUUID(), approved, approved ? "accredited" : "cc_rejected"));
    }

    @Test
    @DisplayName("El conductor ve solo sus deudas abiertas, sin el motivo técnico")
    void listsOnlyTheDriversOpenDebts() throws Exception {
        PendingPayment mine = debts.save(debt(DRIVER, 5));
        debts.save(debt(OTHER_DRIVER, 5));
        PendingPayment settled = debt(DRIVER, 5);
        settled.markSettled(Instant.now());
        debts.save(settled);

        mockMvc.perform(get("/api/payment-debts").header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()))
                .andExpect(jsonPath("$[0].amount").value(4320.00))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].nextAttemptAt").exists())
                .andExpect(jsonPath("$[0].reason").doesNotExist());
    }

    @Test
    @DisplayName("Pagarla desde el perfil la salda: deja de aparecer y el checkout ya no la ve")
    void payingFromTheProfileSettlesIt() throws Exception {
        PaymentMethod card = card(DRIVER);
        PendingPayment owed = debts.save(debt(DRIVER, 5));
        gatewayAnswers(true);

        mockMvc.perform(get("/internal/payments/drivers/" + DRIVER + "/debt")
                        .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN))
                .andExpect(jsonPath("$.hasDebt").value(true));

        mockMvc.perform(post("/api/payment-debts/" + owed.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .header("Idempotency-Key", "pay-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\": " + card.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mockMvc.perform(get("/api/payment-debts").header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/internal/payments/drivers/" + DRIVER + "/debt")
                        .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN))
                .andExpect(jsonPath("$.hasDebt").value(false));
        assertThat(debts.findById(owed.getId()).orElseThrow().getClaimedUntil()).isNull();
    }

    @Test
    @DisplayName("Tarjeta rechazada al pagar: 402 CARD_DECLINED y la deuda sigue abierta")
    void declinedPaymentKeepsTheDebt() throws Exception {
        card(DRIVER);
        PendingPayment owed = debts.save(debt(DRIVER, 5));
        gatewayAnswers(false);

        mockMvc.perform(post("/api/payment-debts/" + owed.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .header("Idempotency-Key", "pay-key-0002"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("CARD_DECLINED"));

        PendingPayment after = debts.findById(owed.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PendingPaymentStatus.PENDING);
        assertThat(after.getRetryCount()).isZero();
        assertThat(after.getClaimedUntil()).isNull();
    }

    @Test
    @DisplayName("Sin Idempotency-Key: 400 IDEMPOTENCY_KEY_REQUIRED, sin cobrar")
    void requiresAnIdempotencyKey() throws Exception {
        PendingPayment owed = debts.save(debt(DRIVER, 5));

        mockMvc.perform(post("/api/payment-debts/" + owed.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("La deuda de otro da el mismo 404 que una que no existe")
    void anotherDriversDebtIsNotFound() throws Exception {
        PendingPayment others = debts.save(debt(OTHER_DRIVER, 5));

        String foreign = mockMvc.perform(post("/api/payment-debts/" + others.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .header("Idempotency-Key", "pay-key-0003"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missing = mockMvc.perform(post("/api/payment-debts/999999/payment")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .header("Idempotency-Key", "pay-key-0004"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(foreign.replace(String.valueOf(others.getId()), "X")).isEqualTo(missing.replace("999999", "X"));
        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Reclamada por el barrido: el pago desde el perfil da 409 y no cobra; el reclamo es único")
    void claimKeepsASingleChargeAtATime() throws Exception {
        card(DRIVER);
        PendingPayment owed = debts.save(debt(DRIVER, 5));
        Instant now = Instant.now();
        assertThat(debts.claim(owed.getId(), now, now.plus(Duration.ofMinutes(2))))
                .isTrue();
        assertThat(debts.claim(owed.getId(), now, now.plus(Duration.ofMinutes(2))))
                .isFalse();

        mockMvc.perform(post("/api/payment-debts/" + owed.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .header("Idempotency-Key", "pay-key-0005"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEBT_PAYMENT_IN_PROGRESS"));
        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());

        // Un reclamo vencido (el proceso se cayó con la deuda reclamada) se puede volver a tomar.
        assertThat(debts.claim(owed.getId(), now.plus(Duration.ofMinutes(3)), now.plus(Duration.ofMinutes(5))))
                .isTrue();
    }

    @Test
    @DisplayName(
            "Al tercer reintento rechazado la deuda vence, se piden anular las reservas y el checkout la sigue viendo")
    void thirdRejectedRetryDefaultsAgainstTheDatabase() throws Exception {
        card(DRIVER);
        PendingPayment owed = debt(DRIVER, 120);
        // Dos reintentos ya fallaron, hace rato: el tercero está vencido.
        owed.recordFailedRetry(Instant.now().minus(Duration.ofMinutes(100)), null, "rechazada", 3, INTERVAL);
        owed.recordFailedRetry(Instant.now().minus(Duration.ofMinutes(80)), null, "rechazada", 3, INTERVAL);
        owed = debts.save(owed);
        gatewayAnswers(false);
        when(driverBookings.cancelConfirmedBookingsForDebt(DRIVER)).thenReturn(2);

        debtCollection.retryDueDebts();

        PendingPayment after = debts.findById(owed.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PendingPaymentStatus.DEFAULTED);
        assertThat(after.getRetryCount()).isEqualTo(3);
        assertThat(after.isBookingsCancelled()).isTrue();
        assertThat(after.getClaimedUntil()).isNull();
        verify(driverBookings).cancelConfirmedBookingsForDebt(DRIVER);

        mockMvc.perform(get("/internal/payments/drivers/" + DRIVER + "/debt")
                        .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN))
                .andExpect(jsonPath("$.hasDebt").value(true));
        mockMvc.perform(get("/api/payment-debts").header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(jsonPath("$[0].status").value("DEFAULTED"))
                .andExpect(jsonPath("$[0].nextAttemptAt").doesNotExist());
    }

    @Test
    @DisplayName("Un reintento que todavía no tocó no se cobra")
    void doesNotRetryBeforeItsTime() {
        card(DRIVER);
        PendingPayment owed = debts.save(debt(DRIVER, 5)); // el primer reintento es en 15 minutos

        debtCollection.retryDueDebts();

        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
        assertThat(debts.findById(owed.getId()).orElseThrow().getRetryCount()).isZero();
    }

    @Test
    @DisplayName("La consulta interna de deuda pide la clave de servicio")
    void internalDebtCheckRequiresTheServiceToken() throws Exception {
        mockMvc.perform(get("/internal/payments/drivers/" + DRIVER + "/debt")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));
    }
}
