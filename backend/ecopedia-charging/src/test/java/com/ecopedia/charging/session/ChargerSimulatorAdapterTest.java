package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.charging.session.data.ChargerSimulatorAdapter;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Prueba del adaptador contra el simulador de cargadores usando el bean {@code chargerHttpClient}
 * configurado por {@code SessionClientConfig} en el perfil {@code dev}.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ChargerSimulatorAdapterTest {

    @Autowired
    @Qualifier("chargerHttpClient")
    private RestClient chargerHttpClient;

    @Test
    @DisplayName("Respuesta 409 del cargador: envía el token de desarrollo y no filtra el cuerpo en el mensaje")
    void chargerRejectedDoesNotLeakResponseBodyAndSendsAuthorizationHeader() {
        RestClient.Builder builder = chargerHttpClient.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChargerSimulatorAdapter adapter = new ChargerSimulatorAdapter(builder.build());

        server.expect(requestTo("http://localhost:8088/v1/connectors/7/session"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ecopedia-dev-only-charger-token"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"secret\":\"x\"}"));

        assertThatThrownBy(() -> adapter.activate(7L, "session-1", new BigDecimal("50.00")))
                .isInstanceOf(PhysicalChargerRejectedException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("secret"));

        server.verify();
    }

    @Test
    @DisplayName(
            "Falla de red al activar: envía el token y lanza PhysicalChargerUnavailableException sin detalles del transporte")
    void chargerUnavailableOnIoFailure() {
        RestClient.Builder builder = chargerHttpClient.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChargerSimulatorAdapter adapter = new ChargerSimulatorAdapter(builder.build());

        server.expect(requestTo("http://localhost:8088/v1/connectors/7/session"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ecopedia-dev-only-charger-token"))
                .andRespond(withException(new IOException("Connection refused: internal-host")));

        assertThatThrownBy(() -> adapter.activate(7L, "session-1", new BigDecimal("50.00")))
                .isInstanceOf(PhysicalChargerUnavailableException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("internal-host"));

        server.verify();
    }

    @Test
    @DisplayName("Activación exitosa envía cabecera Authorization del perfil dev")
    void chargerActivateSuccess() {
        RestClient.Builder builder = chargerHttpClient.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChargerSimulatorAdapter adapter = new ChargerSimulatorAdapter(builder.build());

        server.expect(requestTo("http://localhost:8088/v1/connectors/7/session"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ecopedia-dev-only-charger-token"))
                .andRespond(withSuccess());

        adapter.activate(7L, "session-1", new BigDecimal("50.00"));

        server.verify();
    }

    @Test
    @DisplayName("Desactivar cargador con 200 OK devuelve los kWh entregados")
    void chargerDeactivateReturnsDeliveredKwhOnOk() {
        RestClient.Builder builder = chargerHttpClient.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChargerSimulatorAdapter adapter = new ChargerSimulatorAdapter(builder.build());

        server.expect(requestTo("http://localhost:8088/v1/connectors/7/session"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ecopedia-dev-only-charger-token"))
                .andRespond(
                        withSuccess("{\"sessionId\":\"session-1\",\"energyKwh\":18.450}", MediaType.APPLICATION_JSON));

        assertThat(adapter.deactivate(7L)).contains(new BigDecimal("18.450"));
        server.verify();
    }

    @Test
    @DisplayName("Desactivar cargador con 204 No Content devuelve Optional vacío")
    void chargerDeactivateReturnsEmptyOnNoContent() {
        RestClient.Builder builder = chargerHttpClient.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChargerSimulatorAdapter adapter = new ChargerSimulatorAdapter(builder.build());

        server.expect(requestTo("http://localhost:8088/v1/connectors/7/session"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ecopedia-dev-only-charger-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(adapter.deactivate(7L)).isEmpty();
        server.verify();
    }
}
