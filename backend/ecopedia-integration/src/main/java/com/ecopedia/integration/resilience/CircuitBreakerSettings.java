package com.ecopedia.integration.resilience;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cuándo se abre el circuito hacia un sistema externo y cuánto queda abierto (ECO-68).
 *
 * <p>Se leen del {@code application.yml}, uno por sistema ({@code ecopedia.payment-gateway.circuit-breaker}
 * y {@code ecopedia.power-grid.circuit-breaker}). Con los valores por omisión: se mira el resultado de
 * los últimos 10 pedidos y, desde que hay al menos 5, si la mitad o más no tuvo respuesta, el circuito
 * se abre; queda abierto 30 s sin llamar a nadie, y después deja pasar 3 pedidos de prueba para decidir
 * si se cierra o vuelve a abrirse.
 *
 * @param slidingWindowSize cuántos pedidos recientes se miran
 * @param minimumCalls cuántos pedidos hacen falta antes de poder abrirse
 * @param failureRatePercent el porcentaje de pedidos sin respuesta que lo abre
 * @param waitInOpenState cuánto queda abierto antes de probar de nuevo
 * @param callsInHalfOpenState cuántos pedidos de prueba deja pasar al volver a probar
 */
public record CircuitBreakerSettings(
        @DefaultValue("10") int slidingWindowSize,
        @DefaultValue("5") int minimumCalls,
        @DefaultValue("50") float failureRatePercent,
        @DefaultValue("30s") Duration waitInOpenState,
        @DefaultValue("3") int callsInHalfOpenState) {}
