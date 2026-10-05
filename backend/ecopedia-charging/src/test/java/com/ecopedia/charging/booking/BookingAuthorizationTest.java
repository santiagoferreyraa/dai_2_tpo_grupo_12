package com.ecopedia.charging.booking;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Seguridad y contrato HTTP de la retención de slots (ECO-31), con el artefacto entero arriba.
 *
 * <p>Los tokens se firman como los firma core, con {@link TestTokens}.
 *
 * <p>Acá va quién puede llamar a cada operación; el flujo completo de reservar —retener,
 * confirmar, listar, cancelar— y lo que devuelve cada paso están en {@code BookingApiTest}.
 *
 * <p>Terminales se reemplaza con un mock de {@link ConnectorCatalog}: la prueba es de este
 * artefacto, y no tiene por qué necesitar a core corriendo.
 *
 * <p>De paso, que el contexto levante con el perfil {@code dev} prueba que la migración de
 * Flyway y la entidad {@code Booking} coinciden: con {@code ddl-auto: validate}, cualquier
 * diferencia aborta el arranque.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectorCatalog connectorCatalog;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    /** Cada prueba usa otra hora, para que las retenciones de una no choquen con las de otra. */
    private static int nextSlot = 0;

    @BeforeEach
    void connectorExists() {
        when(connectorCatalog.findConnector(anyLong()))
                .thenAnswer(call -> Optional.of(new ConnectorSnapshot(call.getArgument(0), 1L, "AVAILABLE")));
    }

    private String bearer(String role) {
        return TestTokens.bearer(secret, 42L, role);
    }

    /** Un cuerpo de retención válido, sobre una ventana futura que ninguna otra prueba usa. */
    private static String holdJson() {
        Instant start = Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(2L * nextSlot++));
        return """
                {"connectorId":7,"start":"%s","end":"%s"}
                """
                .formatted(start, start.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("Sin token, retener un slot se rechaza")
    void rejectsAnonymousCallers() throws Exception {
        mockMvc.perform(post("/api/bookings/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdJson()))
                .andExpect(status().isForbidden());
    }

    /* Reservar es cosa del conductor: un operador logueado no retiene slots. */
    @Test
    @DisplayName("Con token de CPO, retener un slot se rechaza")
    void rejectsOperators() throws Exception {
        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CPO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdJson()))
                .andExpect(status().isForbidden());
    }

    /*
     * El caso que justifica compartir el secreto por variable de entorno: un token con la forma
     * correcta pero firmado con otra clave es un token falsificado, y tiene que valer lo mismo
     * que ninguno.
     */
    @Test
    @DisplayName("Un token firmado con otro secreto se trata como anónimo")
    void rejectsTokensSignedWithAnotherSecret() throws Exception {
        String forged = TestTokens.bearer("OtroSecretoQueNoEsElDeEcopediaPeroTieneLargoSuficiente!!", 42L, "CONDUCTOR");

        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, forged)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con token de CONDUCTOR, la retención se crea y dice cuándo vence")
    void createsHoldForDrivers() throws Exception {
        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.connectorId").value(7))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("El mismo slot pedido dos veces: la segunda recibe 409")
    void rejectsTheSameSlotTwice() throws Exception {
        String body = holdJson();

        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 43L, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Una ventana con el fin antes que el inicio recibe 400")
    void rejectsInvertedWindow() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(3));
        String inverted = """
                {"connectorId":7,"start":"%s","end":"%s"}
                """
                .formatted(start, start.minus(Duration.ofHours(1)));

        mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inverted))
                .andExpect(status().isBadRequest());
    }

    /*
     * Las operaciones que ECO-32 sumó, con la misma regla que retener: son del conductor. Se
     * prueban con ids que no existen a propósito —lo que se verifica es que el pedido ni siquiera
     * llegue al servicio, así que tiene que dar 403 y no 404—.
     */

    @Test
    @DisplayName("Sin token, confirmar una reserva se rechaza")
    void rejectsAnonymousConfirm() throws Exception {
        mockMvc.perform(post("/api/checkout/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"holdId":"%s","acceptGracePeriod":true}
                                """
                                        .formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con token de CPO, confirmar una reserva se rechaza")
    void rejectsOperatorConfirm() throws Exception {
        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CPO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"holdId":"%s","acceptGracePeriod":true}
                                """
                                        .formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con token de CPO, listar reservas de conductor se rechaza")
    void rejectsOperatorListing() throws Exception {
        mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, bearer("CPO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin token, cancelar una reserva se rechaza")
    void rejectsAnonymousCancel() throws Exception {
        mockMvc.perform(delete("/api/bookings/1")).andExpect(status().isForbidden());
    }

    /*
     * La disponibilidad (ECO-33) tiene otra regla que el resto: alcanza con tener sesión, sea
     * cual sea el rol. El anónimo no pasa.
     */

    /** Un pedido de disponibilidad válido, de cuatro horas a dos días de ahora. */
    private static MockHttpServletRequestBuilder availabilityRequest() {
        Instant from = Instant.now().plus(Duration.ofDays(2));
        return get("/api/bookings/availability")
                .param("connectorId", "7")
                .param("from", from.toString())
                .param("to", from.plus(Duration.ofHours(4)).toString());
    }

    @Test
    @DisplayName("Sin token, consultar la disponibilidad se rechaza")
    void rejectsAnonymousAvailability() throws Exception {
        mockMvc.perform(availabilityRequest()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con sesión, cualquier rol consulta la disponibilidad: conductor, CPO y admin")
    void anySessionSeesAvailability() throws Exception {
        for (String role : new String[] {"CONDUCTOR", "CPO", "ADMIN"}) {
            mockMvc.perform(availabilityRequest().header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Con token de CPO, cancelar una reserva se rechaza")
    void rejectsOperatorCancel() throws Exception {
        mockMvc.perform(delete("/api/bookings/1").header(HttpHeaders.AUTHORIZATION, bearer("CPO")))
                .andExpect(status().isForbidden());
    }
}
