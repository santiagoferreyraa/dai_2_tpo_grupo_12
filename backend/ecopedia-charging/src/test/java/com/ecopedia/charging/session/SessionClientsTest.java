package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.charging.session.data.CoreChargingPointCatalog;
import com.ecopedia.charging.session.data.IntegrationPowerGridDirectory;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalogUnavailableException;
import com.ecopedia.charging.session.domain.GridFeasibility;
import com.ecopedia.charging.session.domain.GridRequestRejectedException;
import com.ecopedia.charging.session.domain.GridUnavailableException;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Los dos adaptadores de SesionesDeCarga sin los otros artefactos: {@link MockRestServiceServer}
 * contesta lo que contestarían integration y core. Que integration conteste así lo prueba
 * {@code InternalPowerGridApiTest} de su lado.
 */
class SessionClientsTest {

    @Test
    @DisplayName("RedEléctrica: pide con la clave de servicio y lee la respuesta, también un \"no\"")
    void gridAnswer() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://integration.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer clave-de-servicio");
        MockRestServiceServer integration =
                MockRestServiceServer.bindTo(builder).build();
        IntegrationPowerGridDirectory grid = new IntegrationPowerGridDirectory(builder.build());

        integration
                .expect(requestTo("http://integration.test/internal/power-grid/feasibility"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer clave-de-servicio"))
                .andExpect(content().json("{\"nodeId\":\"CABA-OESTE\",\"requestedPowerKw\":150}"))
                .andRespond(withSuccess(
                        "{\"feasible\":false,\"maxAllowedPowerKw\":50,\"reason\":\"DEMAND_CAP_EXCEEDED\"}",
                        MediaType.APPLICATION_JSON));

        GridFeasibility answer = grid.checkFeasibility("CABA-OESTE", new BigDecimal("150"));

        assertThat(answer.feasible()).isFalse();
        assertThat(answer.maxAllowedPowerKw()).isEqualByComparingTo("50");
        assertThat(answer.reason()).isEqualTo("DEMAND_CAP_EXCEEDED");
        integration.verify();
    }

    @Test
    @DisplayName("RedEléctrica: un 422 es un rechazo con su código; 503, 401 o sin conexión, no disponible")
    void gridFailures() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://integration.test");
        MockRestServiceServer integration =
                MockRestServiceServer.bindTo(builder).build();
        IntegrationPowerGridDirectory grid = new IntegrationPowerGridDirectory(builder.build());
        String url = "http://integration.test/internal/power-grid/feasibility";

        integration
                .expect(requestTo(url))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"UNKNOWN_NODE\"}"));
        assertThatThrownBy(() -> grid.checkFeasibility("CABA", BigDecimal.TEN))
                .isInstanceOf(GridRequestRejectedException.class)
                .extracting(e -> ((GridRequestRejectedException) e).getCode())
                .isEqualTo("UNKNOWN_NODE");

        for (HttpStatus status : new HttpStatus[] {HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.UNAUTHORIZED}) {
            integration.reset();
            integration.expect(requestTo(url)).andRespond(withStatus(status));
            assertThatThrownBy(() -> grid.checkFeasibility("CABA-CENTRO", BigDecimal.TEN))
                    .isInstanceOf(GridUnavailableException.class);
        }

        integration.reset();
        integration.expect(requestTo(url)).andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(() -> grid.checkFeasibility("CABA-CENTRO", BigDecimal.TEN))
                .isInstanceOf(GridUnavailableException.class);
    }

    @Test
    @DisplayName("Terminales: junta la potencia del conector con el nodo de su estación")
    void chargingPoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
        MockRestServiceServer core = MockRestServiceServer.bindTo(builder).build();
        CoreChargingPointCatalog catalog = new CoreChargingPointCatalog(builder.build());

        core.expect(requestTo("http://core.test/api/connectors/7"))
                .andRespond(withSuccess(
                        "{\"id\":7,\"stationId\":3,\"connectorType\":\"CCS2\",\"maxPowerKw\":150,"
                                + "\"operationalStatus\":\"AVAILABLE\"}",
                        MediaType.APPLICATION_JSON));
        core.expect(requestTo("http://core.test/api/stations/3"))
                .andRespond(withSuccess(
                        "{\"id\":3,\"name\":\"Centro\",\"nodeId\":\"CABA-CENTRO\",\"connectors\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThat(catalog.find(7)).contains(new ChargingPoint(7, 3, new BigDecimal("150"), "CABA-CENTRO"));
    }

    @Test
    @DisplayName("Terminales: estación sin nodo, conector inexistente y core caído")
    void chargingPointEdges() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
        MockRestServiceServer core = MockRestServiceServer.bindTo(builder).build();
        CoreChargingPointCatalog catalog = new CoreChargingPointCatalog(builder.build());

        core.expect(requestTo("http://core.test/api/connectors/8"))
                .andRespond(withSuccess("{\"id\":8,\"stationId\":4,\"maxPowerKw\":50}", MediaType.APPLICATION_JSON));
        core.expect(requestTo("http://core.test/api/stations/4"))
                .andRespond(withSuccess("{\"id\":4,\"nodeId\":null}", MediaType.APPLICATION_JSON));
        assertThat(catalog.find(8))
                .hasValueSatisfying(point -> assertThat(point.nodeId()).isNull());

        core.reset();
        core.expect(requestTo("http://core.test/api/connectors/99")).andRespond(withResourceNotFound());
        assertThat(catalog.find(99)).isEmpty();

        core.reset();
        core.expect(requestTo("http://core.test/api/connectors/7"))
                .andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(() -> catalog.find(7)).isInstanceOf(ChargingPointCatalogUnavailableException.class);
    }
}
