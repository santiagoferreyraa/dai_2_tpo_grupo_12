package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.GridFeasibility;
import com.ecopedia.charging.session.domain.GridRequestRejectedException;
import com.ecopedia.charging.session.domain.GridUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.PowerDecision.Outcome;
import com.ecopedia.charging.session.domain.PowerGridDirectory;
import com.ecopedia.charging.session.service.ChargingPowerPolicy;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La regla de potencia, caso por caso, con la distribuidora reemplazada por lo que contestaría.
 * Cada caso es una de las decisiones de Manuel del 28/10 (ARQUITECTURA, ficha 7).
 */
class ChargingPowerPolicyTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("22");
    private static final BigDecimal NORMAL = new BigDecimal("22");

    private static ChargingPoint fastConnector(String nodeId) {
        return new ChargingPoint(7, 3, new BigDecimal("150"), nodeId);
    }

    private static PowerDecision decide(PowerGridDirectory grid, ChargingPoint point) {
        return new ChargingPowerPolicy(grid, THRESHOLD, NORMAL).decide(point);
    }

    private static PowerGridDirectory answering(GridFeasibility answer) {
        return (node, power) -> answer;
    }

    @Test
    @DisplayName("Hasta 22 kW es carga normal: no se le pregunta a la distribuidora")
    void standardChargeDoesNotAsk() {
        AtomicInteger calls = new AtomicInteger();
        PowerGridDirectory grid = (node, power) -> {
            calls.incrementAndGet();
            throw new AssertionError("no tenía que preguntar");
        };

        PowerDecision decision = decide(grid, new ChargingPoint(7, 3, new BigDecimal("22"), "CABA-CENTRO"));

        assertThat(decision.outcome()).isEqualTo(Outcome.STANDARD_CHARGE);
        assertThat(decision.powerKw()).isEqualByComparingTo("22");
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("Un \"sí\" carga a la potencia del conector, y pregunta por esa potencia")
    void fastAuthorized() {
        PowerGridDirectory grid = (node, power) -> {
            assertThat(node).isEqualTo("CABA-CENTRO");
            assertThat(power).isEqualByComparingTo("150");
            return new GridFeasibility(true, new BigDecimal("600"), null);
        };

        PowerDecision decision = decide(grid, fastConnector("CABA-CENTRO"));

        assertThat(decision.outcome()).isEqualTo(Outcome.FAST_AUTHORIZED);
        assertThat(decision.powerKw()).isEqualByComparingTo("150");
        assertThat(decision.canStart()).isTrue();
    }

    @Test
    @DisplayName("Un \"no\" con tope carga hasta el tope, sin reintegro")
    void limitedByGrid() {
        PowerDecision decision = decide(
                answering(new GridFeasibility(false, new BigDecimal("50"), "DEMAND_CAP_EXCEEDED")),
                fastConnector("CABA-OESTE"));

        assertThat(decision.outcome()).isEqualTo(Outcome.LIMITED_BY_GRID);
        assertThat(decision.powerKw()).isEqualByComparingTo("50");
        assertThat(decision.canStart()).isTrue();
    }

    @Test
    @DisplayName("Un \"no\" con tope 0 es la falla de RF13: la carga no puede iniciarse")
    void rejectedByGrid() {
        PowerDecision decision = decide(
                answering(new GridFeasibility(false, BigDecimal.ZERO, "MAINTENANCE_WINDOW")),
                fastConnector("CABA-SUR"));

        assertThat(decision.outcome()).isEqualTo(Outcome.REJECTED_BY_GRID);
        assertThat(decision.powerKw()).isEqualByComparingTo("0");
        assertThat(decision.canStart()).isFalse();
    }

    @Test
    @DisplayName("Una estación sin nodo no ofrece carga rápida: potencia normal, sin preguntar")
    void stationWithoutNode() {
        PowerGridDirectory grid = (node, power) -> {
            throw new AssertionError("no tenía que preguntar");
        };

        PowerDecision decision = decide(grid, fastConnector(null));

        assertThat(decision.outcome()).isEqualTo(Outcome.NO_FAST_CHARGE_AT_STATION);
        assertThat(decision.powerKw()).isEqualByComparingTo("22");
    }

    @Test
    @DisplayName("Sin respuesta, nodo desconocido o pedido inválido: potencia normal, y la carga arranca")
    void noValidAnswerMeansNormalPower() {
        PowerGridDirectory down = (node, power) -> {
            throw new GridUnavailableException("caída", null);
        };
        PowerGridDirectory unknown = (node, power) -> {
            throw new GridRequestRejectedException("UNKNOWN_NODE");
        };
        PowerGridDirectory invalid = (node, power) -> {
            throw new GridRequestRejectedException("INVALID_DEMAND");
        };

        assertThat(decide(down, fastConnector("LA-PLATA")))
                .isEqualTo(new PowerDecision(new BigDecimal("22"), Outcome.GRID_UNAVAILABLE));
        assertThat(decide(unknown, fastConnector("CABA")))
                .isEqualTo(new PowerDecision(new BigDecimal("22"), Outcome.UNKNOWN_NODE));
        assertThat(decide(invalid, fastConnector("CABA-CENTRO")))
                .isEqualTo(new PowerDecision(new BigDecimal("22"), Outcome.GRID_REQUEST_ERROR));
    }

    @Test
    @DisplayName("Un tope mayor que el conector no sube la potencia por encima de la del conector")
    void capNeverExceedsConnector() {
        PowerDecision decision = decide(
                answering(new GridFeasibility(false, new BigDecimal("300"), "DEMAND_CAP_EXCEEDED")),
                fastConnector("CABA-NORTE"));

        assertThat(decision.powerKw()).isEqualByComparingTo("150");
    }
}
