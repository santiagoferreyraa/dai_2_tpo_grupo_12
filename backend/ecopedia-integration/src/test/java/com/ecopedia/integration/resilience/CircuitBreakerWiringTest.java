package com.ecopedia.integration.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.gateway.CircuitBreakerPaymentGateway;
import com.ecopedia.integration.powergrid.domain.PowerGridPort;
import com.ecopedia.integration.powergrid.ws.CircuitBreakerPowerGrid;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Que Pagos y RedEléctrica reciban el puerto con el circuito, y que los valores salgan del
 * application.yml. Sin esto, alguien podría volver a anotar el adaptador como bean y el circuito
 * quedaría armado pero sin que nadie pase por él.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CircuitBreakerWiringTest {

    @Autowired
    private PaymentGatewayPort paymentGateway;

    @Autowired
    private PowerGridPort powerGrid;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("El puerto de la pasarela y el de la distribuidora son los decoradores con el circuito")
    void portsGoThroughTheCircuitBreaker() {
        assertThat(paymentGateway).isInstanceOf(CircuitBreakerPaymentGateway.class);
        assertThat(powerGrid).isInstanceOf(CircuitBreakerPowerGrid.class);
    }

    @Test
    @DisplayName("Los valores del circuito se leen del application.yml")
    void settingsComeFromTheConfiguration() {
        assertThat(CircuitBreakers.settings(environment, "ecopedia.payment-gateway.circuit-breaker"))
                .isEqualTo(new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 3));
        assertThat(CircuitBreakers.settings(environment, "ecopedia.power-grid.circuit-breaker"))
                .isEqualTo(new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 3));
    }

    @Test
    @DisplayName("Un valor cambiado en la configuración se respeta, y lo que falta toma el valor por omisión")
    void partialConfigurationKeepsTheDefaults() {
        StandardEnvironment custom = new StandardEnvironment();
        custom.getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("ecopedia.x.circuit-breaker.wait-in-open-state", "1m")));

        assertThat(CircuitBreakers.settings(custom, "ecopedia.x.circuit-breaker"))
                .isEqualTo(new CircuitBreakerSettings(10, 5, 50, Duration.ofMinutes(1), 3));
    }
}
