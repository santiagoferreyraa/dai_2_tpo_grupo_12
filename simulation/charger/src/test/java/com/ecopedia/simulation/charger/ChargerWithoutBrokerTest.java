package com.ecopedia.simulation.charger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Sin broker, el cargador no enciende: contesta 503 y el conector queda libre.
 *
 * <p>Es la decisión que más fácil se rompe sin que nadie lo note: bastaría con publicar la primera
 * lectura "como se pueda" para que el conector cargue sin que nadie reciba sus lecturas. El broker
 * se "cae" reemplazando el {@code JmsTemplate} por uno que falla como el de verdad, igual que en
 * {@code BookingWithoutBrokerTest} de ecopedia-charging.
 */
@SpringBootTest(properties = "simulation.charger.reading-interval=1h")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ChargerWithoutBrokerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JmsTemplate jmsTemplate;

    @Test
    @DisplayName("Si no puede publicar la primera lectura, rechaza la orden con 503 y no enciende")
    void refusesWithoutBroker() throws Exception {
        doThrow(new UncategorizedJmsException("Connection refused"))
                .when(jmsTemplate)
                .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));

        mockMvc.perform(post("/v1/connectors/201/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ecopedia-dev-only-charger-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-201\",\"powerLimitKw\":22}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("telemetry_unavailable"));

        mockMvc.perform(get("/v1/connectors/201")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ecopedia-dev-only-charger-token"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }
}
