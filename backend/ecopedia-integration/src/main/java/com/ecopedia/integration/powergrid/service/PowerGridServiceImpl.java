package com.ecopedia.integration.powergrid.service;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import com.ecopedia.integration.powergrid.domain.PowerGridPort;
import com.ecopedia.integration.powergrid.domain.PowerGridService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * RedEléctrica: hoy delega en el puerto y nada más.
 *
 * <p>Es delgado a propósito. La regla de qué hacer con cada resultado —cargar a menos potencia con
 * un "no", qué hacer si no contesta, el circuit breaker— es de SesionesDeCarga (ECO-52) y del ticket
 * de resiliencia del Sprint 5. Lo que este componente garantiza es que nadie más sabe que del otro
 * lado hay SOAP.
 */
@Service
public class PowerGridServiceImpl implements PowerGridService {

    private final PowerGridPort powerGrid;

    public PowerGridServiceImpl(PowerGridPort powerGrid) {
        this.powerGrid = powerGrid;
    }

    @Override
    public Feasibility checkFeasibility(String nodeId, BigDecimal requestedPowerKw) {
        return powerGrid.checkFeasibility(nodeId, requestedPowerKw);
    }
}
