package com.ecopedia.integration.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Arma el circuit breaker de cada sistema externo (ECO-68).
 *
 * <p><b>Para qué está.</b> Con un timeout solo, una pasarela colgada hace esperar 5 s a cada conductor
 * que intenta reservar, y Pagos le sigue mandando pedidos a un sistema que no contesta. Con el
 * circuito, después de varias fallas seguidas se deja de llamar: los pedidos siguientes reciben al
 * instante el mismo "no disponible" que ya sabían manejar, y pasado un rato se prueba de nuevo.
 *
 * <p><b>Solo cuenta como falla la falta de respuesta</b> (la excepción de "no disponible" de cada
 * puerto). Una tarjeta rechazada o un nodo desconocido son respuestas del sistema externo, que está
 * andando: no lo abren.
 *
 * <p>Cada cambio de estado deja un WARN, para que en la demo se vea cuándo se abre y cuándo se cierra.
 */
public final class CircuitBreakers {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakers.class);

    private CircuitBreakers() {}

    /** Lee la configuración de un sistema, con los valores por omisión de {@link CircuitBreakerSettings}. */
    public static CircuitBreakerSettings settings(Environment environment, String prefix) {
        return Binder.get(environment).bindOrCreate(prefix, CircuitBreakerSettings.class);
    }

    /**
     * @param name el nombre técnico del circuito
     * @param label cómo se lo nombra en el log ("la pasarela de pagos")
     * @param failure la excepción que significa "no contestó"; cualquier otra cuenta como respuesta
     */
    public static CircuitBreaker create(
            String name, String label, CircuitBreakerSettings settings, Class<? extends Throwable> failure) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRatePercent())
                .waitDurationInOpenState(settings.waitInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.callsInHalfOpenState())
                .recordExceptions(failure)
                .build();

        CircuitBreaker breaker = CircuitBreaker.of(name, config);
        breaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition transition = event.getStateTransition();
            log.warn(
                    "Circuito de {}: {} -> {}",
                    label,
                    describe(transition.getFromState()),
                    describe(transition.getToState()));
        });
        return breaker;
    }

    private static String describe(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> "cerrado (se llama normalmente)";
            case OPEN -> "abierto (no se llama: se contesta \"no disponible\" al instante)";
            case HALF_OPEN -> "semiabierto (se prueba con unos pocos pedidos)";
            default -> state.name();
        };
    }
}
