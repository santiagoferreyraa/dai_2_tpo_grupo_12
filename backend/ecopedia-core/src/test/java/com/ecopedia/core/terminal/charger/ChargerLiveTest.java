package com.ecopedia.core.terminal.charger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.core.terminal.domain.ChargerReading;
import com.ecopedia.core.terminal.domain.ChargerRejectedException;
import com.ecopedia.core.terminal.domain.ChargerRejectedException.Reason;
import com.ecopedia.core.terminal.domain.ChargerUnavailableException;
import com.ecopedia.core.terminal.domain.ChargingOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

/**
 * El adaptador contra el simulador de verdad: el proceso de {@code simulation/charger} levantado en
 * el 8088, publicando en el Artemis de docker-compose.
 *
 * <p><b>Corre solo a pedido</b>, porque necesita los dos procesos arriba y CI no los levanta. Con el
 * simulador corriendo ({@code pnpm dev:charger}, o el panel {@code charger} de {@code pnpm dev}) y el
 * broker ({@code docker compose up -d activemq}):
 *
 * <pre>mvn -pl backend/ecopedia-core test -Dtest=ChargerLiveTest -Dcharger.live=true
 *     -Dcharger.broker-password=&lt;la del .env&gt;</pre>
 *
 * <p>Usa la clave de desarrollo; si el .env tiene {@code ECOPEDIA_CHARGER_ACCESS_TOKEN}, el
 * simulador la usa, y hay que pasarla con {@code -Dcharger.token=...}. Escucha el tópico con un
 * selector sobre su conector, así no se mezcla con otras cargas que haya en curso. Los conectores
 * son ids altos que no existen en la base: el simulador no la conoce.
 */
@EnabledIfSystemProperty(named = "charger.live", matches = "true")
class ChargerLiveTest {

    private static final String URL = System.getProperty("charger.url", "http://localhost:8088");
    private static final String TOKEN = System.getProperty("charger.token", "ecopedia-dev-only-charger-token");
    private static final String BROKER = System.getProperty("charger.broker-url", "tcp://localhost:61616");
    private static final String BROKER_USER = System.getProperty("charger.broker-user", "ecopedia");
    private static final String BROKER_PASSWORD = System.getProperty("charger.broker-password", "");

    /** Más que un intervalo de lectura (2 s por defecto), con margen. */
    private static final long RECEIVE_TIMEOUT_MS = 6000;

    private static final long CONNECTOR = 990_001;
    private static final long FAULTY_CONNECTOR = 990_002;

    private final ObjectMapper json = new ObjectMapper();
    private Connection connection;

    private static ChargerRestClient client(String token) {
        return new ChargerRestClient(ChargerClientConfig.build(
                RestClient.builder(), URL, token, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    private static ChargingOrder order(long connectorId, String sessionId) {
        return new ChargingOrder(connectorId, sessionId, new BigDecimal("50"), new BigDecimal("60"), null);
    }

    /** La orden de romper y reparar no es del puerto —es de la demo—: va directo a la API. */
    private static RestClient raw() {
        return RestClient.builder()
                .baseUrl(URL)
                .defaultHeader("Authorization", "Bearer " + TOKEN)
                .build();
    }

    @BeforeEach
    void cleanUp() {
        // Lo que haya quedado de una corrida anterior que se cortó a la mitad.
        client(TOKEN).deactivate(CONNECTOR);
        raw().delete()
                .uri("/v1/connectors/{id}/fault", FAULTY_CONNECTOR)
                .retrieve()
                .toBodilessEntity();
    }

    @AfterEach
    void tearDown() throws JMSException {
        if (connection != null) connection.close();
    }

    private MessageConsumer listenTo(long connectorId) throws JMSException {
        connection = new ActiveMQConnectionFactory(BROKER).createConnection(BROKER_USER, BROKER_PASSWORD);
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        return session.createConsumer(session.createTopic("telemetry.charging"), "connectorId = " + connectorId);
    }

    private JsonNode next(MessageConsumer consumer) throws Exception {
        Message message = consumer.receive(RECEIVE_TIMEOUT_MS);
        assertThat(message).as("una lectura en el tópico").isInstanceOf(TextMessage.class);
        return json.readTree(((TextMessage) message).getText());
    }

    @Test
    @DisplayName("Encender, recibir las lecturas por el tópico y apagar")
    void chargesAndReportsThroughTheTopic() throws Exception {
        MessageConsumer telemetry = listenTo(CONNECTOR);
        String sessionId = "live-" + UUID.randomUUID();
        ChargerRestClient client = client(TOKEN);

        ChargerReading started = client.activate(order(CONNECTOR, sessionId));
        assertThat(started.sessionId()).isEqualTo(sessionId);
        assertThat(started.energyKwh()).isEqualByComparingTo("0");

        JsonNode first = next(telemetry);
        assertThat(first.get("type").asText()).isEqualTo("READING");
        assertThat(first.get("sessionId").asText()).isEqualTo(sessionId);

        JsonNode second = next(telemetry);
        assertThat(second.get("type").asText()).isEqualTo("READING");
        assertThat(second.get("energyKwh").decimalValue()).isPositive();

        // Repetir la orden no reinicia la carga.
        assertThat(client.activate(order(CONNECTOR, sessionId)).energyKwh()).isPositive();

        // Otra sesión encuentra el conector ocupado.
        assertThatThrownBy(() -> client.activate(order(CONNECTOR, "otra")))
                .isInstanceOf(ChargerRejectedException.class)
                .extracting(e -> ((ChargerRejectedException) e).getReason())
                .isEqualTo(Reason.CONNECTOR_BUSY);

        ChargerReading last = client.deactivate(CONNECTOR).orElseThrow();
        assertThat(last.energyKwh()).isPositive();

        JsonNode finished;
        do {
            finished = next(telemetry);
        } while (finished.get("type").asText().equals("READING"));
        assertThat(finished.get("type").asText()).isEqualTo("FINISHED");
        assertThat(finished.get("endReason").asText()).isEqualTo("STOPPED");
        assertThat(finished.get("energyKwh").decimalValue()).isEqualByComparingTo(last.energyKwh());

        assertThat(client.deactivate(CONNECTOR)).isEmpty();
    }

    @Test
    @DisplayName("Un conector roto no enciende")
    void faultedConnector() {
        raw().post()
                .uri("/v1/connectors/{id}/fault", FAULTY_CONNECTOR)
                .retrieve()
                .toBodilessEntity();

        assertThatThrownBy(() -> client(TOKEN).activate(order(FAULTY_CONNECTOR, "live-faulted")))
                .isInstanceOf(ChargerRejectedException.class)
                .extracting(e -> ((ChargerRejectedException) e).getReason())
                .isEqualTo(Reason.CONNECTOR_FAULTED);
    }

    @Test
    @DisplayName("Con la clave equivocada, el cargador no atiende y se lo da por no disponible")
    void wrongToken() {
        assertThatThrownBy(() -> client("otra-clave").activate(order(CONNECTOR, "live-wrong")))
                .isInstanceOf(ChargerUnavailableException.class);
    }
}
