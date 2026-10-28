package com.ecopedia.simulation.charger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.simulation.charger.service.ChargerSimulator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El cargador de punta a punta: las órdenes por REST y lo que sale al tópico, con un Artemis de
 * verdad —el embebido de los tests—.
 *
 * <p>El reloj de la telemetría no corre solo ({@code reading-interval=1h}): cada test da los pasos a
 * mano con {@link ChargerSimulator#tick()}, así lo que se afirma no depende de cuánto tarda la
 * máquina. Cada test usa su propio conector y escucha solo ese, con un selector sobre la propiedad
 * {@code connectorId}: el simulador es uno solo para toda la clase.
 */
@SpringBootTest(properties = "simulation.charger.reading-interval=1h")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ChargerApiTest {

    private static final String ACCESS_TOKEN = "Bearer ecopedia-dev-only-charger-token";
    private static final long RECEIVE_TIMEOUT_MS = 3000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private ChargerSimulator simulator;

    private Connection connection;

    @AfterEach
    void tearDown() throws JMSException {
        if (connection != null) connection.close();
    }

    /** Se suscribe al tópico antes de dar la orden: un suscriptor no durable no ve lo anterior. */
    private MessageConsumer listenTo(long connectorId) throws JMSException {
        connection = connectionFactory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        return session.createConsumer(session.createTopic("telemetry.charging"), "connectorId = " + connectorId);
    }

    private JsonNode next(MessageConsumer consumer) throws Exception {
        Message message = consumer.receive(RECEIVE_TIMEOUT_MS);
        assertThat(message).as("una lectura en el tópico").isInstanceOf(TextMessage.class);
        return json.readTree(((TextMessage) message).getText());
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, ACCESS_TOKEN).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions start(long connectorId, String body) throws Exception {
        return mockMvc.perform(
                authorized(post("/v1/connectors/{id}/session", connectorId)).content(body));
    }

    private ResultActions start(long connectorId, String sessionId, int initialPercent) throws Exception {
        return start(
                connectorId,
                """
                {"sessionId":"%s","powerLimitKw":60,"batteryCapacityKwh":60,"initialBatteryPercent":%d}
                """
                        .formatted(sessionId, initialPercent));
    }

    @Test
    @DisplayName("Encender publica la primera lectura con 0 kWh, y cada paso suma la energía entregada")
    void startsAndReports() throws Exception {
        MessageConsumer telemetry = listenTo(101);

        start(101, "s-101", 20)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHARGING"))
                .andExpect(jsonPath("$.session.sessionId").value("s-101"))
                .andExpect(jsonPath("$.session.batteryPercent").value(20.0));

        JsonNode first = next(telemetry);
        assertThat(first.get("type").asText()).isEqualTo("READING");
        assertThat(first.get("sessionId").asText()).isEqualTo("s-101");
        assertThat(first.get("connectorId").asLong()).isEqualTo(101);
        assertThat(first.get("energyKwh").decimalValue()).isEqualByComparingTo("0");
        assertThat(first.get("powerKw").decimalValue()).isEqualByComparingTo("60");
        assertThat(first.get("eventId").asText()).isNotBlank();
        assertThat(first.get("timestamp").asText()).contains("T");

        simulator.tick();

        // 60 kW durante 1 minuto simulado: 1 kWh, que en una batería de 60 kWh son 1,7 puntos.
        JsonNode second = next(telemetry);
        assertThat(second.get("type").asText()).isEqualTo("READING");
        assertThat(second.get("energyKwh").decimalValue()).isEqualByComparingTo("1.000");
        assertThat(second.get("batteryPercent").decimalValue()).isEqualByComparingTo("21.7");
        assertThat(second.get("eventId").asText())
                .isNotEqualTo(first.get("eventId").asText());
    }

    @Test
    @DisplayName("El mensaje es JSON con el tipo lógico y las propiedades para filtrar")
    void messageFormat() throws Exception {
        MessageConsumer telemetry = listenTo(102);

        start(102, "s-102", 30).andExpect(status().isCreated());

        Message message = telemetry.receive(RECEIVE_TIMEOUT_MS);
        assertThat(message).isInstanceOf(TextMessage.class);
        assertThat(message.getStringProperty("_type")).isEqualTo("telemetry-event");
        assertThat(message.getStringProperty("eventType")).isEqualTo("READING");
        assertThat(message.getLongProperty("connectorId")).isEqualTo(102);
        assertThat(message.getStringProperty("sessionId")).isEqualTo("s-102");
    }

    @Test
    @DisplayName("Apagar publica la lectura final y devuelve lo entregado; apagar de nuevo no hace nada")
    void stops() throws Exception {
        MessageConsumer telemetry = listenTo(103);
        start(103, "s-103", 20).andExpect(status().isCreated());
        next(telemetry);
        simulator.tick();
        next(telemetry);

        mockMvc.perform(authorized(delete("/v1/connectors/103/session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("s-103"))
                .andExpect(jsonPath("$.energyKwh").value(1.0));

        JsonNode last = next(telemetry);
        assertThat(last.get("type").asText()).isEqualTo("FINISHED");
        assertThat(last.get("endReason").asText()).isEqualTo("STOPPED");
        assertThat(last.get("energyKwh").decimalValue()).isEqualByComparingTo("1.000");

        mockMvc.perform(authorized(delete("/v1/connectors/103/session"))).andExpect(status().isNoContent());
        mockMvc.perform(authorized(get("/v1/connectors/103")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("La misma sesión dos veces no reinicia la carga; otra sesión encuentra el conector ocupado")
    void idempotentStartAndBusyConnector() throws Exception {
        MessageConsumer telemetry = listenTo(104);
        start(104, "s-104", 20).andExpect(status().isCreated());
        next(telemetry);
        simulator.tick();

        start(104, "s-104", 20)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.energyKwh").value(1.0));

        start(104, "otra-sesion", 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("connector_busy"));
    }

    @Test
    @DisplayName("Romper el conector en plena carga la corta con una lectura de falla; roto, no enciende")
    void faultedConnector() throws Exception {
        MessageConsumer telemetry = listenTo(105);
        start(105, "s-105", 20).andExpect(status().isCreated());
        next(telemetry);
        simulator.tick();
        next(telemetry);

        mockMvc.perform(authorized(post("/v1/connectors/105/fault")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAULTED"));

        JsonNode fault = next(telemetry);
        assertThat(fault.get("type").asText()).isEqualTo("FAULT");
        assertThat(fault.get("endReason").asText()).isEqualTo("FAULT");
        assertThat(fault.get("energyKwh").decimalValue()).isEqualByComparingTo("1.000");

        start(105, "s-105-b", 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("connector_faulted"));

        mockMvc.perform(authorized(delete("/v1/connectors/105/fault")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        start(105, "s-105-b", 20).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Con la batería llena, la sesión termina sola y el conector queda libre")
    void batteryFull() throws Exception {
        MessageConsumer telemetry = listenTo(106);
        start(106, "s-106", 99).andExpect(status().isCreated());
        next(telemetry);

        JsonNode last = null;
        for (int step = 0;
                step < 20 && (last == null || last.get("type").asText().equals("READING"));
                step++) {
            simulator.tick();
            last = next(telemetry);
        }

        assertThat(last.get("type").asText()).isEqualTo("FINISHED");
        assertThat(last.get("endReason").asText()).isEqualTo("BATTERY_FULL");
        assertThat(last.get("batteryPercent").decimalValue()).isEqualByComparingTo("100");
        mockMvc.perform(authorized(get("/v1/connectors/106")))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("Sin los datos del auto, usa uno típico con la batería entre el 15 y el 40 %")
    void defaultVehicle() throws Exception {
        String body = start(
                        107,
                        """
                        {"sessionId":"s-107","powerLimitKw":22}
                        """)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        double percent = json.readTree(body).at("/session/batteryPercent").asDouble();
        assertThat(percent).isBetween(15.0, 40.0);
    }

    @Test
    @DisplayName("Sin la clave de acceso, 401; con un pedido inválido, 400 con su código")
    void rejectsBadRequests() throws Exception {
        mockMvc.perform(post("/v1/connectors/108/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s\",\"powerLimitKw\":22}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"invalid_access_token\"}"));

        start(108, "{\"sessionId\":\"s\",\"powerLimitKw\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        start(108, "{\"sessionId\":\"s\",\"powerLimitKw\":22,\"initialBatteryPercent\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        start(0, "{\"sessionId\":\"s\",\"powerLimitKw\":22}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_connector"));
        mockMvc.perform(authorized(get("/v1/connectors/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_connector"));
    }
}
