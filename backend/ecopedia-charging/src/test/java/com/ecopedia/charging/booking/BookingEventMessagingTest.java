package com.ecopedia.charging.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Lo que Reservas deja en la cola {@code notifications.dispatch}, con el artefacto entero y un
 * Artemis de verdad —el embebido de los tests—.
 *
 * <p>{@code BookingServiceImplTest} ya prueba cuándo se publica; lo que agrega esta clase es el
 * mensaje tal como lo va a recibir Notificaciones: JSON en un {@code TextMessage}, con el tipo
 * lógico en {@code _type} y las fechas como texto. Es el contrato entre los dos artefactos, y es
 * lo que se rompería en silencio si alguien cambiara el conversor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingEventMessagingTest {

    private static final long DRIVER = 501L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ConnectionFactory connectionFactory;

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

    @Value("${ecopedia.notifications.queue}")
    private String queue;

    /**
     * Un receptor propio y no el {@code JmsTemplate} de la aplicación: el de la aplicación espera
     * para siempre si el mensaje no llega, y una prueba colgada no dice qué falló.
     */
    private JmsTemplate receiver;

    @BeforeEach
    void setUp() {
        when(connectorCatalog.findConnector(anyLong()))
                .thenAnswer(call -> Optional.of(new ConnectorSnapshot(call.getArgument(0), 1L, "AVAILABLE")));
        when(paymentDirectory.hasUsablePaymentMethod(anyLong(), any())).thenReturn(true);
        when(tariffDirectory.calculateDeposit(anyLong())).thenReturn(BigDecimal.ZERO);
        receiver = new JmsTemplate(connectionFactory);
        receiver.setReceiveTimeout(5_000);
    }

    @Test
    @DisplayName("Confirmar deja en la cola el aviso en JSON, con su tipo y la ventana de la reserva")
    void confirmingLeavesTheEventInTheQueue() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(25)).truncatedTo(ChronoUnit.SECONDS);
        long bookingId = confirm(hold(901L, start));

        Message message = receiver.receiveSelected(queue, "bookingId = " + bookingId);

        assertThat(message).isInstanceOf(TextMessage.class);
        assertThat(message.getStringProperty("_type")).isEqualTo("booking-event");
        assertThat(message.getStringProperty("eventType")).isEqualTo("BOOKING_CONFIRMED");

        JsonNode body = json.readTree(((TextMessage) message).getText());
        assertThat(body.get("eventId").asText()).isEqualTo("booking-" + bookingId + "-BOOKING_CONFIRMED");
        assertThat(body.get("driverId").asLong()).isEqualTo(DRIVER);
        assertThat(body.get("connectorId").asLong()).isEqualTo(901L);
        assertThat(Instant.parse(body.get("windowStart").asText())).isEqualTo(start);
    }

    @Test
    @DisplayName("Cancelar deja en la cola un segundo aviso, de cancelación")
    void cancellingLeavesASecondEvent() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(25)).plus(Duration.ofHours(3));
        long bookingId = confirm(hold(902L, start));

        mockMvc.perform(delete("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, driver()))
                .andExpect(status().isNoContent());

        Message confirmed = receiver.receiveSelected(queue, "bookingId = " + bookingId);
        Message cancelled = receiver.receiveSelected(queue, "bookingId = " + bookingId);

        assertThat(confirmed.getStringProperty("eventType")).isEqualTo("BOOKING_CONFIRMED");
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getStringProperty("eventType")).isEqualTo("BOOKING_CANCELLED");
    }

    private String driver() {
        return TestTokens.bearer(secret, DRIVER, "CONDUCTOR");
    }

    private String hold(long connectorId, Instant start) throws Exception {
        String body = """
                {"connectorId":%d,"start":"%s","end":"%s"}
                """
                .formatted(connectorId, start, start.plus(Duration.ofHours(1)));

        String response = mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, driver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private long confirm(String holdId) throws Exception {
        String response = mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, driver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"holdId":"%s","acceptGracePeriod":true}
                                """
                                        .formatted(holdId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response).get("bookingId").asLong();
    }
}
