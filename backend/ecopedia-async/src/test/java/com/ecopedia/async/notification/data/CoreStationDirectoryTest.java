package com.ecopedia.async.notification.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.async.notification.domain.StationInfo;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Pruebas unitarias para {@link CoreStationDirectory} cubriendo resolución normal y fallos contra core.
 */
class CoreStationDirectoryTest {

    private HttpServer server;
    private CoreStationDirectory directory;
    private final Map<String, StubResponse> routes = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            StubResponse response = routes.get(exchange.getRequestURI().getPath());
            if (response == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            if (response.body() == null) {
                exchange.sendResponseHeaders(response.status(), -1);
                exchange.close();
                return;
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        directory = new CoreStationDirectory(
                RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("Devuelve StationInfo completo cuando conector y estación responden 200 OK")
    void findByConnector_returnsStationInfo() {
        routes.put("/api/connectors/7", new StubResponse(200, "{\"id\":7,\"stationId\":3,\"connectorType\":\"CCS2\"}"));
        routes.put(
                "/api/stations/3",
                new StubResponse(200, "{\"id\":3,\"name\":\"Estación Centro\",\"address\":\"Av. 9 de Julio 100\"}"));

        Optional<StationInfo> info = directory.findByConnector(7L);

        assertThat(info).contains(new StationInfo(3L, "Estación Centro", "Av. 9 de Julio 100", "CCS2"));
    }

    @Test
    @DisplayName("Devuelve Optional.empty cuando el conector responde vacío o sin stationId")
    void findByConnector_returnsEmptyWhenConnectorEmptyOrMissingStationId() {
        routes.put("/api/connectors/8", new StubResponse(200, null));
        assertThat(directory.findByConnector(8L)).isEmpty();

        routes.put("/api/connectors/9", new StubResponse(200, "{\"id\":9,\"stationId\":null}"));
        assertThat(directory.findByConnector(9L)).isEmpty();
    }

    @Test
    @DisplayName("Devuelve Optional.empty cuando la estación responde vacío o core contesta 404")
    void findByConnector_returnsEmptyWhenStationEmptyOrCoreError() {
        routes.put("/api/connectors/10", new StubResponse(200, "{\"id\":10,\"stationId\":4}"));
        routes.put("/api/stations/4", new StubResponse(200, null));
        assertThat(directory.findByConnector(10L)).isEmpty();

        assertThat(directory.findByConnector(11L)).isEmpty();
    }

    private record StubResponse(int status, String body) {}
}
