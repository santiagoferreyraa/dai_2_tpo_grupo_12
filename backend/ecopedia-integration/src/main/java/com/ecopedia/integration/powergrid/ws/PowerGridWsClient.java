package com.ecopedia.integration.powergrid.ws;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import com.ecopedia.integration.powergrid.domain.PowerGridPort;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException.Reason;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import com.ecopedia.integration.powergrid.ws.contract.CheckNodeFeasibilityRequest;
import com.ecopedia.integration.powergrid.ws.contract.CheckNodeFeasibilityResponse;
import java.math.BigDecimal;
import java.util.Iterator;
import javax.xml.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.client.SoapFaultClientException;

/**
 * El adaptador SOAP hacia la distribuidora: implementa {@link PowerGridPort} con Spring-WS.
 *
 * <p><b>Separa los tres resultados.</b> Una respuesta —que puede ser "no"— vuelve como
 * {@link Feasibility}. Un Fault del contrato ({@code UnknownNodeFault}, {@code InvalidDemandFault})
 * o uno de validación del XSD es un {@link PowerGridRequestRejectedException}, y se lo reconoce por
 * el elemento del detalle, no por el texto. Todo lo demás —la distribuidora caída, lenta, o un Fault
 * sin detalle como el de credenciales rechazadas— es {@link PowerGridUnavailableException}.
 *
 * <p>El Fault de credenciales deja un ERROR y no un WARN, como con la pasarela: no se arregla solo,
 * es la contraseña del .env mal copiada.
 */
@Component
public class PowerGridWsClient implements PowerGridPort {

    private static final Logger log = LoggerFactory.getLogger(PowerGridWsClient.class);

    private static final String CONTRACT_NAMESPACE = "http://ecopedia.com/simulation/power-grid/v1";
    private static final QName UNKNOWN_NODE_FAULT = new QName(CONTRACT_NAMESPACE, "UnknownNodeFault");
    private static final QName INVALID_DEMAND_FAULT = new QName(CONTRACT_NAMESPACE, "InvalidDemandFault");
    /** El detalle con que Spring-WS, del otro lado, devuelve un pedido que no cumple el XSD. */
    private static final QName VALIDATION_ERROR = new QName("http://springframework.org/spring-ws", "ValidationError");

    private final WebServiceTemplate webServiceTemplate;

    public PowerGridWsClient(@Qualifier("powerGridWebServiceTemplate") WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    @Override
    public Feasibility checkFeasibility(String nodeId, BigDecimal requestedPowerKw) {
        CheckNodeFeasibilityRequest request = new CheckNodeFeasibilityRequest();
        request.setNodeId(nodeId);
        request.setRequestedPowerKw(requestedPowerKw);

        try {
            CheckNodeFeasibilityResponse response =
                    (CheckNodeFeasibilityResponse) webServiceTemplate.marshalSendAndReceive(request);
            return new Feasibility(
                    response.isFeasible(),
                    response.getMaxAllowedPowerKw(),
                    response.getReason() == null
                            ? null
                            : Feasibility.Restriction.valueOf(
                                    response.getReason().name()));
        } catch (SoapFaultClientException fault) {
            throw translate(fault, nodeId, requestedPowerKw);
        } catch (WebServiceClientException failure) {
            log.warn("La distribuidora no contestó por el nodo {}: {}", nodeId, failure.getMessage());
            throw new PowerGridUnavailableException("La distribuidora no contestó", failure);
        }
    }

    private static RuntimeException translate(
            SoapFaultClientException fault, String nodeId, BigDecimal requestedPowerKw) {
        QName detail = firstDetailElement(fault);
        if (UNKNOWN_NODE_FAULT.equals(detail)) {
            return new PowerGridRequestRejectedException(
                    Reason.UNKNOWN_NODE, "La distribuidora no conoce el nodo " + nodeId, fault);
        }
        if (INVALID_DEMAND_FAULT.equals(detail)) {
            return new PowerGridRequestRejectedException(
                    Reason.INVALID_DEMAND, "La distribuidora rechazó la potencia pedida: " + requestedPowerKw, fault);
        }
        if (VALIDATION_ERROR.equals(detail)) {
            log.error("La distribuidora rechazó el pedido por el nodo {} por no cumplir el contrato", nodeId);
            return new PowerGridRequestRejectedException(
                    Reason.INVALID_REQUEST, "El pedido no cumple el contrato de la distribuidora", fault);
        }
        log.error(
                "La distribuidora devolvió un Fault sin detalle por el nodo {} ({}): revisar ECOPEDIA_POWER_GRID_PASSWORD",
                nodeId,
                fault.getFaultStringOrReason());
        return new PowerGridUnavailableException("La distribuidora rechazó el pedido sin detalle", fault);
    }

    private static QName firstDetailElement(SoapFaultClientException fault) {
        SoapFaultDetail detail = fault.getSoapFault().getFaultDetail();
        if (detail == null) return null;
        Iterator<SoapFaultDetailElement> entries = detail.getDetailEntries();
        return entries.hasNext() ? entries.next().getName() : null;
    }
}
