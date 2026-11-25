package com.ecopedia.integration.powergrid.ws;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import com.ecopedia.integration.powergrid.domain.PowerGridPort;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;

/**
 * El circuit breaker delante de la distribuidora (ECO-68): un Decorator sobre el Adapter SOAP.
 *
 * <p>Envuelve a {@link PowerGridWsClient} con el mismo {@link PowerGridPort}. Con el circuito abierto
 * no se llama a la distribuidora y se tira la {@link PowerGridUnavailableException} de siempre: para
 * SesionesDeCarga es la misma distribuidora caída de antes, y la carga sale a potencia normal (ECO-52),
 * sin esperar el timeout cada vez. Un Fault del contrato (nodo desconocido, demanda inválida) es una
 * respuesta: no abre el circuito.
 */
public class CircuitBreakerPowerGrid implements PowerGridPort {

    private final PowerGridPort delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerPowerGrid(PowerGridPort delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Feasibility checkFeasibility(String nodeId, BigDecimal requestedPowerKw) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.checkFeasibility(nodeId, requestedPowerKw));
        } catch (CallNotPermittedException open) {
            throw new PowerGridUnavailableException("Circuito abierto: no se consulta a la distribuidora", open);
        }
    }
}
