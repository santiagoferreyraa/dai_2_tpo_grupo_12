package com.ecopedia.simulation.powergrid.web;

import com.ecopedia.simulation.powergrid.contract.InvalidDemandFault;
import com.ecopedia.simulation.powergrid.contract.ObjectFactory;
import com.ecopedia.simulation.powergrid.contract.UnknownNodeFault;
import com.ecopedia.simulation.powergrid.domain.GridNodeCatalog;
import com.ecopedia.simulation.powergrid.domain.InvalidDemandException;
import com.ecopedia.simulation.powergrid.domain.UnknownNodeException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.server.endpoint.AbstractSoapFaultDefinitionExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;

/**
 * Convierte las excepciones del servicio en SOAP Faults, con el elemento del XSD en el detalle.
 *
 * <p><b>Dos Faults del contrato y nada más.</b> {@code UnknownNodeFault} e
 * {@code InvalidDemandFault} son errores del que pide (código {@code Client}) y llevan en el
 * detalle el elemento que declara el WSDL, para que el cliente sepa cuál es sin leer el texto.
 *
 * <p><b>Sin detalle interno.</b> Cualquier otra excepción sale como un Fault {@code Server} que dice
 * "Internal error" y nada más: la pila, el nombre de la clase y el mensaje quedan en el log. Sin
 * este resolver, el que trae Spring-WS por defecto mandaría el mensaje de la excepción al cliente.
 */
@Component
public class PowerGridFaultResolver extends AbstractSoapFaultDefinitionExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(PowerGridFaultResolver.class);

    private final JAXBContext contract;

    public PowerGridFaultResolver() throws JAXBException {
        this.contract = JAXBContext.newInstance(ObjectFactory.class);
    }

    @Override
    protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception exception) {
        SoapFaultDefinition definition = new SoapFaultDefinition();
        if (exception instanceof UnknownNodeException || exception instanceof InvalidDemandException) {
            definition.setFaultCode(SoapFaultDefinition.CLIENT);
            definition.setFaultStringOrReason(exception.getMessage());
        } else {
            log.error("Error inesperado al atender un pedido", exception);
            definition.setFaultCode(SoapFaultDefinition.SERVER);
            definition.setFaultStringOrReason("Internal error");
        }
        return definition;
    }

    @Override
    protected void customizeFault(Object endpoint, Exception exception, SoapFault fault) {
        Object detail;
        if (exception instanceof UnknownNodeException unknown) {
            UnknownNodeFault element = new UnknownNodeFault();
            element.setNodeId(unknown.getNodeId());
            detail = element;
        } else if (exception instanceof InvalidDemandException invalid) {
            InvalidDemandFault element = new InvalidDemandFault();
            element.setRequestedPowerKw(invalid.getRequestedPowerKw());
            element.setMaxSupportedPowerKw(GridNodeCatalog.MAX_SUPPORTED_POWER_KW);
            detail = element;
        } else {
            return;
        }
        try {
            contract.createMarshaller().marshal(detail, fault.addFaultDetail().getResult());
        } catch (JAXBException unwritable) {
            log.error("No se pudo escribir el detalle del Fault", unwritable);
        }
    }
}
