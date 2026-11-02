package com.ecopedia.core.terminal.charger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.core.terminal.domain.ChargerReading;
import com.ecopedia.core.terminal.domain.ChargerRejectedException;
import com.ecopedia.core.terminal.domain.ChargerRejectedException.Reason;
import com.ecopedia.core.terminal.domain.ChargerUnavailableException;
import com.ecopedia.core.terminal.domain.ChargingOrder;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El adaptador contra los cargadores, sin el simulador: {@link MockRestServiceServer} contesta lo
 * que contestaría él. Lo que se prueba es la traducción —el pedido que sale, y cada respuesta a
 * {@link ChargerReading}, a un rechazo con su motivo o a "no disponible"—. Que el simulador conteste
 * así lo prueban sus propios tests, y contra el proceso real, {@code ChargerLiveTest}.
 */
class ChargerRestClientTest {

    private static final ChargingOrder ORDER =
            new ChargingOrder(7, "session-1", new BigDecimal("50"), new BigDecimal("60"), new BigDecimal("100"));

    private MockRestServiceServer charger;
    private ChargerRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://charger.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer clave-de-prueba");
        charger = MockRestServiceServer.bindTo(builder).build();
        client = new ChargerRestClient(builder.build());
    }

    private static String error(int status, String code) {
        return """
                {"status":%d,"code":"%s","message":"x"}
                """.formatted(status, code);
    }

    @Test
    @DisplayName("Enciende con la clave de acceso y devuelve cómo arrancó la sesión")
    void activates() {
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer clave-de-prueba"))
                .andExpect(
                        content()
                                .json(
                                        """
                        {"sessionId":"session-1","powerLimitKw":50,"batteryCapacityKwh":60,"vehicleMaxChargeKw":100}
                        """))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                {"connectorId":7,"status":"CHARGING","session":{"sessionId":"session-1",
                                 "powerKw":50.00,"energyKwh":0.000,"batteryPercent":23.0}}
                                """));

        ChargerReading reading = client.activate(ORDER);

        assertThat(reading.sessionId()).isEqualTo("session-1");
        assertThat(reading.powerKw()).isEqualByComparingTo("50");
        assertThat(reading.energyKwh()).isEqualByComparingTo("0");
        assertThat(reading.batteryPercent()).isEqualByComparingTo("23");
        charger.verify();
    }

    @Test
    @DisplayName("Un conector roto u ocupado vuelve como rechazo, con el motivo")
    void rejections() {
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error(409, "connector_faulted")));
        assertThatThrownBy(() -> client.activate(ORDER))
                .isInstanceOf(ChargerRejectedException.class)
                .extracting(e -> ((ChargerRejectedException) e).getReason())
                .isEqualTo(Reason.CONNECTOR_FAULTED);

        charger.reset();
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error(409, "connector_busy")));
        assertThatThrownBy(() -> client.activate(ORDER))
                .isInstanceOf(ChargerRejectedException.class)
                .extracting(e -> ((ChargerRejectedException) e).getReason())
                .isEqualTo(Reason.CONNECTOR_BUSY);

        charger.reset();
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error(400, "invalid_request")));
        assertThatThrownBy(() -> client.activate(ORDER))
                .isInstanceOf(ChargerRejectedException.class)
                .extracting(e -> ((ChargerRejectedException) e).getReason())
                .isEqualTo(Reason.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Caído, sin telemetría (503) o rechazando la clave: no está disponible")
    void unavailable() {
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(() -> client.activate(ORDER)).isInstanceOf(ChargerUnavailableException.class);

        charger.reset();
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error(503, "telemetry_unavailable")));
        assertThatThrownBy(() -> client.activate(ORDER)).isInstanceOf(ChargerUnavailableException.class);

        charger.reset();
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.activate(ORDER)).isInstanceOf(ChargerUnavailableException.class);
    }

    @Test
    @DisplayName("Apagar devuelve cómo terminó la sesión, o vacío si el conector no estaba cargando")
    void deactivates() {
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(
                        """
                        {"sessionId":"session-1","powerKw":50.00,"energyKwh":12.500,"batteryPercent":43.8}
                        """,
                        MediaType.APPLICATION_JSON));

        Optional<ChargerReading> last = client.deactivate(7);

        assertThat(last).isPresent();
        assertThat(last.get().energyKwh()).isEqualByComparingTo("12.5");

        charger.reset();
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session")).andRespond(withNoContent());

        assertThat(client.deactivate(7)).isEmpty();
    }

    @Test
    @DisplayName("Apagar con el cargador caído: no está disponible")
    void deactivateUnavailable() {
        charger.expect(requestTo("http://charger.test/v1/connectors/7/session"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.deactivate(7)).isInstanceOf(ChargerUnavailableException.class);
    }
}
