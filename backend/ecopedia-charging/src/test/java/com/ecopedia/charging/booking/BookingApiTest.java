package com.ecopedia.charging.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * El recorrido completo de reservar un slot (ECO-32, RF08), con el artefacto entero arriba:
 * retener, confirmar, listar y cancelar por HTTP.
 *
 * <p><b>Acá sí hay base.</b> Las reglas ya están probadas sin Spring en
 * {@code BookingServiceImplTest}; lo que agrega esta clase es que la consulta de cruce sea la
 * misma en JPQL que en memoria —una diferencia entre las dos haría pasar aquellas pruebas sobre
 * una base que en realidad deja reservar dos veces el mismo horario— y que cada error salga con
 * el código HTTP que el front espera.
 *
 * <p>Terminales se reemplaza con un mock de {@link ConnectorCatalog}: la prueba es de este
 * artefacto, y no tiene por qué necesitar a core corriendo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingApiTest {

    private static final long DRIVER = 77L;
    private static final long OTHER_DRIVER = 78L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private ConnectorCatalog connectorCatalog;

    /*
     * Confirmar pasa por el checkout, que le pregunta a Pagos por las tarjetas y a core por la seña. Los dos
     * son otros artefactos: se reemplazan, igual que Terminales.
     */
    @MockitoBean
    private PaymentDirectory paymentDirectory;

    @MockitoBean
    private TariffDirectory tariffDirectory;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    /**
     * Cada prueba trabaja sobre otro horario.
     *
     * <p>El contexto de Spring se comparte entre pruebas —y con las otras clases de test—, así
     * que la base y las retenciones en memoria sobreviven de una a la otra. Sin horarios
     * distintos, una prueba se encontraría con la reserva que dejó la anterior y el orden de
     * ejecución decidiría cuál falla.
     */
    private static int nextSlot = 0;

    @BeforeEach
    void connectorExists() {
        when(connectorCatalog.findConnector(anyLong()))
                .thenAnswer(call -> Optional.of(new ConnectorSnapshot(call.getArgument(0), 1L, "AVAILABLE")));
        when(paymentDirectory.hasUsablePaymentMethod(anyLong(), any())).thenReturn(true);
        when(tariffDirectory.calculateDeposit(anyLong())).thenReturn(BigDecimal.ZERO);
    }

    private String driver(long userId) {
        return TestTokens.bearer(secret, userId, "CONDUCTOR");
    }

    /**
     * Una ventana de una hora que no usa ninguna otra prueba.
     *
     * <p>A veinte días, cómodamente dentro del horizonte configurado ({@code max-horizon}) y
     * lejos de las ventanas a un día que usa {@code BookingAuthorizationTest}, que comparte el
     * mismo contexto de Spring y por lo tanto la misma base.
     */
    private static Instant nextWindowStart() {
        return Instant.now().plus(Duration.ofDays(20)).plus(Duration.ofHours(2L * nextSlot++));
    }

    /** Retiene un slot y devuelve el id de la retención. */
    private String holdSlot(long connectorId, Instant start, long userId) throws Exception {
        String body = """
                {"connectorId":%d,"start":"%s","end":"%s"}
                """
                .formatted(connectorId, start, start.plus(Duration.ofHours(1)));

        String response = mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, driver(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response).get("id").asText();
    }

    /** Intenta retener el slot y devuelve el código HTTP con que contestó. */
    private int holdAttempt(long connectorId, Instant start, long userId) throws Exception {
        String body = """
                {"connectorId":%d,"start":"%s","end":"%s"}
                """
                .formatted(connectorId, start, start.plus(Duration.ofHours(1)));

        return mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, driver(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String confirmBody(String holdId) {
        return """
                {"holdId":"%s","acceptGracePeriod":true}
                """.formatted(holdId);
    }

    /** Confirma la retención y devuelve la reserva creada. */
    private JsonNode confirm(String holdId, long userId) throws Exception {
        String response = mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, driver(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(holdId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response);
    }

    /*
     * LA prueba del ticket, y la que se muestra en la demo: confirmada la reserva, el conector
     * queda bloqueado para el resto durante esa ventana. El segundo conductor no choca contra la
     * retención del primero —esa se soltó al confirmar— sino contra la reserva guardada.
     */
    @Test
    @DisplayName("Confirmada la reserva, otro conductor recibe 409 sobre esa ventana")
    void confirmedBookingBlocksTheConnector() throws Exception {
        Instant start = nextWindowStart();
        JsonNode booking = confirm(holdSlot(10L, start, DRIVER), DRIVER);

        assertStatusIs(booking, "CONFIRMED");

        // Exactamente la misma ventana, y una que se cruza a medias: las dos bloqueadas.
        assertThat(holdAttempt(10L, start, OTHER_DRIVER)).isEqualTo(409);
        assertThat(holdAttempt(10L, start.plus(Duration.ofMinutes(30)), OTHER_DRIVER))
                .isEqualTo(409);

        // Y la ventana pegada sigue libre: el bloqueo no se pasa de lo reservado.
        assertThat(holdAttempt(10L, start.plus(Duration.ofHours(1)), OTHER_DRIVER))
                .isEqualTo(201);
    }

    @Test
    @DisplayName("Cancelada la reserva, la ventana vuelve a estar disponible")
    void cancellingFreesTheWindow() throws Exception {
        Instant start = nextWindowStart();
        JsonNode booking = confirm(holdSlot(11L, start, DRIVER), DRIVER);
        long bookingId = booking.get("bookingId").asLong();

        mockMvc.perform(delete("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(status().isNoContent());

        assertThat(holdAttempt(11L, start, OTHER_DRIVER)).isEqualTo(201);
    }

    @Test
    @DisplayName("El conductor ve su reserva en el listado, con su ventana y su estado")
    void listsTheDriverBookings() throws Exception {
        Instant start = nextWindowStart();
        long bookingId =
                confirm(holdSlot(12L, start, DRIVER), DRIVER).get("bookingId").asLong();

        mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].connectorId".formatted(bookingId))
                        .value(12))
                .andExpect(
                        jsonPath("$[?(@.id == %d)].status".formatted(bookingId)).value("CONFIRMED"));

        // Y el de al lado no la ve: el listado sale del token, no de la URL.
        mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, driver(OTHER_DRIVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(bookingId)).isEmpty());
    }

    @Test
    @DisplayName("Confirmar dos veces la misma retención: la segunda recibe 404")
    void rejectsConfirmingTwice() throws Exception {
        String holdId = holdSlot(13L, nextWindowStart(), DRIVER);
        confirm(holdId, DRIVER);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(holdId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Confirmar una retención inventada recibe 404")
    void rejectsUnknownHold() throws Exception {
        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Confirmar la retención de otro conductor recibe 403")
    void rejectsConfirmingSomeoneElsesHold() throws Exception {
        String holdId = holdSlot(14L, nextWindowStart(), DRIVER);

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, driver(OTHER_DRIVER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(holdId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancelar la reserva de otro conductor recibe 403 y no la cancela")
    void rejectsCancellingSomeoneElsesBooking() throws Exception {
        Instant start = nextWindowStart();
        long bookingId =
                confirm(holdSlot(15L, start, DRIVER), DRIVER).get("bookingId").asLong();

        mockMvc.perform(delete("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, driver(OTHER_DRIVER)))
                .andExpect(status().isForbidden());

        // Y sigue bloqueando el slot, que es lo que probaría que de verdad no se canceló.
        assertThat(holdAttempt(15L, start, OTHER_DRIVER)).isEqualTo(409);
    }

    @Test
    @DisplayName("Cancelar una reserva que no existe recibe 404")
    void rejectsCancellingUnknownBooking() throws Exception {
        mockMvc.perform(delete("/api/bookings/{id}", 999_999L).header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cancelar dos veces la misma reserva recibe 204 las dos veces")
    void cancellingTwiceIsIdempotent() throws Exception {
        long bookingId = confirm(holdSlot(16L, nextWindowStart(), DRIVER), DRIVER)
                .get("bookingId")
                .asLong();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, driver(DRIVER)))
                    .andExpect(status().isNoContent());
        }
    }

    /** Consulta la disponibilidad del conector con token de conductor. */
    private ResultActions availability(long connectorId, Instant from, Instant to) throws Exception {
        return mockMvc.perform(get("/api/bookings/availability")
                .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                .param("connectorId", String.valueOf(connectorId))
                .param("from", from.toString())
                .param("to", to.toString()));
    }

    /*
     * ECO-33 contra la base de verdad: la reserva guardada sale por la consulta JPQL de
     * findOverlapping y recorta la agenda. Si esa consulta y la de existsOverlapping se
     * separaran, esta prueba mostraría como libre un horario que retener rechaza.
     */
    @Test
    @DisplayName("La disponibilidad descuenta la reserva confirmada y deja libre lo pegado")
    void availabilitySubtractsTheConfirmedBooking() throws Exception {
        Instant start = nextWindowStart().truncatedTo(ChronoUnit.SECONDS);
        confirm(holdSlot(17L, start, DRIVER), DRIVER);

        String response = availability(17L, start.minus(Duration.ofHours(1)), start.plus(Duration.ofHours(2)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode free = json.readTree(response);
        assertThat(free).hasSize(2);
        assertWindow(free.get(0), start.minus(Duration.ofHours(1)), start);
        assertWindow(free.get(1), start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(2)));

        // Y lo que la agenda ofrece como libre, retener lo acepta: las dos consultas dicen lo mismo.
        assertThat(holdAttempt(17L, start.plus(Duration.ofHours(1)), OTHER_DRIVER))
                .isEqualTo(201);
    }

    /* Las dos respuestas que el front tiene que poder distinguir. */
    @Test
    @DisplayName("Todo tomado es 200 con lista vacía; fuera de servicio es 409")
    void fullyBookedAndOutOfServiceAreDifferentAnswers() throws Exception {
        Instant start = nextWindowStart().truncatedTo(ChronoUnit.SECONDS);
        confirm(holdSlot(18L, start, DRIVER), DRIVER);

        availability(18L, start, start.plus(Duration.ofHours(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        when(connectorCatalog.findConnector(18L))
                .thenReturn(Optional.of(new ConnectorSnapshot(18L, 1L, "OUT_OF_SERVICE")));

        availability(18L, start, start.plus(Duration.ofHours(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("fuera de servicio")));
    }

    @Test
    @DisplayName("La disponibilidad de un conector que no existe recibe 404")
    void availabilityOfUnknownConnector() throws Exception {
        when(connectorCatalog.findConnector(19L)).thenReturn(Optional.empty());
        Instant start = nextWindowStart();

        availability(19L, start, start.plus(Duration.ofHours(1))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un rango invertido, o al que le falta un extremo, recibe 400")
    void availabilityWithInvalidRange() throws Exception {
        Instant start = nextWindowStart();

        availability(20L, start, start.minus(Duration.ofHours(1))).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/bookings/availability")
                        .header(HttpHeaders.AUTHORIZATION, driver(DRIVER))
                        .param("connectorId", "20")
                        .param("from", start.toString()))
                .andExpect(status().isBadRequest());
    }

    private static void assertWindow(JsonNode window, Instant start, Instant end) {
        assertThat(Instant.parse(window.get("start").asText())).isEqualTo(start);
        assertThat(Instant.parse(window.get("end").asText())).isEqualTo(end);
    }

    private static void assertStatusIs(JsonNode booking, String expected) {
        assertThat(booking.get("status").asText()).isEqualTo(expected);
    }
}
