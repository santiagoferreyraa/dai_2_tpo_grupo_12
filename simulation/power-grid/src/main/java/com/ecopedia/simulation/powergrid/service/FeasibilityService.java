package com.ecopedia.simulation.powergrid.service;

import com.ecopedia.simulation.powergrid.domain.Feasibility;
import com.ecopedia.simulation.powergrid.domain.Feasibility.Restriction;
import com.ecopedia.simulation.powergrid.domain.GridNodeCatalog;
import com.ecopedia.simulation.powergrid.domain.GridNodeCatalog.GridNode;
import com.ecopedia.simulation.powergrid.domain.InvalidDemandException;
import com.ecopedia.simulation.powergrid.domain.NodeBehavior;
import com.ecopedia.simulation.powergrid.domain.UnknownNodeException;
import java.math.BigDecimal;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * La regla de factibilidad de la distribuidora: ¿el nodo aguanta la potencia pedida?
 *
 * <p>No guarda nada: cada consulta se contesta mirando el catálogo. Una distribuidora real miraría
 * la demanda del momento; acá el comportamiento de cada nodo está fijo, para poder mostrarlo.
 */
@Service
public class FeasibilityService {

    private static final Logger log = LoggerFactory.getLogger(FeasibilityService.class);

    private final Duration slowNodeDelay;

    public FeasibilityService(@Value("${simulation.power-grid.slow-node-delay}") Duration slowNodeDelay) {
        this.slowNodeDelay = slowNodeDelay;
    }

    /**
     * @throws InvalidDemandException si la potencia es nula, negativa o más de lo que maneja un nodo
     * @throws UnknownNodeException si el nodo no existe
     */
    public Feasibility check(String nodeId, BigDecimal requestedPowerKw) {
        if (requestedPowerKw.signum() <= 0 || requestedPowerKw.compareTo(GridNodeCatalog.MAX_SUPPORTED_POWER_KW) > 0) {
            throw new InvalidDemandException(requestedPowerKw);
        }
        GridNode node = GridNodeCatalog.find(nodeId).orElseThrow(() -> new UnknownNodeException(nodeId));

        if (node.behavior() == NodeBehavior.SLOW) {
            Pause.of(slowNodeDelay);
        }

        Feasibility result = evaluate(node, requestedPowerKw);
        log.info(
                "Factibilidad del nodo {} para {} kW: {}",
                nodeId,
                requestedPowerKw,
                result.feasible() ? "autorizada" : "rechazada (" + result.reason() + ")");
        return result;
    }

    private static Feasibility evaluate(GridNode node, BigDecimal requestedPowerKw) {
        if (node.behavior() == NodeBehavior.MAINTENANCE) {
            return new Feasibility(false, BigDecimal.ZERO.setScale(2), Restriction.MAINTENANCE_WINDOW);
        }
        if (requestedPowerKw.compareTo(node.capacityKw()) > 0) {
            return new Feasibility(false, node.capacityKw(), Restriction.DEMAND_CAP_EXCEEDED);
        }
        return new Feasibility(true, node.capacityKw(), null);
    }
}
