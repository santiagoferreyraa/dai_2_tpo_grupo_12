package com.ecopedia.integration.powergrid.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.integration.powergrid.domain.PowerGridPort;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException.Reason;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import com.ecopedia.integration.resilience.CircuitBreakerSettings;
import com.ecopedia.integration.resilience.CircuitBreakers;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El circuit breaker delante de la distribuidora (ECO-68), con la distribuidora reemplazada por un mock. */
class CircuitBreakerPowerGridTest {

    private static final CircuitBreakerSettings SETTINGS =
            new CircuitBreakerSettings(10, 5, 50, Duration.ofMillis(200), 3);
    private static final BigDecimal DEMAND = new BigDecimal("150");

    private PowerGridPort powerGrid;
    private CircuitBreaker circuitBreaker;
    private CircuitBreakerPowerGrid resilient;

    @BeforeEach
    void setUp() {
        powerGrid = mock(PowerGridPort.class);
        circuitBreaker =
                CircuitBreakers.create("power-grid", "la distribuidora", SETTINGS, PowerGridUnavailableException.class);
        resilient = new CircuitBreakerPowerGrid(powerGrid, circuitBreaker);
    }

    @Test
    @DisplayName("Con la distribuidora caída se abre, y abierto no la llama: sale el mismo \"no disponible\"")
    void opensAndStopsCalling() {
        when(powerGrid.checkFeasibility(anyString(), any()))
                .thenThrow(new PowerGridUnavailableException("La distribuidora no contestó", null));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> resilient.checkFeasibility("CABA-OESTE", DEMAND))
                    .isInstanceOf(PowerGridUnavailableException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        reset(powerGrid);

        assertThatThrownBy(() -> resilient.checkFeasibility("CABA-OESTE", DEMAND))
                .isInstanceOf(PowerGridUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        verify(powerGrid, never()).checkFeasibility(anyString(), any());
    }

    @Test
    @DisplayName("Un nodo desconocido es una respuesta de la distribuidora: no abre el circuito")
    void contractFaultsDoNotOpenIt() {
        when(powerGrid.checkFeasibility(anyString(), any()))
                .thenThrow(new PowerGridRequestRejectedException(Reason.UNKNOWN_NODE, "Nodo desconocido", null));

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> resilient.checkFeasibility("NO-EXISTE", DEMAND))
                    .isInstanceOf(PowerGridRequestRejectedException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(powerGrid, times(10)).checkFeasibility("NO-EXISTE", DEMAND);
    }
}
