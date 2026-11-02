package com.ecopedia.simulation.powergrid.domain;

import java.math.BigDecimal;

/**
 * La potencia pedida es nula, negativa o más de lo que maneja un nodo. Sale como
 * {@code InvalidDemandFault}.
 */
public class InvalidDemandException extends RuntimeException {

    private final BigDecimal requestedPowerKw;

    public InvalidDemandException(BigDecimal requestedPowerKw) {
        super("Requested power must be greater than zero and at most " + GridNodeCatalog.MAX_SUPPORTED_POWER_KW
                + " kW");
        this.requestedPowerKw = requestedPowerKw;
    }

    public BigDecimal getRequestedPowerKw() {
        return requestedPowerKw;
    }
}
