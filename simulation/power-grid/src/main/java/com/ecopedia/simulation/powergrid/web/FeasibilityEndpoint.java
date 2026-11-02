package com.ecopedia.simulation.powergrid.web;

import com.ecopedia.simulation.powergrid.contract.CheckNodeFeasibilityRequest;
import com.ecopedia.simulation.powergrid.contract.CheckNodeFeasibilityResponse;
import com.ecopedia.simulation.powergrid.contract.RestrictionReason;
import com.ecopedia.simulation.powergrid.domain.Feasibility;
import com.ecopedia.simulation.powergrid.service.FeasibilityService;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * La operación {@code CheckNodeFeasibility}: el único servicio que publica la distribuidora.
 *
 * <p>Solo traduce entre las clases que JAXB generó del XSD y el negocio. Cuando el pedido llega
 * acá ya pasó por los dos interceptores de {@code WebServiceConfig}: el UsernameToken se validó y
 * el XML cumple el XSD. Los nodos que no existen y las potencias sin sentido salen como Faults, por
 * {@code PowerGridFaultResolver}.
 */
@Endpoint
public class FeasibilityEndpoint {

    public static final String NAMESPACE = "http://ecopedia.com/simulation/power-grid/v1";

    private final FeasibilityService feasibilityService;

    public FeasibilityEndpoint(FeasibilityService feasibilityService) {
        this.feasibilityService = feasibilityService;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "CheckNodeFeasibilityRequest")
    @ResponsePayload
    public CheckNodeFeasibilityResponse checkNodeFeasibility(@RequestPayload CheckNodeFeasibilityRequest request) {
        Feasibility result = feasibilityService.check(request.getNodeId(), request.getRequestedPowerKw());

        CheckNodeFeasibilityResponse response = new CheckNodeFeasibilityResponse();
        response.setNodeId(request.getNodeId());
        response.setFeasible(result.feasible());
        response.setMaxAllowedPowerKw(result.maxAllowedPowerKw());
        if (result.reason() != null) {
            response.setReason(RestrictionReason.valueOf(result.reason().name()));
        }
        return response;
    }
}
