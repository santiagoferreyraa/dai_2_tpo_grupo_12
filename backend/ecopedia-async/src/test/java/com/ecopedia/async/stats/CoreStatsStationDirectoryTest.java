package com.ecopedia.async.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.async.stats.data.CoreStatsStationDirectory;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoreStatsStationDirectoryTest {

    private MockRestServiceServer server;
    private CoreStatsStationDirectory directory;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.local");
        server = MockRestServiceServer.bindTo(builder).build();
        directory = new CoreStatsStationDirectory(builder.build());
    }

    @Test
    @DisplayName("200 OK completo devuelve StationOwnershipInfo con ownerId, nombre y tipo de conector")
    void findByConnector_returnsFullOwnershipInfo() {
        server.expect(requestTo("http://core.local/api/connectors/7"))
                .andRespond(withSuccess(
                        "{\"id\":7,\"stationId\":3,\"connectorType\":\"CCS2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://core.local/api/stations/3"))
                .andRespond(withSuccess(
                        "{\"id\":3,\"name\":\"Estación Obelisco\",\"ownerId\":42}", MediaType.APPLICATION_JSON));

        Optional<StationOwnershipInfo> result = directory.findByConnector(7L);

        assertThat(result).contains(new StationOwnershipInfo(3L, "Estación Obelisco", 42L, "CCS2"));
        server.verify();
    }

    @Test
    @DisplayName("Conector sin stationId devuelve Optional.empty")
    void findByConnector_withoutStationIdReturnsEmpty() {
        server.expect(requestTo("http://core.local/api/connectors/8"))
                .andRespond(withSuccess("{\"id\":8,\"stationId\":null}", MediaType.APPLICATION_JSON));

        assertThat(directory.findByConnector(8L)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Estación 404 devuelve Optional.empty")
    void findByConnector_station404ReturnsEmpty() {
        server.expect(requestTo("http://core.local/api/connectors/9"))
                .andRespond(withSuccess(
                        "{\"id\":9,\"stationId\":99,\"connectorType\":\"TYPE2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://core.local/api/stations/99")).andRespond(withResourceNotFound());

        assertThat(directory.findByConnector(9L)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Error de red o timeout contra core degrada graciosamente a Optional.empty")
    void findByConnector_timeoutReturnsEmpty() {
        server.expect(requestTo("http://core.local/api/connectors/10")).andRespond(request -> {
            throw new IOException("Read timed out");
        });

        assertThat(directory.findByConnector(10L)).isEmpty();
        server.verify();
    }
}
