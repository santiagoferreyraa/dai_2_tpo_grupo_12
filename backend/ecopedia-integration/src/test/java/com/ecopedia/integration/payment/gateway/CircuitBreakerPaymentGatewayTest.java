package com.ecopedia.integration.payment.gateway;

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

import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.resilience.CircuitBreakerSettings;
import com.ecopedia.integration.resilience.CircuitBreakers;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El circuit breaker delante de la pasarela (ECO-68), con la pasarela reemplazada por un mock. Lo que
 * se prueba es el circuito: que se abra cuando la pasarela no contesta, que no se abra por un rechazo
 * de la tarjeta, que abierto no la llame y que se vuelva a cerrar cuando contesta de nuevo.
 */
class CircuitBreakerPaymentGatewayTest {

    /** Los valores del application.yml, salvo la espera, que se acorta para no frenar el test. */
    private static final CircuitBreakerSettings SETTINGS =
            new CircuitBreakerSettings(10, 5, 50, Duration.ofMillis(200), 3);

    private PaymentGatewayPort gateway;
    private CircuitBreaker circuitBreaker;
    private CircuitBreakerPaymentGateway resilient;

    @BeforeEach
    void setUp() {
        gateway = mock(PaymentGatewayPort.class);
        circuitBreaker = CircuitBreakers.create(
                "payment-gateway", "la pasarela", SETTINGS, PaymentGatewayUnavailableException.class);
        resilient = new CircuitBreakerPaymentGateway(gateway, circuitBreaker);
    }

    private void gatewayDown() {
        when(gateway.validate(anyString())).thenThrow(new PaymentGatewayUnavailableException(null));
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new PaymentGatewayUnavailableException(null));
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> resilient.validate("tok")).isInstanceOf(PaymentGatewayUnavailableException.class);
        }
    }

    @Test
    @DisplayName("Con la pasarela caída, a la quinta falla el circuito se abre")
    void opensAfterEnoughFailures() {
        gatewayDown();

        failTimes(4);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        failTimes(1);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Abierto, no llama a la pasarela y contesta el mismo \"no disponible\" de siempre")
    void openDoesNotCallTheGateway() {
        gatewayDown();
        failTimes(5);
        reset(gateway);

        assertThatThrownBy(() -> resilient.charge("tok", BigDecimal.TEN, "seña", "reserva 1", "clave-1"))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class)
                .hasMessageContaining("Probá de nuevo");
        assertThatThrownBy(() -> resilient.validate("tok")).isInstanceOf(PaymentGatewayUnavailableException.class);

        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
        verify(gateway, never()).validate(anyString());
    }

    @Test
    @DisplayName("Una tarjeta rechazada es una respuesta: por más rechazos que haya, no abre el circuito")
    void rejectionsDoNotOpenIt() {
        when(gateway.tokenize(any())).thenThrow(new IllegalArgumentException("La tarjeta está vencida"));
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new GatewayCharge("p-1", false, "cc_rejected_insufficient_amount"));
        CardData card = mock(CardData.class);

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> resilient.tokenize(card)).isInstanceOf(IllegalArgumentException.class);
            assertThat(resilient
                            .charge("tok", BigDecimal.TEN, "seña", "reserva 1", "clave-" + i)
                            .approved())
                    .isFalse();
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(gateway, times(10)).tokenize(card);
    }

    @Test
    @DisplayName("Pasada la espera, prueba de nuevo y, si la pasarela contesta, se cierra")
    void closesWhenTheGatewayIsBack() throws InterruptedException {
        gatewayDown();
        failTimes(5);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        reset(gateway);
        Thread.sleep(SETTINGS.waitInOpenState().toMillis() + 50);

        for (int i = 0; i < SETTINGS.callsInHalfOpenState(); i++) {
            resilient.validate("tok");
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(gateway, times(SETTINGS.callsInHalfOpenState())).validate("tok");
    }

    @Test
    @DisplayName("Si al volver a probar la pasarela sigue caída, se abre de nuevo")
    void reopensWhenTheGatewayIsStillDown() throws InterruptedException {
        gatewayDown();
        failTimes(5);
        Thread.sleep(SETTINGS.waitInOpenState().toMillis() + 50);

        failTimes(SETTINGS.callsInHalfOpenState());

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
