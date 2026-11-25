package com.ecopedia.integration.powergrid.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.RequestMatchers.soapHeader;
import static org.springframework.ws.test.client.ResponseCreators.withClientOrSenderFault;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withSoapEnvelope;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException.Reason;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

/**
 * El adaptador SOAP sin la distribuidora: {@link MockWebServiceServer} contesta lo que contestaría
 * ella. Se prueba la traducción en los dos sentidos y que cada forma de fallar termine donde tiene
 * que terminar. Que la distribuidora de verdad conteste así lo prueban sus propios tests, y el
 * adaptador contra ella, {@code PowerGridLiveTest}.
 */
@ExtendWith(OutputCaptureExtension.class)
class PowerGridWsClientTest {

    private static final String NS = "http://ecopedia.com/simulation/power-grid/v1";
    private static final QName WS_SECURITY =
            new QName("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd", "Security");

    private MockWebServiceServer powerGrid;
    private PowerGridWsClient client;

    @BeforeEach
    void setUp() throws Exception {
        WebServiceTemplate template = PowerGridClientConfig.buildTemplate(
                "http://power-grid.test/ws",
                "ecopedia",
                "clave-de-prueba",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        powerGrid = MockWebServiceServer.createServer(template);
        client = new PowerGridWsClient(template);
    }

    private static Source xml(String body) {
        return new StringSource(body);
    }

    private static Source response(boolean feasible, String max, String reason) {
        return xml(
                """
                <CheckNodeFeasibilityResponse xmlns="%s"><nodeId>CABA-CENTRO</nodeId><feasible>%s</feasible>\
                <maxAllowedPowerKw>%s</maxAllowedPowerKw>%s</CheckNodeFeasibilityResponse>"""
                        .formatted(NS, feasible, max, reason == null ? "" : "<reason>" + reason + "</reason>"));
    }

    private static Source faultWithDetail(String detail) {
        return xml(
                """
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"><SOAP-ENV:Body>\
                <SOAP-ENV:Fault><faultcode>SOAP-ENV:Client</faultcode><faultstring>x</faultstring>\
                <detail>%s</detail></SOAP-ENV:Fault></SOAP-ENV:Body></SOAP-ENV:Envelope>"""
                        .formatted(detail));
    }

    @Test
    @DisplayName("Pide la factibilidad con el UsernameToken y traduce el sí")
    void authorizes() {
        powerGrid
                .expect(payload(xml(
                        """
                        <CheckNodeFeasibilityRequest xmlns="%s"><nodeId>CABA-CENTRO</nodeId>\
                        <requestedPowerKw>150</requestedPowerKw></CheckNodeFeasibilityRequest>"""
                                .formatted(NS))))
                .andExpect(soapHeader(WS_SECURITY))
                .andRespond(withPayload(response(true, "600.00", null)));

        Feasibility result = client.checkFeasibility("CABA-CENTRO", new BigDecimal("150"));

        assertThat(result.feasible()).isTrue();
        assertThat(result.maxAllowedPowerKw()).isEqualByComparingTo("600");
        assertThat(result.reason()).isNull();
        powerGrid.verify();
    }

    /* Un "no" de la red es una respuesta, no una excepción. */
    @Test
    @DisplayName("Un no de la red vuelve como respuesta, con el motivo y el tope")
    void rejectionIsAnAnswer() {
        powerGrid
                .expect(soapHeader(WS_SECURITY))
                .andRespond(withPayload(response(false, "50.00", "DEMAND_CAP_EXCEEDED")));

        Feasibility result = client.checkFeasibility("CABA-OESTE", new BigDecimal("150"));

        assertThat(result.feasible()).isFalse();
        assertThat(result.maxAllowedPowerKw()).isEqualByComparingTo("50");
        assertThat(result.reason()).isEqualTo(Feasibility.Restriction.DEMAND_CAP_EXCEEDED);
    }

    @Test
    @DisplayName("UnknownNodeFault se reconoce por el detalle y es un pedido rechazado")
    void unknownNodeFault() {
        powerGrid
                .expect(soapHeader(WS_SECURITY))
                .andRespond(withSoapEnvelope(faultWithDetail(
                        "<UnknownNodeFault xmlns=\"%s\"><nodeId>NODO-X</nodeId></UnknownNodeFault>".formatted(NS))));

        assertThatThrownBy(() -> client.checkFeasibility("NODO-X", new BigDecimal("50")))
                .isInstanceOf(PowerGridRequestRejectedException.class)
                .extracting(e -> ((PowerGridRequestRejectedException) e).getReason())
                .isEqualTo(Reason.UNKNOWN_NODE);
    }

    @Test
    @DisplayName("InvalidDemandFault se reconoce por el detalle y es un pedido rechazado")
    void invalidDemandFault() {
        powerGrid
                .expect(soapHeader(WS_SECURITY))
                .andRespond(withSoapEnvelope(faultWithDetail(
                        """
                        <InvalidDemandFault xmlns="%s"><requestedPowerKw>0</requestedPowerKw>\
                        <maxSupportedPowerKw>1000.00</maxSupportedPowerKw></InvalidDemandFault>"""
                                .formatted(NS))));

        assertThatThrownBy(() -> client.checkFeasibility("CABA-CENTRO", BigDecimal.ZERO))
                .isInstanceOf(PowerGridRequestRejectedException.class)
                .extracting(e -> ((PowerGridRequestRejectedException) e).getReason())
                .isEqualTo(Reason.INVALID_DEMAND);
    }

    @Test
    @DisplayName("Un pedido que no cumple el XSD es un pedido rechazado, no una caída")
    void validationFault() {
        powerGrid
                .expect(soapHeader(WS_SECURITY))
                .andRespond(withSoapEnvelope(faultWithDetail(
                        "<spring-ws:ValidationError xmlns:spring-ws=\"http://springframework.org/spring-ws\">"
                                + "cvc-pattern-valid</spring-ws:ValidationError>")));

        assertThatThrownBy(() -> client.checkFeasibility("CABA-CENTRO", new BigDecimal("50")))
                .isInstanceOf(PowerGridRequestRejectedException.class)
                .extracting(e -> ((PowerGridRequestRejectedException) e).getReason())
                .isEqualTo(Reason.INVALID_REQUEST);
    }

    /* Es el Fault con que la distribuidora rechaza unas credenciales equivocadas. */
    @Test
    @DisplayName("Un Fault sin detalle, como el de credenciales, es una distribuidora no disponible")
    void faultWithoutDetail() {
        powerGrid
                .expect(soapHeader(WS_SECURITY))
                .andRespond(withClientOrSenderFault(
                        "The security token could not be authenticated or authorized", Locale.ENGLISH));

        assertThatThrownBy(() -> client.checkFeasibility("CABA-CENTRO", new BigDecimal("50")))
                .isInstanceOf(PowerGridUnavailableException.class);
    }

    /*
     * QA-25: la respuesta se valida contra el contrato antes de leerla. Sin esa validación, JAXB lee
     * estas respuestas a medias (un tope vacío, un motivo que no existe) y el cliente las da por
     * buenas.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            sin el tope de potencia | <nodeId>CABA-CENTRO</nodeId><feasible>true</feasible>
            un motivo que el contrato no tiene | <nodeId>CABA-CENTRO</nodeId><feasible>false</feasible><maxAllowedPowerKw>50.00</maxAllowedPowerKw><reason>SIN_GANAS</reason>
            una potencia con tres decimales | <nodeId>CABA-CENTRO</nodeId><feasible>true</feasible><maxAllowedPowerKw>50.125</maxAllowedPowerKw>
            un nodo en minúsculas | <nodeId>caba-centro</nodeId><feasible>true</feasible><maxAllowedPowerKw>50.00</maxAllowedPowerKw>
            """)
    @DisplayName("Una respuesta que no cumple el contrato es una distribuidora no disponible")
    void offContractResponse(String caseName, String fields, CapturedOutput output) {
        powerGrid
                .expect(soapHeader(WS_SECURITY))
                .andRespond(
                        withPayload(xml("<CheckNodeFeasibilityResponse xmlns=\"%s\">%s</CheckNodeFeasibilityResponse>"
                                .formatted(NS, fields))));

        assertThatThrownBy(() -> client.checkFeasibility("CABA-CENTRO", new BigDecimal("50")))
                .isInstanceOf(PowerGridUnavailableException.class)
                .hasMessageContaining("no cumple el contrato");
        assertThat(output).contains("ERROR").contains("no cumple el contrato");
    }

    @Test
    @DisplayName("Si la distribuidora no contesta a tiempo, no está disponible")
    void timeout() {
        powerGrid
                .expect(soapHeader(WS_SECURITY))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.checkFeasibility("LA-PLATA", new BigDecimal("50")))
                .isInstanceOf(PowerGridUnavailableException.class);
    }
}
