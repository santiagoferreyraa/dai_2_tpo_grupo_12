package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.charging.session.data.CoreChargingPointCatalog;
import com.ecopedia.charging.session.data.IntegrationPowerGridDirectory;
import com.ecopedia.charging.session.data.SessionClientConfig;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.PowerDecision.Outcome;
import com.ecopedia.charging.session.service.ChargingPowerPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

/**
 * La regla de potencia contra los procesos de verdad: SesionesDeCarga → RedEléctrica
 * (ecopedia-integration, 8083) → la distribuidora simulada (8087), y Terminales (core, 8081).
 *
 * <p><b>Corre solo a pedido</b>, con {@code pnpm dev} arriba:
 *
 * <pre>mvn -pl backend/ecopedia-charging test -Dtest=PowerGridFeasibilityLiveTest -Dpower-grid.live=true</pre>
 *
 * <p>Usa la clave de servicio de desarrollo; si el .env tiene {@code ECOPEDIA_INTERNAL_TOKEN},
 * integration usa esa, y hay que pasarla con {@code -Dinternal.token=...}. Los nodos son los del
 * catálogo de la distribuidora (README).
 */
@EnabledIfSystemProperty(named = "power-grid.live", matches = "true")
class PowerGridFeasibilityLiveTest {

    private static final String INTEGRATION = System.getProperty("integration.url", "http://localhost:8083");
    private static final String CORE = System.getProperty("core.url", "http://localhost:8081");
    private static final String TOKEN = System.getProperty("internal.token", "ecopedia-dev-only-internal-token");

    private static ChargingPowerPolicy policy(String token) {
        RestClient client =
                new SessionClientConfig().integrationInternalHttpClient(RestClient.builder(), INTEGRATION, token);
        return new ChargingPowerPolicy(
                new IntegrationPowerGridDirectory(client), new BigDecimal("22"), new BigDecimal("22"));
    }

    private static PowerDecision decide(String nodeId) {
        return policy(TOKEN).decide(new ChargingPoint(990_001, 990_001, new BigDecimal("150"), nodeId));
    }

    /* Potencia con isEqualByComparingTo: lo que viene de la red puede llegar como 50 o como 50.00. */
    private static void expect(PowerDecision decision, Outcome outcome, String powerKw) {
        assertThat(decision.outcome()).isEqualTo(outcome);
        assertThat(decision.powerKw()).isEqualByComparingTo(powerKw);
    }

    @Test
    @DisplayName("Cada nodo del catálogo termina en la decisión que le toca")
    void eachNode() {
        expect(decide("CABA-CENTRO"), Outcome.FAST_AUTHORIZED, "150");
        expect(decide("CABA-OESTE"), Outcome.LIMITED_BY_GRID, "50");
        expect(decide("CABA-SUR"), Outcome.REJECTED_BY_GRID, "0");
        expect(decide("NODO-X"), Outcome.UNKNOWN_NODE, "22");
    }

    /* La distribuidora tarda 8 s con LA-PLATA; RedEléctrica se rinde a los 5 y contesta 503. */
    @Test
    @DisplayName("El nodo lento: RedEléctrica corta a los 5 s y la carga va a potencia normal")
    void slowNode() {
        long start = System.nanoTime();

        expect(decide("LA-PLATA"), Outcome.GRID_UNAVAILABLE, "22");

        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isBetween(Duration.ofMillis(4500), Duration.ofMillis(7500));
    }

    @Test
    @DisplayName("Con la clave de servicio equivocada, RedEléctrica no atiende: potencia normal")
    void wrongServiceToken() {
        PowerDecision decision =
                policy("otra-clave").decide(new ChargingPoint(990_001, 990_001, new BigDecimal("150"), "CABA-CENTRO"));

        assertThat(decision.outcome()).isEqualTo(Outcome.GRID_UNAVAILABLE);
    }

    @Test
    @DisplayName("Terminales: un conector del seed trae su potencia y el nodo de su estación")
    void seedChargingPoint() {
        RestClient core = new SessionClientConfig().coreTerminalHttpClient(RestClient.builder(), CORE);
        JsonNode station = core.get().uri("/api/stations/9").retrieve().body(JsonNode.class);
        long connectorId = station.get("connectors").get(0).get("id").asLong();

        ChargingPoint point =
                new CoreChargingPointCatalog(core).find(connectorId).orElseThrow();

        assertThat(point.stationId()).isEqualTo(9);
        assertThat(point.nodeId()).isEqualTo("CABA-OESTE");
        assertThat(point.maxPowerKw()).isPositive();
    }
}
