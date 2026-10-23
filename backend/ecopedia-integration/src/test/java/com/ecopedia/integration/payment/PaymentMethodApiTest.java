package com.ecopedia.integration.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.integration.payment.data.JpaPaymentMethodRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El ABM de medios de pago de punta a punta: quién puede usarlo y qué contesta (RF02, ECO-26).
 *
 * <p><b>Firma tokens de verdad</b> con la misma clave que declara {@code application.yml}, así que
 * cada caso recorre el filtro entero antes de llegar a la anotación. Es lo mismo que hacen
 * {@code TerminalAuthorizationTest} y {@code UserAuthorizationTest} en core, y acá además prueba
 * algo propio de este artefacto: que un token emitido por Usuarios, que vive en OTRO proceso, valga
 * en este. Si las dos claves se desincronizaran, esta clase se pone en rojo.
 *
 * <p><b>Cubre también la parte que ninguna anotación puede cubrir</b>: que un conductor no vea ni
 * elimine las tarjetas de otro. {@code hasRole('CONDUCTOR')} los deja pasar a los dos; lo que separa
 * a uno de otro es el id que sale del token, y eso solo se verifica ejerciéndolo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PaymentMethodApiTest {

    private static final String VALID_CARD =
            """
            {"number":"4111111111111111","expiryMonth":9,"expiryYear":2029,\
            "holderName":"SANTIAGO R","label":"La del laburo"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaPaymentMethodRepository paymentMethodRepository;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @AfterEach
    void cleanUp() {
        paymentMethodRepository.deleteAll();
    }

    /** Emite un token como el que devolvería el login de core, para el usuario y el rol pedidos. */
    private String bearer(long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "usuario" + userId + "@ecopedia.test")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        return "Bearer " + token;
    }

    private MockHttpServletRequestBuilder registerAs(long userId, String role, String body) {
        return post("/api/payment-methods")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    @DisplayName("Un conductor registra su tarjeta y recibe marca, últimos cuatro y nada más")
    void registersACard() throws Exception {
        mockMvc.perform(registerAs(1L, "CONDUCTOR", VALID_CARD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("VISA"))
                .andExpect(jsonPath("$.lastFour").value("1111"))
                .andExpect(jsonPath("$.label").value("La del laburo"))
                .andExpect(jsonPath("$.expired").value(false))
                // Lo que NO tiene que venir: el token de cobro y el número.
                .andExpect(jsonPath("$.gatewayToken").doesNotExist())
                .andExpect(jsonPath("$.number").doesNotExist());
    }

    @Test
    @DisplayName("La respuesta no contiene el número completo en ningún campo")
    void neverEchoesTheNumber() throws Exception {
        String body = mockMvc.perform(registerAs(1L, "CONDUCTOR", VALID_CARD))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // RF02 sobre el JSON entero, no campo por campo: si alguien agrega un campo con el número,
        // este test lo ve.
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("4111111111111111"));
    }

    @Test
    @DisplayName("Un operador no puede registrar medios de pago")
    void deniesTheOperator() throws Exception {
        mockMvc.perform(registerAs(1L, "CPO", VALID_CARD)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El administrador tampoco: RF03 no le da las tarjetas ajenas")
    void deniesTheAdministrator() throws Exception {
        mockMvc.perform(registerAs(1L, "ADMIN", VALID_CARD)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin token no se puede ni listar")
    void deniesTheAnonymous() throws Exception {
        mockMvc.perform(get("/api/payment-methods")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token firmado con otra clave no vale")
    void deniesAForgedToken() throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "UnaClaveDistintaDeLaQueUsaEcopediaParaFirmarTokens!!".getBytes(StandardCharsets.UTF_8));

        String forged = Jwts.builder()
                .subject("1")
                .claim("role", "CONDUCTOR")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cada conductor ve solo sus tarjetas")
    void listsOnlyOwnCards() throws Exception {
        mockMvc.perform(registerAs(1L, "CONDUCTOR", VALID_CARD)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, bearer(1L, "CONDUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // El otro conductor existe y tiene sesión válida, pero esta tarjeta no es suya.
        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, bearer(2L, "CONDUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Un conductor no puede eliminar la tarjeta de otro")
    void deniesRemovingSomeoneElsesCard() throws Exception {
        String created = mockMvc.perform(registerAs(1L, "CONDUCTOR", VALID_CARD))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long cardId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/payment-methods/" + cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L, "CONDUCTOR")))
                // 400 y no 403: contestar 403 desconectaría la sesión del frontend y además
                // confirmaría que ese id existe. Ver PaymentExceptionHandler.
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No se encontró")));

        // Y sigue estando para su dueño.
        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, bearer(1L, "CONDUCTOR")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("El dueño sí la elimina, y deja de listarse")
    void removesOwnCard() throws Exception {
        String created = mockMvc.perform(registerAs(1L, "CONDUCTOR", VALID_CARD))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long cardId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/payment-methods/" + cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L, "CONDUCTOR")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/payment-methods").header(HttpHeaders.AUTHORIZATION, bearer(1L, "CONDUCTOR")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Un número mal copiado devuelve 400 con el motivo, no un 500")
    void explainsARejectedCard() throws Exception {
        String typo =
                """
                {"number":"4111111111111112","expiryMonth":9,"expiryYear":2029,\
                "holderName":"SANTIAGO R"}""";

        mockMvc.perform(registerAs(1L, "CONDUCTOR", typo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no es válido")));
    }

    @Test
    @DisplayName("Un cuerpo incompleto devuelve el mensaje del campo que falta")
    void explainsAnInvalidBody() throws Exception {
        String missingHolder =
                """
                {"number":"4111111111111111","expiryMonth":9,"expiryYear":2029,"holderName":""}""";

        mockMvc.perform(registerAs(1L, "CONDUCTOR", missingHolder))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("titular")));
    }
}
