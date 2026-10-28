package com.ecopedia.simulation.powergrid;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * La distribuidora de punta a punta, por HTTP y con el XML escrito a mano, como lo mandaría
 * cualquier cliente SOAP: así se prueba también lo que pasa antes de Spring-WS —el tamaño y el
 * parseo— y la cabecera WS-Security tal como viaja.
 *
 * <p>El nodo lento no espera acá ({@code slow-node-delay=0s}): lo que se prueba de él es que
 * autoriza; la demora la mide el test del cliente contra el proceso real, en ecopedia-integration.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "simulation.power-grid.slow-node-delay=0s")
@ActiveProfiles("dev")
class PowerGridServiceTest {

    private static final String SECURITY =
            """
            <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">\
            <wsse:UsernameToken><wsse:Username>ecopedia</wsse:Username>\
            <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">\
            %s</wsse:Password></wsse:UsernameToken></wsse:Security>""";

    private static final String DEV_PASSWORD = "ecopedia-dev-only-power-grid-password";

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    private static String envelope(String header, String body) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
                xmlns:pg="http://ecopedia.com/simulation/power-grid/v1">\
                <soapenv:Header>%s</soapenv:Header><soapenv:Body>%s</soapenv:Body></soapenv:Envelope>"""
                .formatted(header, body);
    }

    private static String check(String nodeId, String kw) {
        return "<pg:CheckNodeFeasibilityRequest><pg:nodeId>%s</pg:nodeId><pg:requestedPowerKw>%s</pg:requestedPowerKw>"
                        .formatted(nodeId, kw)
                + "</pg:CheckNodeFeasibilityRequest>";
    }

    private static String secured(String nodeId, String kw) {
        return envelope(SECURITY.formatted(DEV_PASSWORD), check(nodeId, kw));
    }

    private HttpResponse<String> post(BodyPublisher body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ws"))
                .header("Content-Type", "text/xml; charset=utf-8")
                .POST(body)
                .build();
        return http.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return post(BodyPublishers.ofString(body));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .build(),
                BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Publica el WSDL, con los dos Faults en la operación, y el XSD que importa")
    void publishesContract() throws Exception {
        HttpResponse<String> wsdl = get("/ws/powerGrid.wsdl");
        HttpResponse<String> xsd = get("/ws/power-grid.xsd");

        assertThat(wsdl.statusCode()).isEqualTo(200);
        assertThat(wsdl.body())
                .contains("<wsdl:operation name=\"CheckNodeFeasibility\">")
                .contains("<wsdl:fault name=\"UnknownNodeFault\" message=\"tns:UnknownNodeFault\"/>")
                .contains("<wsdl:fault name=\"InvalidDemandFault\" message=\"tns:InvalidDemandFault\"/>")
                .contains("schemaLocation=\"power-grid.xsd\"");
        assertThat(xsd.statusCode()).isEqualTo(200);
        assertThat(xsd.body()).contains("name=\"CheckNodeFeasibilityRequest\"");
    }

    @Test
    @DisplayName("Un nodo normal autoriza hasta su capacidad")
    void normalNode() throws Exception {
        HttpResponse<String> response = post(secured("CABA-CENTRO", "150"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("<ns2:feasible>true</ns2:feasible>")
                .contains("<ns2:maxAllowedPowerKw>600.00</ns2:maxAllowedPowerKw>")
                .doesNotContain("reason");
    }

    /* Un "no" de la red es una respuesta con 200, no un Fault. */
    @Test
    @DisplayName("El nodo con tope de demanda rechaza lo que lo supera, como respuesta y no como Fault")
    void demandCap() throws Exception {
        HttpResponse<String> over = post(secured("CABA-OESTE", "150"));
        HttpResponse<String> within = post(secured("CABA-OESTE", "50"));

        assertThat(over.statusCode()).isEqualTo(200);
        assertThat(over.body())
                .contains("<ns2:feasible>false</ns2:feasible>")
                .contains("<ns2:maxAllowedPowerKw>50.00</ns2:maxAllowedPowerKw>")
                .contains("<ns2:reason>DEMAND_CAP_EXCEEDED</ns2:reason>");
        assertThat(within.body()).contains("<ns2:feasible>true</ns2:feasible>");
    }

    @Test
    @DisplayName("El nodo en mantenimiento no autoriza nada")
    void maintenance() throws Exception {
        HttpResponse<String> response = post(secured("CABA-SUR", "7"));

        assertThat(response.body())
                .contains("<ns2:feasible>false</ns2:feasible>")
                .contains("<ns2:reason>MAINTENANCE_WINDOW</ns2:reason>");
    }

    @Test
    @DisplayName("El nodo lento autoriza")
    void slowNode() throws Exception {
        assertThat(post(secured("LA-PLATA", "150")).body()).contains("<ns2:feasible>true</ns2:feasible>");
    }

    @Test
    @DisplayName("Un nodo que no existe es un UnknownNodeFault de tipo Client, con el elemento en el detalle")
    void unknownNode() throws Exception {
        HttpResponse<String> response = post(secured("NODO-X", "50"));

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .contains("<faultcode>SOAP-ENV:Client</faultcode>")
                .contains("<ns2:UnknownNodeFault")
                .contains("<ns2:nodeId>NODO-X</ns2:nodeId>");
    }

    @ParameterizedTest(name = "{0} kW")
    @ValueSource(strings = {"0", "-5", "1000.01"})
    @DisplayName("Una potencia nula, negativa o fuera de rango es un InvalidDemandFault")
    void invalidDemand(String kw) throws Exception {
        HttpResponse<String> response = post(secured("CABA-CENTRO", kw));

        assertThat(response.body())
                .contains("<faultcode>SOAP-ENV:Client</faultcode>")
                .contains("<ns2:InvalidDemandFault")
                .contains("<ns2:maxSupportedPowerKw>1000.00</ns2:maxSupportedPowerKw>");
    }

    @Test
    @DisplayName("Sin la cabecera WS-Security, el pedido no llega al servicio")
    void requiresSecurityHeader() throws Exception {
        HttpResponse<String> response = post(envelope("", check("CABA-CENTRO", "150")));

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("<SOAP-ENV:Fault>").doesNotContain("feasible");
    }

    @Test
    @DisplayName("Con la contraseña equivocada, el pedido no llega al servicio")
    void rejectsWrongPassword() throws Exception {
        HttpResponse<String> response = post(envelope(SECURITY.formatted("otra"), check("CABA-CENTRO", "150")));

        assertThat(response.body())
                .contains("The security token could not be authenticated or authorized")
                .doesNotContain("feasible");
    }

    @Test
    @DisplayName("Un pedido que no cumple el XSD se rechaza con un Fault de validación")
    void validatesAgainstSchema() throws Exception {
        HttpResponse<String> response = post(secured("caba-centro", "150"));

        assertThat(response.body()).contains("<faultstring xml:lang=\"en\">Validation error</faultstring>");
    }

    /*
     * XXE: la entidad apunta a un archivo del disco. Si el parser la resolviera, el contenido
     * aparecería en la respuesta (en el Fault de nodo desconocido, que repite el nodeId).
     */
    @Test
    @DisplayName("Un pedido con DOCTYPE y una entidad externa se rechaza sin leer el archivo")
    void rejectsExternalEntities(@TempDir Path dir) throws Exception {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "SECRETO-DEL-DISCO");
        String body = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
                + secured("&xxe;", "50");

        HttpResponse<String> response = post(body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Malformed SOAP message").doesNotContain("SECRETO-DEL-DISCO");
    }

    @Test
    @DisplayName("Un pedido de más del tope se corta con 413, con Content-Length")
    void rejectsOversizedRequest() throws Exception {
        String padding = "<!--" + "x".repeat(20_000) + "-->";
        HttpResponse<String> response =
                post(envelope(SECURITY.formatted(DEV_PASSWORD) + padding, check("CABA-CENTRO", "150")));

        assertThat(response.statusCode()).isEqualTo(413);
    }

    /* Sin largo conocido, el cliente HTTP lo manda por partes: el tope tiene que cortarlo igual. */
    @Test
    @DisplayName("Un pedido de más del tope se corta con 413, también enviado por partes")
    void rejectsOversizedChunkedRequest() throws Exception {
        byte[] body = envelope(
                        SECURITY.formatted(DEV_PASSWORD) + "<!--" + "x".repeat(20_000) + "-->",
                        check("CABA-CENTRO", "150"))
                .getBytes();

        HttpResponse<String> response = post(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));

        assertThat(response.statusCode()).isEqualTo(413);
    }
}
