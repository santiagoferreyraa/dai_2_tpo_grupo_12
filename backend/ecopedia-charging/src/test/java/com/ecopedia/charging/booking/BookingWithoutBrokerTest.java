package com.ecopedia.charging.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Con el broker caído, la reserva se confirma igual (RNF06).
 *
 * <p>Es la decisión de ECO-41 que más fácil se rompe sin que nadie lo note: basta con que el
 * adaptador deje escapar la excepción para que el conductor reciba un 500 por una reserva que
 * quedó guardada. El broker se "cae" reemplazando el {@code JmsTemplate} por uno que falla como
 * falla el de verdad cuando no hay a quién conectarse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BookingWithoutBrokerTest {

    private static final long DRIVER = 601L;

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

    @MockitoBean
    private JmsTemplate jmsTemplate;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @Test
    @DisplayName("Si el aviso no se puede publicar, la reserva se confirma igual")
    void confirmsEvenIfTheBrokerIsDown() throws Exception {
        when(connectorCatalog.findConnector(anyLong()))
                .thenAnswer(call -> Optional.of(new ConnectorSnapshot(call.getArgument(0), 1L, "AVAILABLE")));
        when(paymentDirectory.hasUsablePaymentMethod(anyLong(), any())).thenReturn(true);
        when(tariffDirectory.calculateDeposit(anyLong())).thenReturn(BigDecimal.ZERO);
        doThrow(new UncategorizedJmsException("Connection refused: localhost:61616"))
                .when(jmsTemplate)
                .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));

        String driver = TestTokens.bearer(secret, DRIVER, "CONDUCTOR");
        Instant start = Instant.now().plus(Duration.ofDays(26));

        String hold = mockMvc.perform(post("/api/bookings/holds")
                        .header(HttpHeaders.AUTHORIZATION, driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"connectorId":903,"start":"%s","end":"%s"}
                                """
                                        .formatted(start, start.plus(Duration.ofHours(1)))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/checkout/booking")
                        .header(HttpHeaders.AUTHORIZATION, driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"holdId":"%s","acceptGracePeriod":true}
                                """
                                        .formatted(json.readTree(hold).get("id").asText())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Y el aviso sí se intentó: lo que se prueba es que su falla no llega al conductor.
        verify(jmsTemplate).convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
    }
}
