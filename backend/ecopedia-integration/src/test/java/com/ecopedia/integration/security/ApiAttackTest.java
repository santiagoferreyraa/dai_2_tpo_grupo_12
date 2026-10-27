package com.ecopedia.integration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.integration.payment.data.JpaPaymentMethodRepository;
import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Pruebas de ataque sobre los medios de pago (QA-07, ECO-157): JWT hostil, mass assignment e
 * inyección.
 *
 * <p>Es el artefacto con los datos más sensibles del sistema: una sesión falsificada acá deja ver o
 * borrar tarjetas ajenas. Por eso el caso alterado es cambiar el {@code sub} al de otro conductor.
 *
 * <p><b>El rechazo hoy es 403, no 401</b>: ver DEF-02 y {@link #REJECTED_AS_ANONYMOUS}.
 *
 * <p><b>La pasarela se reemplaza por un mock</b>, como en {@code PaymentMethodApiTest}: es otro
 * proceso (simulation/payment-gateway). El mock contesta lo que contesta la de verdad para lo que
 * usan estos tests: tokeniza un número de solo dígitos y rechaza el resto con el mensaje de
 * {@code PaymentGatewayRestClient}. El rechazo real del número con SQL lo prueba la colección de
 * Newman contra el simulador, y la validación en sí, {@code PaymentGatewayApiTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiAttackTest {

    /** Lo que recibe hoy un pedido sin sesión válida. Los criterios piden 401: DEF-02 / ECO-183. */
    private static final int REJECTED_AS_ANONYMOUS = 403;

    private static final String VISA = "4111111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaPaymentMethodRepository paymentMethodRepository;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @MockitoBean
    private PaymentGatewayPort paymentGateway;

    @BeforeEach
    void gatewayTokenizesOnlyDigits() {
        when(paymentGateway.tokenize(any())).thenAnswer(call -> {
            CardData card = call.getArgument(0);
            if (!card.number().matches("\\d+")) {
                throw new IllegalArgumentException(
                        "El número de tarjeta no es válido. Revisá que esté completo y tenga solo dígitos");
            }
            return new TokenizedCard(CardBrand.VISA, card.number().substring(card.number().length() - 4),
                    "tok_" + UUID.randomUUID());
        });
    }

    @AfterEach
    void cleanUp() {
        paymentMethodRepository.deleteAll();
    }

    private String driver(long userId) {
        return HostileTokens.valid(secret, userId, "CONDUCTOR");
    }

    private void givenCard(long userId) throws Exception {
        mockMvc.perform(post("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, driver(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"number":"%s","expiryMonth":9,"expiryYear":2029,"holderName":"TITULAR","label":"Propia"}
                                """
                                        .formatted(VISA)))
                .andExpect(status().isCreated());
    }

    // --- JWT hostil -------------------------------------------------------------------------

    @Test
    @DisplayName("JWT · Control: con un token válido, el conductor ve su tarjeta")
    void validTokenWorks() throws Exception {
        givenCard(601L);

        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, driver(601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastFour").value("1111"));
    }

    @Test
    @DisplayName("JWT · Un token vencido no lista tarjetas")
    void rejectsExpiredToken() throws Exception {
        givenCard(602L);

        mockMvc.perform(get("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.expired(secret, 602L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token con el sub de otro conductor, sin volver a firmar, no ve sus tarjetas")
    void rejectsTamperedSubject() throws Exception {
        givenCard(603L);
        String forged = HostileTokens.tampered(driver(604L), "\"sub\":\"604\"", "\"sub\":\"603\"");

        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token con alg=none, sin firma, no lista tarjetas")
    void rejectsAlgNone() throws Exception {
        givenCard(605L);

        mockMvc.perform(get("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.algNone(605L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token de otro emisor, firmado con su propia clave, no lista tarjetas")
    void rejectsTokenFromAnotherIssuer() throws Exception {
        givenCard(606L);

        mockMvc.perform(get("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.fromAnotherIssuer(606L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    // --- Mass assignment --------------------------------------------------------------------

    @Test
    @DisplayName("Mass assignment · Alta de tarjeta con driverId, marca, últimos 4, token e id de más: los pone el servidor")
    void cardIgnoresServerOwnedFields() throws Exception {
        mockMvc.perform(post("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, driver(607L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"number":"%s","expiryMonth":9,"expiryYear":2029,"holderName":"TITULAR",
                                 "label":"Propia","driverId":999,"brand":"AMEX","lastFour":"0000",
                                 "gatewayToken":"tok_inyectado","active":false,"id":777}
                                """
                                        .formatted(VISA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("VISA"))
                .andExpect(jsonPath("$.lastFour").value("1111"));

        List<PaymentMethod> stored = paymentMethodRepository.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getDriverId()).isEqualTo(607L);
        assertThat(stored.get(0).getGatewayToken()).isNotEqualTo("tok_inyectado");
        assertThat(stored.get(0).isActive()).isTrue();

        // Y el conductor que se quiso poner como dueño no la ve.
        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, driver(999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- Inyección --------------------------------------------------------------------------

    @Test
    @DisplayName("Inyección · Una etiqueta con <script> y SQL se guarda como texto y vuelve como JSON")
    void labelIsStoredAsData() throws Exception {
        String hostileLabel = "<script>alert(1)</script>' OR 1=1--";

        mockMvc.perform(post("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, driver(608L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"number":"%s","expiryMonth":9,"expiryYear":2029,"holderName":"TITULAR","label":"%s"}
                                """
                                        .formatted(VISA, hostileLabel)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.label").value(hostileLabel));

        assertThat(paymentMethodRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Inyección · Un número de tarjeta con SQL se rechaza con 400 y no guarda nada")
    void numberRejectsSql() throws Exception {
        mockMvc.perform(post("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, driver(609L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"number":"4111' OR '1'='1","expiryMonth":9,"expiryYear":2029,"holderName":"TITULAR"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(paymentMethodRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Inyección · Borrar con SQL en el id responde 400 y no borra nada")
    void deleteRejectsSqlInPath() throws Exception {
        givenCard(610L);

        mockMvc.perform(delete("/api/payment-methods/{id}", "1 OR 1=1").header(HttpHeaders.AUTHORIZATION, driver(610L)))
                .andExpect(status().isBadRequest());

        assertThat(paymentMethodRepository.findAll()).allMatch(PaymentMethod::isActive);
    }
}
