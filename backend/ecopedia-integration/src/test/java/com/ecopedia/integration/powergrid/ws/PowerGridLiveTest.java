package com.ecopedia.integration.powergrid.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException.Reason;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * El adaptador contra la distribuidora de verdad: el proceso de {@code simulation/power-grid}
 * levantado en el 8087.
 *
 * <p><b>Corre solo a pedido</b>, porque necesita ese proceso arriba y CI no lo levanta. Con la
 * distribuidora corriendo ({@code pnpm dev:grid}, o el panel {@code grid} de {@code pnpm dev}):
 *
 * <pre>mvn -pl backend/ecopedia-integration test -Dtest=PowerGridLiveTest -Dpower-grid.live=true</pre>
 *
 * <p>Usa la contraseña de desarrollo; contra {@code pnpm demo}, se pasa la del .env con
 * {@code -Dpower-grid.password=...}. Es lo que reemplaza a probarlo desde una pantalla: en esta
 * pieza nadie llama todavía a RedEléctrica (eso es ECO-52), y un endpoint de prueba contradiría que
 * el componente no tiene capa de presentación.
 */
@EnabledIfSystemProperty(named = "power-grid.live", matches = "true")
class PowerGridLiveTest {

    private static final String URL = System.getProperty("power-grid.url", "http://localhost:8087/ws");
    private static final String PASSWORD =
            System.getProperty("power-grid.password", "ecopedia-dev-only-power-grid-password");

    private static PowerGridWsClient client(String password) throws Exception {
        return new PowerGridWsClient(PowerGridClientConfig.buildTemplate(
                URL, "ecopedia", password, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("Un nodo normal autoriza hasta su capacidad")
    void normalNodeAuthorizes() throws Exception {
        Feasibility result = client(PASSWORD).checkFeasibility("CABA-CENTRO", new BigDecimal("150"));

        assertThat(result.feasible()).isTrue();
        assertThat(result.maxAllowedPowerKw()).isEqualByComparingTo("600");
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("El nodo con tope de demanda rechaza lo que lo supera y dice hasta cuánto")
    void demandCapRejects() throws Exception {
        Feasibility result = client(PASSWORD).checkFeasibility("CABA-OESTE", new BigDecimal("150"));

        assertThat(result.feasible()).isFalse();
        assertThat(result.maxAllowedPowerKw()).isEqualByComparingTo("50");
        assertThat(result.reason()).isEqualTo(Feasibility.Restriction.DEMAND_CAP_EXCEEDED);
    }

    @Test
    @DisplayName("El nodo en mantenimiento no autoriza nada")
    void maintenanceRejects() throws Exception {
        Feasibility result = client(PASSWORD).checkFeasibility("CABA-SUR", new BigDecimal("22"));

        assertThat(result.feasible()).isFalse();
        assertThat(result.reason()).isEqualTo(Feasibility.Restriction.MAINTENANCE_WINDOW);
    }

    @Test
    @DisplayName("Un nodo que no existe vuelve como UnknownNodeFault")
    void unknownNode() throws Exception {
        PowerGridWsClient client = client(PASSWORD);

        assertThatThrownBy(() -> client.checkFeasibility("NODO-X", new BigDecimal("50")))
                .isInstanceOf(PowerGridRequestRejectedException.class)
                .extracting(e -> ((PowerGridRequestRejectedException) e).getReason())
                .isEqualTo(Reason.UNKNOWN_NODE);
    }

    @Test
    @DisplayName("Una potencia en cero vuelve como InvalidDemandFault")
    void invalidDemand() throws Exception {
        PowerGridWsClient client = client(PASSWORD);

        assertThatThrownBy(() -> client.checkFeasibility("CABA-CENTRO", BigDecimal.ZERO))
                .isInstanceOf(PowerGridRequestRejectedException.class)
                .extracting(e -> ((PowerGridRequestRejectedException) e).getReason())
                .isEqualTo(Reason.INVALID_DEMAND);
    }

    @Test
    @DisplayName("Con la contraseña equivocada, la distribuidora no atiende y se la da por no disponible")
    void wrongPassword() throws Exception {
        PowerGridWsClient client = client("otra-contrasena");

        assertThatThrownBy(() -> client.checkFeasibility("CABA-CENTRO", new BigDecimal("50")))
                .isInstanceOf(PowerGridUnavailableException.class);
    }

    /* El nodo lento tarda 8 s; el cliente se rinde a los 5, sin reintentar. */
    @Test
    @DisplayName("El nodo lento pasa el timeout de lectura y se lo da por no disponible a los 5 s")
    void slowNodeTimesOut() throws Exception {
        PowerGridWsClient client = client(PASSWORD);
        long start = System.nanoTime();

        assertThatThrownBy(() -> client.checkFeasibility("LA-PLATA", new BigDecimal("50")))
                .isInstanceOf(PowerGridUnavailableException.class);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isBetween(Duration.ofMillis(4500), Duration.ofMillis(7000));
    }
}
