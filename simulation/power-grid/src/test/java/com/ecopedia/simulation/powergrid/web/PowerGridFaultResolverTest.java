package com.ecopedia.simulation.powergrid.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.simulation.powergrid.domain.UnknownNodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;

/**
 * Lo que no se puede provocar desde afuera: que un error inesperado salga sin detalle interno.
 * Con el resolver por defecto de Spring-WS, el mensaje de la excepción viajaba al cliente.
 */
class PowerGridFaultResolverTest {

    @Test
    @DisplayName("Un error inesperado sale como Fault Server que solo dice Internal error")
    void hidesUnexpectedErrors() throws Exception {
        SoapFaultDefinition fault = new PowerGridFaultResolver()
                .getFaultDefinition(null, new IllegalStateException("jdbc:h2:file:C:/secreto connection refused"));

        assertThat(fault.getFaultCode()).isEqualTo(SoapFaultDefinition.SERVER);
        assertThat(fault.getFaultStringOrReason()).isEqualTo("Internal error");
    }

    @Test
    @DisplayName("Un Fault del contrato sale como Client, con su mensaje")
    void contractFaultsAreClientFaults() throws Exception {
        SoapFaultDefinition fault =
                new PowerGridFaultResolver().getFaultDefinition(null, new UnknownNodeException("X"));

        assertThat(fault.getFaultCode()).isEqualTo(SoapFaultDefinition.CLIENT);
        assertThat(fault.getFaultStringOrReason()).isEqualTo("Unknown node");
    }
}
